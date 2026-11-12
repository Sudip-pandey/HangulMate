#!/usr/bin/env python3
"""PanLex join test for HangulMate seed v3 (Phase 0).
Joins kor (Hangul) x npi/npl on PanLex `meaning` id, matches against the
9,149 seed words lacking verified Nepali. Read-only wrt the seed.
"""
import json, re, random
from collections import OrderedDict
import pandas as pd

BASE = "/home/hatch/workspace/hangulmate/tools/dataset_v3_phase0"
DL = BASE + "/downloads"
SEED = "/home/hatch/workspace/hangulmate/app/src/main/assets/seed/vocabulary_seed.json"
REV = "95d5b0e9941a5aee02559169ae906e25921385e6"

HANGUL = re.compile(r"[\uAC00-\uD7A3\u1100-\u11FF\u3130-\u318F]")

# --- load seed, target set ---
with open(SEED, encoding="utf-8") as f:
    seed = json.load(f)
targets = [e for e in seed if e.get("nepaliStatus") != "verified"]
verified = [e for e in seed if e.get("nepaliStatus") == "verified"]
print(f"seed total={len(seed)} verified={len(verified)} targets={len(targets)}")
target_by_korean = {}
for e in targets:
    target_by_korean.setdefault(e["korean"], []).append(e)

# --- load PanLex tables ---
kor = pd.read_csv(f"{DL}/kor.tsv", sep="\t", dtype=str, keep_default_na=False)
npi = pd.read_csv(f"{DL}/npi.tsv", sep="\t", dtype=str, keep_default_na=False)
npl = pd.read_csv(f"{DL}/npl.tsv", sep="\t", dtype=str, keep_default_na=False)
print(f"kor rows={len(kor)} npi rows={len(npi)} npl rows={len(npl)}")

# Korean side: Hangul txt only, in file order
kor_hangul = kor[kor["txt"].apply(lambda s: bool(HANGUL.search(s)))]
print(f"kor Hangul rows={len(kor_hangul)}")
# meaning -> [korean txts in file order]
kor_by_meaning = OrderedDict()
for _, r in kor_hangul.iterrows():
    kor_by_meaning.setdefault(r["meaning"], []).append(r["txt"])
# korean txt -> [meanings in file order] (dedup preserving order)
ko_to_meanings = {}
for m, txts in kor_by_meaning.items():
    for t in txts:
        if t not in ko_to_meanings:
            ko_to_meanings[t] = []
        if m not in ko_to_meanings[t]:
            ko_to_meanings[t].append(m)

def nep_by_meaning(df):
    d = OrderedDict()
    for _, r in df.iterrows():
        d.setdefault(r["meaning"], []).append(r["txt"])
    return d

npi_by_meaning = nep_by_meaning(npi)
npl_by_meaning = nep_by_meaning(npl)
print(f"distinct kor meanings (Hangul)={len(kor_by_meaning)}")
print(f"distinct npi meanings={len(npi_by_meaning)} distinct npl meanings={len(npl_by_meaning)}")
shared_npi = set(kor_by_meaning) & set(npi_by_meaning)
shared_npl = set(kor_by_meaning) & set(npl_by_meaning)
print(f"shared meanings kor-npi={len(shared_npi)} kor-npl={len(shared_npl)}")

# --- match targets ---
results = []  # one per target word (korean headword)
for e in targets:
    k = e["korean"]
    meanings = ko_to_meanings.get(k, [])
    cands = []       # (nepali, meaning, source_config)
    seen = set()
    for m in meanings:
        for src, table in (("npi", npi_by_meaning), ("npl", npl_by_meaning)):
            for t in table.get(m, []):
                key = (t, src)
                if key not in seen:
                    seen.add(key)
                    cands.append({"nepali": t, "meaning": int(m), "source_config": src})
    if cands:
        results.append({
            "korean": k,
            "seed_id": e.get("id"),
            "partOfSpeech": e.get("partOfSpeech"),
            "english": e.get("english"),
            "exampleKo": e.get("exampleKo"),
            "nepali_candidates": [c["nepali"] for c in cands],
            "meaning_ids": [c["meaning"] for c in cands],
            "candidate_sources": [c["source_config"] for c in cands],
            "source": "panlex",
            "status": "candidate",
        })

matched = len(results)
print(f"matched target words: {matched} / {len(targets)} = {matched/len(targets)*100:.2f}%")
single = sum(1 for r in results if len(r["nepali_candidates"]) == 1)
multi = sum(1 for r in results if len(r["nepali_candidates"]) > 1)
print(f"exactly one candidate: {single}; multiple: {multi}")
from collections import Counter
print("candidate-count distribution:", dict(sorted(Counter(len(r['nepali_candidates']) for r in results).items())))
npi_only = sum(1 for r in results if set(r["candidate_sources"]) == {"npi"})
npl_only = sum(1 for r in results if set(r["candidate_sources"]) == {"npl"})
both = sum(1 for r in results if set(r["candidate_sources"]) == {"npi", "npl"})
print(f"via npi only={npi_only}, npl only={npl_only}, both={both}")

# how many seed headwords exist in kor at all (Hangul)?
in_kor = sum(1 for e in targets if e["korean"] in ko_to_meanings)
print(f"target headwords present in kor (Hangul) at all: {in_kor} / {len(targets)}")

# --- write panlex_join.json ---
out = {
    "header": {
        "dataset": "gtak1/panlex-meanings",
        "revision": REV,
        "license": "CC0 (PanLex data); dataset card: gtak1/panlex-meanings",
        "korean_config": "kor (Hangul txt rows only)",
        "nepali_configs": ["npi (primary, langvar 503)", "npl (secondary, langvar 7556)"],
        "total_kor_rows": int(len(kor)),
        "total_kor_hangul_rows": int(len(kor_hangul)),
        "total_npi_rows": int(len(npi)),
        "total_npl_rows": int(len(npl)),
        "seed_target_words": len(targets),
        "matched_count": matched,
        "matched_percent": round(matched / len(targets) * 100, 2),
        "exactly_one_candidate": single,
        "multiple_candidates": multi,
        "note": "Candidates are NOT verified Nepali. Every row is a candidate for human/MT review. 851 verified entries untouched.",
    },
    "matches": [
        {"korean": r["korean"], "nepali_candidates": r["nepali_candidates"],
         "meaning_ids": r["meaning_ids"], "source": "panlex", "status": "candidate"}
        for r in results
    ],
}
with open(f"{BASE}/panlex_join.json", "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
print("wrote panlex_join.json")

# --- random 50 for hand audit ---
random.seed(20261111)
sample = random.sample(results, min(50, len(results)))
with open(f"{BASE}/audit_sample.json", "w", encoding="utf-8") as f:
    json.dump(sample, f, ensure_ascii=False, indent=1)
for i, r in enumerate(sample, 1):
    print(f"{i:2d}. {r['korean']} [{r['partOfSpeech']}] EN:{r['english'][:60]} | NE: {' / '.join(r['nepali_candidates'])} | ex: {r['exampleKo']}")
