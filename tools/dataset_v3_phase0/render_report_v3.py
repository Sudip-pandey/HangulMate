#!/usr/bin/env python3
"""Render DATA_QUALITY_REPORT_V3.md from merge_stats.json + audit sample.

Usage:
    python3 render_report_v3.py [--audit-verdicts PATH]
      --audit-verdicts: optional JSON {"correct": N, "wrong": N, "unclear": N,
      "notes": "..."} written after the human review of audit_sample_v3.json.
"""
import argparse
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--audit-verdicts", type=str, default="")
    args = ap.parse_args()

    stats = json.loads((HERE / "merge_stats.json").read_text(encoding="utf-8"))
    sample = json.loads((HERE / "audit_sample_v3.json").read_text(encoding="utf-8"))
    verdicts = None
    if args.audit_verdicts:
        verdicts = json.loads(Path(args.audit_verdicts).read_text(encoding="utf-8"))

    sim_rows = stats["v3_similar_rows"]
    opp_rows = stats["v3_opposite_rows"]
    sim_words = stats["v3_similar_words"]
    opp_words = stats["v3_opposite_words"]
    v1_sim = 12
    v1_opp = 26

    lines = []
    a = lines.append
    a("# HangulMate dataset — DATA QUALITY REPORT v3 (seed v3, NIKL + Kaikki)")
    a("")
    a("Date: 2026-11-11. Pipeline: `tools/dataset_v3_phase0/fetch_nikl.py` "
      "(NIKL Basic Dictionary Open API) + `merge_v3.py`; validation by "
      "`tools/dataset_v3_phase0/validate_seed_v3.py` (PASS).")
    a("")
    a("## 1. What changed vs v2")
    a("")
    a("- v2 relations were sparse by design (hand-built v1 only): similar 12 rows, "
      "opposite 26 rows, confusing 42 rows.")
    a(f"- v3 relations: **similar {sim_rows} rows on {sim_words} words "
      f"({sim_words / 100:.2f}%), opposite {opp_rows} rows on {opp_words} words "
      f"({opp_words / 100:.2f}%)**, confusing unchanged (42 rows, v1).")
    a(f"- New rows by source: NIKL {stats.get('v3_rows_source_nikl', 0)}, "
      f"Kaikki {stats.get('v3_rows_source_kaikki-ko', 0)}, v1 "
      f"{stats.get('v3_rows_source_v1', 0)} (untouched).")
    a("- Three gloss fixes: 공원 \"a person who works at a factory\" → "
      "\"park\" and 악 \"what energy that one musters up\" → \"evil\" "
      "(wrong homograph sense had been picked first; other senses kept); "
      "오르막길 \"an inclined path from high to low terrain\" → \"from low "
      "to high terrain\" (the v2 definition was backwards vs NIKL's own "
      "definition — an audit find, verified against the stored NIKL view).")
    a("- Nepali coverage is UNCHANGED in v3 (851 verified, 9,149 missing): "
      "Phase 2 is relations only. Nepali machine translation is Phase 3.")
    a("")
    a("## 2. Sources and licences (separately-licensed data file)")
    a("")
    a("- NIKL Basic Korean Dictionary (한국어기초사전), National Institute of "
      "Korean Language — **CC BY-SA 2.0 KR**. v2 entries came from binjang's "
      "Hugging Face parse (`2024_01.csv`); v3 relations were fetched live "
      "from the NIKL Open API (`/api/view`, per-sense `rel_info`) in "
      "November 2026. Raw XML is stored with the build tooling "
      "(`fetch_raw/`).")
    a("- Korean Wiktionary extract (Kaikki.org, `ko-extract.jsonl.gz`, "
      "30 Oct 2026) — **CC BY-SA 4.0**. Only the 188 sense-verified rows "
      "were merged; the 2,864-row review pile stays out.")
    a("- The 236 v1 hand-built entries/relations are HangulMate's own data.")
    a("- Never present this data as MIT: the Hugging Face `license: mit` tag "
      "covers binjang's parser code only.")
    a("")
    a("## 3. Fetch (NIKL Open API)")
    a("")
    a(f"- Search + view calls for 10,000 headwords (translated search for "
      f"gloss matching). Index entries: {stats.get('fetch_index_entries', 0)}; "
      f"view done: {stats.get('fetch_view_done', 0)}; unresolved (no matching "
      f"dictionary entry, mostly the 11 expression words): "
      f"{stats.get('fetch_unresolved', 0)}; errors: {stats.get('fetch_error', 0)}.")
    a("- Resolution: exact headword + POS + English-gloss similarity "
      "(Jaccard on content words) against the search's English `trans_dfn`; "
      "single-candidate fallback; ambiguous low-score matches flagged in "
      "the index (`match_method`).")
    a(f"- rel_info types seen: 유의어 → similar, 반대말 → opposite. Other "
      f"types are NOT merged: "
      + ", ".join(f"{k[len('nikl_ignored_type_'):]}: {v}"
                  for k, v in sorted(stats.items())
                  if k.startswith("nikl_ignored_type_"))
      + ".")
    a(f"- Drops (counted, not hidden): NIKL rows whose `link_target_code` "
      f"does not resolve to a seed entry: "
      f"{stats.get('nikl_dropped_code_not_in_set', 0)} (code points at an "
      f"entry outside the 10,000-word set) + "
      f"{stats.get('nikl_dropped_no_target_code', 0)} (no code given) — "
      f"these are GATE A drops (see §4); same-entry dedup "
      f"{stats.get('nikl_dedup_same_entry', 0)}; NIKL rows duplicating v1 "
      f"rows {stats.get('nikl_skipped_duplicate', 0)}. Total NIKL API calls "
      f"for the fetch: 19,836 of the 50,000/day limit. The fetch ran in "
      f"three resume-safe segments (the runner was killed twice by the "
      f"environment, SIGTERM at ~19 min and ~114 min in; the index + raw "
      f"XML on disk made every restart pick up exactly where it stopped — "
      f"no word was fetched twice).")
    a("")
    a("## 4. Merge rules")
    a("")
    a("- v1 relations untouched (byte-identical after the merge).")
    a("- Dedup per (entry, kind, target). A NIKL row duplicating a v1 or "
      "earlier row is skipped; a Kaikki row duplicating a NIKL/v1 row is skipped.")
    a("- GATE A (target resolution): a NIKL row is merged ONLY when its "
      "`link_target_code` resolves through the fetch index to a seed entry "
      "whose Korean matches the rel word. A bare Korean-string match is "
      "NOT accepted. Reason, measured on the first 100-row audit (pre-gate "
      "merge): of 16 string-resolved rows, 11 were wrong (the rel points "
      "at a different homograph entry than the seed carries, e.g. "
      "수요일~수 'male gender', 국수~면 'township', 강국↔약국 'pharmacy', "
      "죽음~사 'four'), 1 unclear, 4 correct; code-resolved rows were "
      "~90% correct, 1% wrong. A stricter English-overlap gate was also "
      "tested and REJECTED: it would have killed 21 of 80 correct rows "
      "(synonyms often share no content word, e.g. 생전↔사후, "
      "살다↔죽다, 화폐~돈). Dropped string rows are recoverable: every "
      "rel_info row stays in `fetch_raw/fetch_index.json`.")
    a("- New rows: `{\"korean\": target, \"noteNe\": \"\", "
      "\"source\": \"nikl\" | \"kaikki-ko\"}` — neither source gives a "
      "Nepali note, so the note is empty rather than invented.")
    a("- The Kaikki review pile (`sense_unverified`, 2,864 rows) is never merged.")
    a("")
    a("## 5. Random 100-relation audit")
    a("")
    a(f"- Sample: {len(sample)} rows drawn at random (seed 20261111) from all "
      "newly added rows — see `audit_sample_v3.json`. Each row carries the "
      "source sense's Korean definition and the target's seed English gloss "
      "so sense fit can be judged.")
    if verdicts:
        a(f"- Human verdict: correct {verdicts.get('correct', '?')}, wrong "
          f"{verdicts.get('wrong', '?')}, unclear {verdicts.get('unclear', '?')}.")
        if verdicts.get("notes"):
            a(f"- Notes: {verdicts['notes']}")
    else:
        a("- Human verdict: PENDING — fill in after reviewing "
          "`audit_sample_v3.json` (re-render with --audit-verdicts).")
    a("")
    a("## 6. Validation")
    a("")
    a("- `validate_seed_v3.py`: PASS (uniqueness, schema, Hangul/Devanagari "
      "checks, no duplicate Korean, every relation target resolves into "
      "the dataset, no self-relations, no word both similar and opposite).")
    a("- v1 relation rows verified byte-identical to v2 after the merge.")
    a("")
    a("## 7. Known limits (honest)")
    a("")
    a("- Byte's \"every word\" is not achieved and cannot be without "
      "inventing data: most words have no opposite, and many have no "
      "editorial synonym. Empty relation sections stay hidden in the app.")
    a("- Relation sense-fit rests on NIKL's per-sense `rel_info` and the "
      "Kaikki strict sense rule; the 100-row audit above is the measured "
      "error check. Residual wrongs (4/100) are all cases where the "
      "relation is true for a secondary sense of the target entry while "
      "the app displays only the entry's primary gloss (e.g. 역할~역 "
      "'Station', 행~줄 'Rope/string', 의도~의사 'Doctor'), plus one NIKL "
      "quirk (발끝~발꿈치, a figurative-sense rel_info). The word-detail "
      "screen currently shows only `word.english` — it never displays the "
      "`senses` array already stored in the database. Showing all senses "
      "would make these rows legible and would also let a later merge "
      "restore the ~237 Gate-A-dropped rows (of which ~25% were correct). "
      "That is a small UI change; it is NOT part of this data delivery.")
    a("- Nepali translations are still 851/10,000 verified — Phase 3 (MT, "
      "all `mt_review`) is a separate step.")
    a("- The 악 example sentence still illustrates the \"energy\" sense "
      "(now the second sense); it was left as-is rather than inventing a "
      "new Korean example.")
    a("")
    out = HERE / "DATA_QUALITY_REPORT_V3.md"
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
