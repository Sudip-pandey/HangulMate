#!/usr/bin/env python3
"""HangulMate Phase 2 — NIKL fetch for seed v3 relations.

Per seed headword: 1 search (exact + English translation) to resolve the
NIKL target_code (exact word + POS + English-gloss similarity), then 1 view
to read per-sense rel_info (type strings: 반대말 confirmed by probe;
유의어 handled if it ever appears).

Stdlib only. Resume-safe: completed ids are skipped (index + raw XML on
disk). Polite pacing: 2 worker threads, 0.5 s delay per thread between
calls. Daily limit 50,000; this run needs ~20,000.

Usage:
    python3 fetch_nikl.py [--limit N] [--start N] [--workers N] [--delay S]
State:
    fetch_raw/search/<id>.xml  search response
    fetch_raw/view/<id>.xml    view response
    fetch_raw/fetch_index.json per-id resolution + rel_info summary
    fetch_raw/fetch_log.txt    append-only run log
"""
import argparse
import json
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SEED = ROOT / "app" / "src" / "main" / "assets" / "seed" / "vocabulary_seed.json"
RAW = Path(__file__).resolve().parent / "fetch_raw"
SEARCH_DIR = RAW / "search"
VIEW_DIR = RAW / "view"
INDEX_FILE = RAW / "fetch_index.json"
LOG_FILE = RAW / "fetch_log.txt"
HELPER = Path.home() / "workspace" / "custom-connectors" / "custom.krdict" / "bin" / "krdict.py"

POS_MAP = {
    "noun": "명사",
    "verb": "동사",
    "adjective": "형용사",
    "adverb": "부사",
    "pronoun": "대명사",
    "number": "수사",
    "interjection": "감탄사",
    # "expression": no NIKL POS — word-only match (usually unresolved).
}
STOPWORDS = set(
    "a an the to of for in on at by with and or is are be been being was were "
    "it its this that these those as from into than then so such one ones someone "
    "something somebody an".split()
)


def log(msg: str) -> None:
    line = f"{time.strftime('%Y-%m-%d %H:%M:%S')} {msg}"
    print(line, flush=True)
    with open(LOG_FILE, "a", encoding="utf-8") as f:
        f.write(line + "\n")


def tokens(text: str) -> set:
    words = re.findall(r"[a-z0-9]+", (text or "").lower())
    return {w for w in words if w not in STOPWORDS and len(w) > 1}


def jaccard(a: set, b: set) -> float:
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def run_helper(args: list, retries: int = 3) -> str:
    """Run krdict.py; return stdout XML. Retries transient failures.
    Raises RuntimeError('DAILY_LIMIT') on NIKL error 010,
    RuntimeError('KEY') on 020, RuntimeError('PARAM') on 100."""
    for attempt in range(1, retries + 1):
        try:
            p = subprocess.run(
                [sys.executable, str(HELPER)] + args,
                capture_output=True, text=True, timeout=90,
            )
        except subprocess.TimeoutExpired:
            if attempt == retries:
                raise RuntimeError("TIMEOUT")
            time.sleep(2 * attempt)
            continue
        out = p.stdout or ""
        err = p.stderr or ""
        if "<error_code>010</error_code>" in out or "010" in err and "Daily" in err:
            raise RuntimeError("DAILY_LIMIT")
        if "<error_code>020</error_code>" in out:
            raise RuntimeError("KEY")
        if "<error_code>100</error_code>" in out:
            raise RuntimeError("PARAM")
        if p.returncode == 0 and ("<channel>" in out or "<error>" in out):
            return out
        if attempt == retries:
            raise RuntimeError(f"HELPER_FAIL rc={p.returncode} err={err[:200]!r}")
        time.sleep(2 * attempt)
    raise RuntimeError("HELPER_FAIL")


def resolve(entry: dict, search_xml: str):
    """Pick the best search item for a seed entry.
    Returns (target_code, sup_no, pos, method, score) or None."""
    try:
        root = ET.fromstring(search_xml)
    except ET.ParseError:
        return None
    if root.findtext("total") == "0":
        return None
    word = entry["korean"]
    want_pos = POS_MAP.get(entry.get("partOfSpeech") or "")
    seed_en = tokens(entry.get("english") or "")
    defs = entry.get("senses") or []
    if defs:
        seed_en |= tokens(defs[0].get("definitions", [""])[0]
                          if defs[0].get("definitions") else "")
    candidates = []
    for it in root.iter("item"):
        if it.findtext("word") != word:
            continue
        pos = it.findtext("pos") or ""
        if want_pos and pos != want_pos:
            continue
        best = 0.0
        best_sense = None
        for s in it.iter("sense"):
            tr = s.find("translation")
            dfn = tr.findtext("trans_dfn") if tr is not None else None
            score = jaccard(seed_en, tokens(dfn or ""))
            if score > best:
                best, best_sense = score, s.findtext("sense_order")
        candidates.append((best, it, best_sense))
    if not candidates:
        return None
    if len(candidates) == 1:
        best, it, sense = candidates[0]
        method = "gloss_match" if best >= 0.2 else "single_candidate"
        return (it.findtext("target_code"), it.findtext("sup_no"),
                it.findtext("pos"), method, round(best, 3))
    candidates.sort(key=lambda c: -c[0])
    best, it, sense = candidates[0]
    method = "gloss_match" if best >= 0.2 else "ambiguous_low_score"
    return (it.findtext("target_code"), it.findtext("sup_no"),
            it.findtext("pos"), method, round(best, 3))


def parse_rel_info(view_xml: str):
    """Return (counts_by_type, rows) from a view response."""
    root = ET.fromstring(view_xml)
    counts = {}
    rows = []
    for i, s in enumerate(root.iter("sense_info"), 1):
        for r in s.findall("rel_info"):
            typ = (r.findtext("type") or "").strip()
            w = (r.findtext("word") or "").strip()
            tc = (r.findtext("link_target_code") or "").strip()
            counts[typ] = counts.get(typ, 0) + 1
            rows.append({"sense": i, "type": typ, "word": w,
                         "link_target_code": tc})
    return counts, rows


class Fetcher:
    def __init__(self, delay: float):
        self.delay = delay
        self.lock = threading.Lock()
        self.index = self._load_index()
        self.done = 0
        self.calls = 0
        self.started = time.time()

    def _load_index(self):
        if INDEX_FILE.exists():
            return json.loads(INDEX_FILE.read_text(encoding="utf-8"))
        return {"entries": {}, "calls_made": 0}

    def _save_index(self):
        tmp = INDEX_FILE.with_suffix(".json.tmp")
        tmp.write_text(json.dumps(self.index, ensure_ascii=False, indent=1),
                       encoding="utf-8")
        tmp.replace(INDEX_FILE)

    def fetch_one(self, entry: dict):
        eid = entry["id"]
        with self.lock:
            if eid in self.index["entries"]:
                st = self.index["entries"][eid].get("status")
                if st in ("view_done", "unresolved"):
                    return
        status = {"id": eid, "korean": entry["korean"]}
        try:
            search_path = SEARCH_DIR / f"{eid}.xml"
            if search_path.exists():
                search_xml = search_path.read_text(encoding="utf-8")
            else:
                search_xml = run_helper(["search", "--q", entry["korean"],
                                         "--method", "exact", "--translated", "y",
                                         "--trans_lang", "1"])
                search_path.write_text(search_xml, encoding="utf-8")
                with self.lock:
                    self.calls += 1
                time.sleep(self.delay)
            res = resolve(entry, search_xml)
            if res is None:
                status.update(status="unresolved", reason="no_matching_entry")
            else:
                target_code, sup_no, pos, method, score = res
                view_path = VIEW_DIR / f"{eid}.xml"
                if view_path.exists():
                    view_xml = view_path.read_text(encoding="utf-8")
                else:
                    view_xml = run_helper(["view", "--target-code", target_code])
                    view_path.write_text(view_xml, encoding="utf-8")
                    with self.lock:
                        self.calls += 1
                    time.sleep(self.delay)
                counts, rows = parse_rel_info(view_xml)
                status.update(status="view_done", target_code=target_code,
                              sup_no=sup_no, pos=pos, match_method=method,
                              match_score=score, rel_info_counts=counts,
                              rel_info_rows=rows)
        except RuntimeError as e:
            if str(e) in ("DAILY_LIMIT", "KEY", "PARAM"):
                raise
            status.update(status="error", reason=str(e))
        except Exception as e:  # noqa: BLE001 — recorded, not fatal
            status.update(status="error", reason=f"{type(e).__name__}: {e}")
        with self.lock:
            self.index["entries"][eid] = status
            self.done += 1
            if self.done % 25 == 0:
                self.index["calls_made"] = self.index.get("calls_made", 0) + self.calls
                self.calls = 0
                self._save_index()
                elapsed = time.time() - self.started
                log(f"progress {self.done} done, elapsed {elapsed/60:.1f}m")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--start", type=int, default=0)
    ap.add_argument("--workers", type=int, default=2)
    ap.add_argument("--delay", type=float, default=0.5)
    args = ap.parse_args()

    SEARCH_DIR.mkdir(parents=True, exist_ok=True)
    VIEW_DIR.mkdir(parents=True, exist_ok=True)
    seed = json.loads(SEED.read_text(encoding="utf-8"))
    todo = seed[args.start:]
    if args.limit:
        todo = todo[: args.limit]
    # Skip already-finished ids up front.
    fetcher = Fetcher(args.delay)
    pending = [e for e in todo
               if fetcher.index["entries"].get(e["id"], {}).get("status")
               not in ("view_done", "unresolved")]
    log(f"seed={len(seed)} todo={len(todo)} pending={len(pending)} "
        f"workers={args.workers} delay={args.delay}s")
    try:
        with ThreadPoolExecutor(max_workers=args.workers) as ex:
            list(ex.map(fetcher.fetch_one, pending))
    except RuntimeError as e:
        log(f"ABORT: {e}")
        fetcher._save_index()
        return 10 if str(e) == "DAILY_LIMIT" else 20
    with fetcher.lock:
        fetcher.index["calls_made"] = fetcher.index.get("calls_made", 0) + fetcher.calls
        fetcher._save_index()
    n_done = sum(1 for v in fetcher.index["entries"].values()
                 if v.get("status") == "view_done")
    n_unres = sum(1 for v in fetcher.index["entries"].values()
                  if v.get("status") == "unresolved")
    n_err = sum(1 for v in fetcher.index["entries"].values()
                if v.get("status") == "error")
    log(f"FINISHED done={fetcher.done} view_done_total={n_done} "
        f"unresolved_total={n_unres} errors_total={n_err} "
        f"calls_total={fetcher.index.get('calls_made')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
