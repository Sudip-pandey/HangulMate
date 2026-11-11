#!/usr/bin/env python3
"""NIKL -> HangulMate seed pipeline (v2).

Reads data/raw/2024_01.csv (binjang's parse of NIKL's Basic Korean
Dictionary), merges rows by headword Form into entries with a senses
array, selects exactly 10,000 unique headwords (EPS-first), and writes:

  data/processed/vocabulary_seed_v2.json  - full 10,000-entry seed (v2)
  data/processed/phrases.json            - spaced idioms/phrases (separate table)
  data/processed/stats.json              - every count the report needs
  app/src/main/assets/seed/vocabulary_seed.json - the same seed, shipped

Usage:
  python3 tools/nikl_pipeline.py [--selftest]

Stdlib only (csv / ast / json). Nepali translations come from
tools/nepali_core.py (hand-translated EPS core, status "verified");
every other entry ships nepali=null, status "missing" -- never
fabricated. Run tools/validate_seed_v2.py afterwards.
"""
import ast
import csv
import html
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parent
RAW_CSV = ROOT / "data" / "raw" / "2024_01.csv"
V1_SEED = ROOT / "data" / "raw" / "vocabulary_seed_v1.json"
V1_SEED_BACKUP = ROOT / "data" / "processed" / "vocabulary_seed_v1_backup.json"
OUT_PROCESSED = ROOT / "data" / "processed" / "vocabulary_seed_v2.json"
OUT_ASSET = ROOT / "app" / "src" / "main" / "assets" / "seed" / "vocabulary_seed.json"
OUT_PHRASES = ROOT / "data" / "processed" / "phrases.json"
OUT_STATS = ROOT / "data" / "processed" / "stats.json"

sys.path.insert(0, str(HERE))
from romanization import romanize, SELFTEST  # noqa: E402

TARGET_TOTAL = 10_000
MAX_SENSES = 8
MAX_DEFS_PER_SENSE = 3
JAMO_RE = re.compile(r"^[ㄱ-ㅎㅏ-ㅣ]+$")
HANGUL_RE = re.compile(r"^[가-힣]+$")

# POS that are not standalone dictionary words (affix / ending / particle).
AFFIX_POS = {"접사", "어미", "조사"}
POS_MAP = {
    "명사": "noun", "의존 명사": "noun",
    "동사": "verb", "보조 동사": "verb",
    "형용사": "adjective", "보조 형용사": "adjective",
    "관형사": "adjective",  # determiner; closest app POS
    "부사": "adverb",
    "감탄사": "interjection",
    "대명사": "pronoun",
    "수사": "number",
    "": "noun",  # NIKL leaves POS blank on 9.3% of rows (app default)
}
CONTENT_POS = {"noun", "verb", "adjective", "adverb"}

# NIKL level -> (difficulty, epsImportance). Best level wins per form.
LEVEL_ORDER = {"초급": 0, "중급": 1, "고급": 2, "": 3}
LEVEL_META = {"초급": (1, "high"), "중급": (2, "medium"),
              "고급": (3, "medium"), "": (2, "low")}
LEVEL_TAG = {"초급": "beginner", "중급": "intermediate", "고급": "advanced"}


def topic_from_semantic_category(cat):
    """Map a NIKL 'A > B' semantic category to an app Topic key."""
    if not cat:
        return None
    head, _, tail = cat.partition(" > ")
    if head == "식생활":
        return "food"
    if head == "사회 생활":
        if tail.startswith("직장") or tail in ("직업", "직위"):
            return "workplace"
        if tail.startswith("교통"):
            return "transport"
        if tail in ("언어 행위", "말", "소통 수단", "통신 행위"):
            return "conversation"
        if tail == "매체":
            return "general"
        return "daily"
    if head == "경제 생활":
        return "workplace"
    if head == "인간":
        if tail.startswith(("신체", "생리", "체력", "감각", "용모")):
            return "health"
        if tail.startswith(("감정", "태도", "성격")):
            return "conversation"
        if tail in ("인칭",):
            return "conversation"
        if tail in ("사람의 종류",):
            return "daily"
        return "general"
    if head == "삶":
        if tail.startswith(("병과 증상", "치료", "약품류")):
            return "health"
        if tail.startswith(("친족", "가족", "여가", "일상", "삶의")):
            return "daily"
        return "daily"
    if head in ("주생활", "의생활"):
        return "daily"
    if head == "개념":
        if tail in ("수", "세는 말", "순서", "양"):
            return "numbers"
        if tail == "시간":
            return "daily"
        if tail in ("위치 및 방향", "지역"):
            return "directions"
        if tail in ("지시", "의문"):
            return "conversation"
        return "general"
    if head == "정치와 행정":
        if tail.startswith("사법 및 치안"):
            return "safety"
        return "general"
    if head == "자연":
        if tail == "기상 및 기후":
            return "daily"
        return "general"
    # 교육, 문화, 종교, 동식물, and anything unlisted -> general
    return "general"


# Keyword fallback (word-boundary) on the first English gloss, used only
# when the row has no semantic category at all.
TOPIC_KEYWORDS = [
    ("numbers", [r"\b(counting|numeral)\b"]),
    ("health", [r"\b(doctor|hospital|disease|illness|medicine|pain|fever|injury|body|tooth|headache|treatment|sick)\b"]),
    ("food", [r"\b(food|eat|eating|drink|rice|meal|cook|cooking|fruit|vegetable|meat|dish|restaurant)\b"]),
    ("transport", [r"\b(bus|train|car|airplane|ship|bicycle|road|street|station|airport|traffic|vehicle|subway)\b"]),
    ("workplace", [r"\b(work|job|company|office|salary|wage|employee|boss|factory worker|labo[u]?r)\b"]),
    ("tools", [r"\b(tool|hammer|knife|machine|equipment|device|instrument)\b"]),
    ("safety", [r"\b(safe|danger|dangerous|accident|warning|fire|emergency|protect|safety)\b"]),
    ("agriculture", [r"\b(farm|farmer|rice field|crop|harvest|plant|seed|soil)\b"]),
    ("manufacturing", [r"\b(factory|manufacture|produce|production|product|industry)\b"]),
    ("construction", [r"\b(build|construct|construction|brick|cement|building site)\b"]),
    ("directions", [r"\b(north|south|east|west|left|right|front|back|direction|turn|straight|corner)\b"]),
    ("conversation", [r"\b(speak|talk|say|ask|answer|greet|hello|conversation|language)\b"]),
]


def topic_from_keywords(gloss):
    if not gloss:
        return "general"
    low = " " + gloss.lower()[:80] + " "
    for topic, patterns in TOPIC_KEYWORDS:
        for pat in patterns:
            if re.search(pat, low):
                return topic
    return "general"


def parse_list_field(raw):
    """Parse a NIKL Python-literal list field; tolerate plain strings."""
    raw = (raw or "").strip()
    if not raw:
        return []
    try:
        val = ast.literal_eval(raw)
    except (ValueError, SyntaxError):
        return [raw] if raw not in ("[]",) else []
    if isinstance(val, list):
        return val
    return [val]


def clean_gloss(text):
    # NIKL glosses occasionally contain HTML entities (&quot; etc.).
    return re.sub(r"\s+", " ", html.unescape(text or "").strip())


def first_plain_usage(usages):
    """First example sentence: a plain string, else the first line of the
    first nested dialogue pair."""
    for u in usages:
        if isinstance(u, str) and u.strip():
            return u.strip()
    for u in usages:
        if isinstance(u, list):
            for line in u:
                if isinstance(line, str) and line.strip():
                    return line.strip()
    return ""


def main():
    if "--selftest" in sys.argv:
        bad = [(k, romanize(k), v) for k, v in SELFTEST.items()
               if romanize(k) != v]
        for k, g, v in bad:
            print(f"FAIL {k} -> {g} (expect {v})")
        print(f"romanization self-test: {len(SELFTEST) - len(bad)}/{len(SELFTEST)} pass")
        return 0 if not bad else 1

    # --- hand-translated Nepali core (verified) ----------------------
    try:
        from nepali_core import NEPALI_CORE
    except ImportError:
        NEPALI_CORE = {}
    print(f"nepali core entries loaded: {len(NEPALI_CORE)}")

    # --- parse CSV ----------------------------------------------------
    rows = []
    with open(RAW_CSV, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            rows.append(row)
    stats = {"source_rows": len(rows)}

    # Group rows by Form.
    by_form = defaultdict(list)
    for r in rows:
        by_form[r["Form"]].append(r)
    stats["unique_forms"] = len(by_form)

    dropped = Counter()
    phrases = defaultdict(list)  # spaced forms -> rows
    usable = {}  # form -> merged record

    for form, form_rows in by_form.items():
        if form.startswith("-"):
            dropped["hyphen_prefixed_forms"] += 1
            dropped["hyphen_prefixed_rows"] += len(form_rows)
            continue
        if JAMO_RE.match(form):
            dropped["bare_jamo_forms"] += 1
            dropped["bare_jamo_rows"] += len(form_rows)
            continue
        if " " in form.strip():
            dropped["spaced_phrase_forms"] += 1
            dropped["spaced_phrase_rows"] += len(form_rows)
            phrases[form] = form_rows
            continue
        content_rows = [r for r in form_rows if r["Part of Speech"] not in AFFIX_POS]
        if not content_rows:
            dropped["affix_only_forms"] += 1
            dropped["affix_only_rows"] += len(form_rows)
            continue
        if not HANGUL_RE.match(form):
            dropped["non_hangul_forms"] += 1
            dropped["non_hangul_rows"] += len(form_rows)
            continue
        usable[form] = content_rows
    stats["dropped"] = dict(dropped)
    stats["usable_forms"] = len(usable)

    # --- merge each usable form ---------------------------------------
    def merge_form(form, form_rows):
        senses = []
        seen_sense = set()
        for r in form_rows:
            pos = POS_MAP.get(r["Part of Speech"], "noun")
            defs = [clean_gloss(d) for d in parse_list_field(r["English Definition"])]
            defs = [d for d in defs if d][:MAX_DEFS_PER_SENSE]
            if not defs:
                continue
            key = (pos, defs[0])
            if key in seen_sense:
                continue
            seen_sense.add(key)
            senses.append({"pos": pos, "definitions": defs})
        if not senses:
            return None
        meta = re.compile(r"^The name of the (vowel|consonant)\b")
        senses.sort(key=lambda s: 1 if meta.search(s["definitions"][0]) else 0)
        # Entry POS: most frequent sense POS, content POS wins ties.
        pos_counts = Counter(s["pos"] for s in senses)
        entry_pos = max(pos_counts,
                        key=lambda p: (pos_counts[p], p in CONTENT_POS))
        # Best (highest-priority) level among rows.
        level = min((r["Vocabulary Level"] for r in form_rows),
                    key=lambda lv: LEVEL_ORDER.get(lv, 3))
        semcat = next((r["Semantic Category"].strip() for r in form_rows
                       if r["Semantic Category"].strip()), "")
        topic = topic_from_semantic_category(semcat)
        if topic is None:
            if entry_pos == "number":
                topic = "numbers"
            else:
                topic = topic_from_keywords(senses[0]["definitions"][0])
        usages = []
        for r in form_rows:
            usages += parse_list_field(r["Usages"])
        example_ko = first_plain_usage(usages)
        return {
            "form": form,
            "senses": senses[:MAX_SENSES],
            "partOfSpeech": entry_pos,
            "level": level,
            "semanticCategory": semcat or None,
            "topic": topic,
            "exampleKo": example_ko,
            "hasExample": bool(example_ko),
        }

    merged = {}
    for form, form_rows in usable.items():
        rec = merge_form(form, form_rows)
        if rec is not None:
            merged[form] = rec
    stats["merged_forms"] = len(merged)
    stats["merged_forms_no_senses_dropped"] = len(usable) - len(merged)
    stats["level_forms"] = dict(Counter(
        m["level"] or "(none)" for m in merged.values()))

    # --- v1 seed ------------------------------------------------------
    # The asset file is OUR output: never read v1 from it (the first
    # pipeline run overwrites it and a second run would then treat the
    # 10,000-entry v2 file as v1). v1 lives pristine in data/raw/.
    if not V1_SEED.exists():
        print(f"ERROR: pristine v1 seed missing: {V1_SEED}")
        return 1
    v1 = json.loads(V1_SEED.read_text(encoding="utf-8"))
    if len(v1) != 236:
        print(f"ERROR: v1 seed has {len(v1)} entries, expected 236 "
              f"(refusing to run against a non-pristine input)")
        return 1
    v1_by_korean = {e["korean"]: e for e in v1}
    stats["v1_entries"] = len(v1)
    stats["v1_overlapping_nikl"] = sum(1 for k in v1_by_korean if k in merged)

    # --- selection: exactly TARGET_TOTAL - len(v1) new headwords -------
    need_new = TARGET_TOTAL - len(v1)
    candidates = [m for form, m in merged.items() if form not in v1_by_korean]

    def sort_key(m):
        return (
            LEVEL_ORDER.get(m["level"], 3),                       # band first
            0 if m["partOfSpeech"] in CONTENT_POS else 1,        # content first
            0 if m["semanticCategory"] else 1,                   # categorised first
            0 if m["hasExample"] else 1,                         # example first
            m["form"],                                            # deterministic
        )

    candidates.sort(key=sort_key)
    if len(candidates) < need_new:
        print(f"ERROR: only {len(candidates)} candidates for {need_new} slots")
        return 1
    selected = candidates[:need_new]
    stats["selected_new"] = len(selected)
    stats["selected_by_level"] = dict(Counter(
        m["level"] or "(none)" for m in selected))
    stats["unselected_candidates"] = len(candidates) - need_new

    # --- build v2 entries ----------------------------------------------
    def build_entry(merged_rec, new_id):
        form = merged_rec["form"]
        nepali = NEPALI_CORE.get(form)
        status = "verified" if nepali else "missing"
        first_defs = merged_rec["senses"][0]["definitions"]
        difficulty, eps = LEVEL_META[merged_rec["level"]]
        tags = ["nikl"]
        if merged_rec["level"] in LEVEL_TAG:
            tags.append(LEVEL_TAG[merged_rec["level"]])
        return {
            "id": new_id,
            "korean": form,
            "nepali": nepali,  # null when missing (never fabricated)
            "nepaliStatus": status,
            "english": first_defs[0],
            "romanization": romanize(form),
            "syllables": list(form),
            "partOfSpeech": merged_rec["partOfSpeech"],
            "topic": merged_rec["topic"],
            "difficulty": difficulty,
            "epsImportance": eps,
            "exampleKo": merged_rec["exampleKo"],
            "exampleNe": "",
            "exampleEn": "",
            "similar": [],
            "opposite": [],
            "confusing": [],
            "tags": tags,
            "senses": merged_rec["senses"],
            "source": "NIKL",
            "level": merged_rec["level"] or None,
            "semanticCategory": merged_rec["semanticCategory"],
        }

    out = []
    # 1. v1 entries first, preserved verbatim + v2 fields. Where NIKL has
    #    the same headword, attach its senses/level/category (the Nepali
    #    stays the verified v1 translation).
    for e in v1:
        entry = dict(e)
        entry["nepaliStatus"] = "verified"
        entry["source"] = "v1"
        rec = merged.get(e["korean"])
        if rec is not None:
            entry["senses"] = rec["senses"]
            entry["level"] = rec["level"] or None
            entry["semanticCategory"] = rec["semanticCategory"]
            entry["source"] = "NIKL+v1"
        else:
            # Pure v1 entry (no NIKL headword): one sense built from its
            # own verified English gloss, so every entry has >=1 sense.
            entry["senses"] = [{
                "pos": entry["partOfSpeech"],
                "definitions": [entry["english"]],
            }]
            entry["level"] = None
            entry["semanticCategory"] = None
        out.append(entry)
    # 2. New NIKL entries, HM-10001 upward.
    for i, m in enumerate(selected):
        out.append(build_entry(m, f"HM-{10001 + i:05d}"))

    assert len(out) == TARGET_TOTAL, f"built {len(out)} != {TARGET_TOTAL}"
    assert len({e["korean"] for e in out}) == TARGET_TOTAL, "duplicate korean!"
    assert len({e["id"] for e in out}) == TARGET_TOTAL, "duplicate id!"

    # --- phrases table --------------------------------------------------
    phrase_list = []
    for form, form_rows in sorted(phrases.items()):
        defs = []
        for r in form_rows:
            defs += [clean_gloss(d) for d in parse_list_field(r["English Definition"])]
        defs = [d for d in defs if d]
        if not defs:
            continue
        level = min((r["Vocabulary Level"] for r in form_rows),
                    key=lambda lv: LEVEL_ORDER.get(lv, 3))
        phrase_list.append({
            "korean": form,
            "partOfSpeech": POS_MAP.get(form_rows[0]["Part of Speech"], "noun"),
            "english": defs[0],
            "definitions": defs[:3],
            "level": level or None,
            "romanization": romanize(form),
        })
    stats["phrase_entries"] = len(phrase_list)

    # --- counts for the report ------------------------------------------
    stats["final_total"] = len(out)
    stats["nepali_status"] = dict(Counter(e["nepaliStatus"] for e in out))
    stats["with_examples"] = sum(1 for e in out if e["exampleKo"])
    stats["with_pos_nondefault"] = sum(1 for e in out if e["partOfSpeech"] != "noun")
    stats["topic_counts"] = dict(sorted(Counter(e["topic"] for e in out).items()))
    stats["difficulty_counts"] = dict(sorted(Counter(e["difficulty"] for e in out).items()))
    stats["eps_counts"] = dict(sorted(Counter(e["epsImportance"] for e in out).items()))
    stats["pos_counts"] = dict(sorted(Counter(e["partOfSpeech"] for e in out).items()))
    stats["senses_total"] = sum(len(e["senses"]) for e in out)
    stats["senses_per_entry_avg"] = round(stats["senses_total"] / len(out), 2)
    stats["entries_with_multiple_senses"] = sum(1 for e in out if len(e["senses"]) > 1)
    stats["relations_total"] = sum(len(e["similar"]) + len(e["opposite"])
                                   + len(e["confusing"]) for e in out)
    stats["source_counts"] = dict(Counter(e["source"] for e in out))

    # --- write outputs ---------------------------------------------------
    (ROOT / "data" / "processed").mkdir(parents=True, exist_ok=True)
    if not V1_SEED_BACKUP.exists():
        V1_SEED_BACKUP.write_text(
            json.dumps(v1, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_PROCESSED.write_text(
        json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_ASSET.write_text(
        json.dumps(out, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_PHRASES.write_text(
        json.dumps(phrase_list, ensure_ascii=False, indent=1), encoding="utf-8")
    OUT_STATS.write_text(
        json.dumps(stats, ensure_ascii=False, indent=2), encoding="utf-8")

    print(json.dumps(stats, ensure_ascii=False, indent=2))
    print(f"\nwrote {OUT_PROCESSED} ({OUT_PROCESSED.stat().st_size/1e6:.2f} MB)")
    print(f"wrote {OUT_ASSET} ({OUT_ASSET.stat().st_size/1e6:.2f} MB)")
    print(f"wrote {OUT_PHRASES} ({len(phrase_list)} phrases)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
