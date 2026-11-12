#!/usr/bin/env python3
"""Measure candidate merge gates on the full NIKL harvest + the 100-row audit sample.

Gate A: keep only code-resolved NIKL rows (drop all string-resolved).
Gate B: Gate A + require the source sense's English trans_dfn (from the
  source's stored SEARCH xml, matched by target_code + sense_order) to share
  >=1 content token with the target entry's displayed primary english.

Outputs counts for the full harvest and a cross-tab of the audit sample
against the manual verdicts in audit_verdicts_v3.json.
"""
import json
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SEED_V2 = ROOT / "app/src/main/assets/seed/vocabulary_seed.json"
INDEX_FILE = HERE / "fetch_raw" / "fetch_index.json"
SEARCH_DIR = HERE / "fetch_raw" / "search"
SAMPLE = HERE / "audit_sample_v3.json"
VERDICTS = HERE / "audit_verdicts_v3.json"

STOPWORDS = set(
    "a an the to of for in on at by with and or is are be been being was were "
    "it its this that these those as from into than then so such one ones someone "
    "something somebody an".split()
)


def tokens(text):
    words = re.findall(r"[a-z0-9]+", (text or "").lower())
    return {w for w in words if w not in STOPWORDS and len(w) > 1}


def load_seed(path=SEED_V2):
    entries = json.loads(path.read_text(encoding="utf-8"))
    return entries, {e["id"]: e for e in entries}, {e["korean"]: e for e in entries}


def load_search_translations(entry_id, target_code):
    """Map sense_order -> trans_dfn for the resolved entry in search/<id>.xml."""
    f = SEARCH_DIR / f"{entry_id}.xml"
    if not f.exists():
        return {}
    try:
        root = ET.parse(f).getroot()
    except ET.ParseError:
        return {}
    for it in root.iter("item"):
        if it.findtext("target_code") != target_code:
            continue
        out = {}
        for s in it.iter("sense"):
            tr = s.find("translation")
            dfn = tr.findtext("trans_dfn") if tr is not None else None
            if dfn:
                out[int(s.findtext("sense_order") or 0)] = dfn
        return out
    return {}


def main():
    entries, by_id, by_korean = load_seed()
    idx = json.loads(INDEX_FILE.read_text(encoding="utf-8"))
    code_to_entry = {}
    for eid, v in idx["entries"].items():
        tc = v.get("target_code")
        if tc and v.get("status") == "view_done":
            code_to_entry.setdefault(tc, by_id[eid])

    # Rebuild the NIKL row pool exactly as merge_v3 does (dedup per entry+kind+target).
    rows = []
    seen = set()
    for e in entries:
        v = idx["entries"].get(e["id"])
        if not v or v.get("status") != "view_done":
            continue
        trans = load_search_translations(e["id"], v.get("target_code"))
        for r in v.get("rel_info_rows", []):
            if r["type"] not in ("유의어", "반대말"):
                continue
            kind = "similar" if r["type"] == "유의어" else "opposite"
            tw = r["word"]
            tc = r.get("link_target_code") or ""
            tentry = None
            method = None
            if tc:
                hit = code_to_entry.get(tc)
                if hit is not None and hit["korean"] == tw:
                    tentry, method = hit, "code"
            if tentry is None:
                tentry = by_korean.get(tw)
                method = "string"
            if tentry is None or tentry["korean"] == e["korean"]:
                continue
            key = (e["id"], kind, tentry["id"])
            if key in seen:
                continue
            seen.add(key)
            src_en = trans.get(r["sense"], "")
            rows.append({
                "source_id": e["id"], "source": e["korean"], "kind": kind,
                "target": tw, "target_id": tentry["id"], "method": method,
                "src_trans": src_en, "tgt_primary": tentry["english"],
                "overlap": bool(tokens(src_en) & tokens(tentry["english"])),
            })
    print(f"NIKL rows (deduped): {len(rows)}")
    from collections import Counter
    c = Counter((r["method"], r["overlap"]) for r in rows)
    print("method x overlap:", dict(c))
    c2 = Counter((r["method"], r["kind"], r["overlap"]) for r in rows)
    for k in sorted(c2):
        print("  ", k, c2[k])
    gate_a_kept = [r for r in rows if r["method"] == "code"]
    gate_b_kept = [r for r in gate_a_kept if r["overlap"]]
    print(f"Gate A (code only) keeps: {len(gate_a_kept)} / {len(rows)} "
          f"({100*len(gate_a_kept)/len(rows):.1f}%)")
    print(f"Gate B (code + primary overlap) keeps: {len(gate_b_kept)} / {len(rows)} "
          f"({100*len(gate_b_kept)/len(rows):.1f}%)")
    no_trans = sum(1 for r in rows if not r["src_trans"])
    print(f"rows with NO source trans_dfn: {no_trans}")

    # Cross-tab the audit sample.
    sample = json.loads(SAMPLE.read_text(encoding="utf-8"))
    verdicts = json.loads(VERDICTS.read_text(encoding="utf-8"))
    vmap = {(v["source_word"], v["kind"], v["target"]): v["verdict"]
            for v in verdicts}
    rowmap = {(r["source"], r["kind"], r["target"]): r for r in rows}
    print("\nAudit sample cross-tab (method / gateB / manual verdict):")
    ct = Counter()
    for s in sample:
        key = (s["source_word"], s["kind"], s["target"])
        r = rowmap.get(key)
        if r is None:
            print("  NOT IN ROW POOL:", key)
            continue
        ct[(r["method"], "keepB" if r["overlap"] else "dropB", vmap[key])] += 1
    for k in sorted(ct):
        print("  ", k, ct[k])
    # What Gate B drops among sample rows judged correct:
    print("\nSample rows judged CORRECT that Gate B would drop:")
    for s in sample:
        key = (s["source_word"], s["kind"], s["target"])
        r = rowmap.get(key)
        if r and vmap[key] == "correct" and not r["overlap"]:
            print(f"  {s['source_word']} {s['kind']} -> {s['target']} [{r['method']}] "
                  f"src='{r['src_trans'][:60]}' tgt='{r['tgt_primary'][:60]}'")


if __name__ == "__main__":
    main()
