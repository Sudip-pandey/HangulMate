#!/usr/bin/env python3
"""HangulMate Phase 2 — merge NIKL + Kaikki relations into seed v3.

Inputs:
  - v2 seed: app/src/main/assets/seed/vocabulary_seed.json (never edited)
  - Kaikki verified rows: tools/dataset_v3_phase0/kaikki_relations.json
    (per-headword 'similar'/'opposite' arrays, sense_verified=true only;
    'sense_unverified' review pile is NEVER read here)
  - NIKL fetch: tools/dataset_v3_phase0/fetch_raw/fetch_index.json
    (rel_info_rows already parsed per sense; only type 유의어 -> similar
    and 반대말 -> opposite are merged; 참고어/준말/높임말 etc. ignored)

Rules (Byte's accuracy rule first):
  - The 236 v1 hand-verified relations are NEVER modified — new rows are
    additive only, and a new row that duplicates a v1 row (same kind +
    target) is skipped.
  - Never invent a relation. A source with none for a word leaves the
    arrays empty.
  - Dedup per (entry, kind, target). Drop self-relations and targets not
    in the 10,000-word set; count every drop in merge_stats.json.
  - Target resolution (GATE A, added after the 100-row audit): NIKL rows
    are merged ONLY when link_target_code resolves through the fetch
    index to a seed entry whose korean matches the rel word. A bare
    korean-string match is NOT accepted. Audit evidence: of the 16
    string-resolved rows in the audit sample, 11 were wrong (the rel
    points at a different homograph entry than the seed carries — e.g.
    수요일~수 'male gender', 국수~면 'township', 강국↔약국 'pharmacy',
    죽음~사 'four'), 1 unclear, 4 correct; code-resolved rows were
    ~90% correct, 1% wrong. Losing ~4 correct rows per 100 is the price
    of removing ~11 wrong ones. The dropped rows are NOT lost: every
    rel_info row stays in fetch_raw/fetch_index.json, so a later merge
    (e.g. once the app shows all senses) can restore them.
  - Fix the seed gloss bugs FIRST (reorder senses + primary english):
    공원 HM-10092 "factory worker" -> "park"; 악 HM-18814 "energy one
    musters up" -> "evil" (verbatim NIKL sense definitions; other senses
    kept as secondary); 오르막길 HM-16823 "high to low" -> "low to high"
    (audit find: the v2 definition is backwards vs NIKL's own definition,
    corrected in place).
  - New rows carry {"korean": target, "noteNe": "", "source": "nikl" |
    "kaikki-ko"} — neither source gives a Nepali note. The v3 validator
    allows empty noteNe ONLY for source-marked rows.

Outputs (all under tools/dataset_v3_phase0/ unless --seed-out is given):
  vocabulary_seed_v3.json, merge_stats.json, audit_sample_v3.json,
  DATA_QUALITY_REPORT_V3.md

Usage:
    python3 merge_v3.py [--seed-out PATH] [--audit-n N]
"""
import argparse
import copy
import json
import random
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
SEED_V2 = ROOT / "app" / "src" / "main" / "assets" / "seed" / "vocabulary_seed.json"
KAIKKI = HERE / "kaikki_relations.json"
INDEX = HERE / "fetch_raw" / "fetch_index.json"

GLOSS_FIXES = {
    # id: (new primary english, sense definition that must become senses[0])
    "HM-10092": ("A large place with grass, trees and flowers where people "
                  "can play and rest.",
                  "A large place with grass, trees and flowers where people "
                  "can play and rest."),
    "HM-18814": ("Something not right, bad, and against a moral standard.",
                  "Something not right, bad, and against a moral standard."),
}

# Gloss fix 3 (found by the v3 audit, 2026-11-11): a definition's wording is
# backwards, not a sense-order problem. NIKL: 오르막길 sense 1 =
# "낮은 곳에서 높은 곳으로 이어지는 비탈진 길" (low -> high, i.e. uphill),
# but the v2 seed says "from high to low" — 내리막길's definition.
# id: (wrong definition string, corrected string)
GLOSS_TEXT_FIXES = {
    "HM-16823": ("An inclined path from high to low terrain.",
                  "An inclined path from low to high terrain."),
}


def apply_text_fix(entry: dict, old: str, new: str) -> None:
    done = False
    for s in entry.get("senses") or []:
        defs = s.get("definitions") or []
        for i, d in enumerate(defs):
            if d == old:
                defs[i] = new
                done = True
    if not done:
        raise ValueError(f"gloss text fix: definition not found in {entry['id']}")
    if entry.get("english") == old:
        entry["english"] = new


def reorder_senses(entry: dict, first_definition: str) -> None:
    senses = entry.get("senses") or []
    hit = None
    for s in senses:
        defs = s.get("definitions") or []
        if defs and defs[0] == first_definition:
            hit = s
            break
    if hit is None:
        raise ValueError(f"gloss fix: sense not found in {entry['id']}")
    senses.remove(hit)
    senses.insert(0, hit)
    entry["senses"] = senses


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--seed-out", type=str, default=str(HERE / "vocabulary_seed_v3.json"))
    ap.add_argument("--audit-n", type=int, default=100)
    args = ap.parse_args()

    seed = json.loads(SEED_V2.read_text(encoding="utf-8"))
    by_id = {e["id"]: e for e in seed}
    by_korean = {e["korean"]: e for e in seed}
    assert len(by_korean) == len(seed), "seed has duplicate korean"
    out = copy.deepcopy(seed)
    out_by_id = {e["id"]: e for e in out}
    out_by_korean = {e["korean"]: e for e in out}

    stats = Counter()
    audit_rows = []

    # --- 1. gloss fixes -------------------------------------------------
    for eid, (new_en, first_def) in GLOSS_FIXES.items():
        e = out_by_id[eid]
        old_en = e["english"]
        reorder_senses(e, first_def)
        e["english"] = new_en
        stats[f"gloss_fix_{eid}"] += 1
        print(f"gloss fix {eid} {e['korean']}: {old_en!r} -> {new_en!r}")
    for eid, (old_def, new_def) in GLOSS_TEXT_FIXES.items():
        e = out_by_id[eid]
        apply_text_fix(e, old_def, new_def)
        stats[f"gloss_fix_{eid}"] += 1
        print(f"gloss fix {eid} {e['korean']}: {old_def!r} -> {new_def!r}")

    # --- 2. NIKL relations ----------------------------------------------
    index = json.loads(INDEX.read_text(encoding="utf-8"))
    entries = index["entries"]
    # target_code -> seed entry (only for successfully fetched words)
    code_to_entry = {}
    for eid, v in entries.items():
        if v.get("status") == "view_done" and v.get("target_code"):
            code_to_entry[v["target_code"]] = by_id[eid]
    stats["fetch_index_entries"] = len(entries)
    stats["fetch_view_done"] = sum(1 for v in entries.values()
                                   if v.get("status") == "view_done")
    stats["fetch_unresolved"] = sum(1 for v in entries.values()
                                    if v.get("status") == "unresolved")
    stats["fetch_error"] = sum(1 for v in entries.values()
                               if v.get("status") == "error")

    KIND_MAP = {"유의어": "similar", "반대말": "opposite"}

    def add_row(entry, kind, target_word, source, evidence):
        """Add a relation row if new and in-set. Returns outcome string."""
        if target_word == entry["korean"]:
            return "dropped_self"
        tgt = out_by_korean.get(target_word)
        if tgt is None:
            return "dropped_out_of_set"
        arr = entry[kind]
        if any(r.get("korean") == target_word for r in arr):
            return "skipped_duplicate"
        arr.append({"korean": target_word, "noteNe": "", "source": source})
        audit_rows.append({"source_word": entry["korean"], "source_id": entry["id"],
                           "kind": kind, "target": target_word,
                           "target_id": tgt["id"], "provenance": source,
                           "evidence": evidence,
                           "pos_match": entry["partOfSpeech"] == tgt["partOfSpeech"]})
        return "added"

    for eid, v in entries.items():
        if v.get("status") != "view_done":
            continue
        entry = out_by_id[eid]
        seen = set()
        for row in v.get("rel_info_rows") or []:
            typ = row.get("type")
            if typ not in KIND_MAP:
                stats[f"nikl_ignored_type_{typ}"] += 1
                continue
            kind = KIND_MAP[typ]
            target_word = row.get("word")
            if not target_word:
                stats["nikl_empty_word"] += 1
                continue
            # GATE A: resolve ONLY via link_target_code -> seed entry with
            # matching korean. A bare string match is dropped (see docstring).
            tc = row.get("link_target_code")
            resolved_via = "code"
            if not tc:
                stats["nikl_dropped_no_target_code"] += 1
                continue
            if tc not in code_to_entry:
                stats["nikl_dropped_code_not_in_set"] += 1
                continue
            if code_to_entry[tc]["korean"] != target_word:
                stats["nikl_dropped_code_word_mismatch"] += 1
                continue
            key = (kind, target_word)
            if key in seen:
                stats["nikl_dedup_same_entry"] += 1
                continue
            seen.add(key)
            evidence = (f"NIKL view {v.get('target_code')} sense "
                        f"{row.get('sense')}: rel_info type={typ} word={target_word} "
                        f"link_target_code={tc} (resolved via {resolved_via})")
            outcome = add_row(entry, kind, target_word, "nikl", evidence)
            stats[f"nikl_{outcome}"] += 1

    # --- 3. Kaikki verified rows -----------------------------------------
    kaikki = json.loads(KAIKKI.read_text(encoding="utf-8"))
    words = kaikki["words"]
    for headword, buckets in words.items():
        entry = out_by_korean.get(headword)
        if entry is None:
            stats["kaikki_headword_not_in_seed"] += 1
            continue
        for kind in ("similar", "opposite"):
            for row in buckets.get(kind) or []:
                assert row.get("sense_verified") is True, \
                    f"unverified Kaikki row slipped in: {headword} {row}"
                outcome = add_row(entry, kind, row["korean"], "kaikki-ko",
                                  f"Kaikki ko-extract sense_verified=true; "
                                  f"dump_pos={row.get('dump_pos')}")
                stats[f"kaikki_{outcome}"] += 1
    stats["kaikki_unverified_rows_ignored"] = sum(
        len(v.get("sense_unverified", [])) for v in words.values())

    # --- 4. counts --------------------------------------------------------
    for kind in ("similar", "opposite", "confusing"):
        stats[f"v3_{kind}_rows"] = sum(len(e[kind]) for e in out)
        stats[f"v3_{kind}_words"] = sum(1 for e in out if e[kind])
    stats["v3_total_entries"] = len(out)
    for src in ("nikl", "kaikki-ko"):
        stats[f"v3_rows_source_{src}"] = sum(
            1 for e in out for kind in ("similar", "opposite")
            for r in e[kind] if r.get("source") == src)
    # v1 rows have no source field
    stats["v3_rows_source_v1"] = sum(
        1 for e in out for kind in ("similar", "opposite", "confusing")
        for r in e[kind] if "source" not in r)

    # --- 5. audit sample ---------------------------------------------------
    random.seed(20261111)
    audit_rows.sort(key=lambda r: (r["provenance"], r["source_word"]))
    n = min(args.audit_n, len(audit_rows))
    sample = random.sample(audit_rows, n) if audit_rows else []
    # Enrich each sampled row with the source sense's Korean definition
    # (re-read from the stored view XML) and the target's seed English
    # gloss, so a human can judge sense fit without re-fetching.
    import xml.etree.ElementTree as ET
    view_dir = HERE / "fetch_raw" / "view"
    for row in sample:
        sid = row["source_id"]
        v = entries.get(sid, {})
        sense_no = None
        for r in v.get("rel_info_rows") or []:
            if r.get("word") == row["target"] and r.get("type") in KIND_MAP:
                sense_no = r.get("sense")
                break
        definition = None
        vp = view_dir / f"{sid}.xml"
        if vp.exists() and sense_no:
            try:
                root = ET.parse(vp).getroot()
                senses = list(root.iter("sense_info"))
                if 1 <= sense_no <= len(senses):
                    definition = senses[sense_no - 1].findtext("definition")
            except ET.ParseError:
                definition = None
        row["source_sense_no"] = sense_no
        row["source_sense_ko"] = definition
        tgt = out_by_id.get(row["target_id"])
        row["target_english"] = tgt["english"] if tgt else None
        row["source_english"] = out_by_id[sid]["english"]
    (HERE / "audit_sample_v3.json").write_text(
        json.dumps(sample, ensure_ascii=False, indent=1), encoding="utf-8")
    (HERE / "merge_stats.json").write_text(
        json.dumps(dict(stats), ensure_ascii=False, indent=1), encoding="utf-8")

    # --- 6. write seed v3 --------------------------------------------------
    out_path = Path(args.seed_out)
    out_path.write_text(json.dumps(out, ensure_ascii=False, indent=1),
                        encoding="utf-8")
    print(f"wrote {out_path} ({out_path.stat().st_size} bytes)")
    print("stats:", json.dumps(dict(stats), ensure_ascii=False, indent=1))
    print(f"audit sample: {len(sample)} rows -> audit_sample_v3.json")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
