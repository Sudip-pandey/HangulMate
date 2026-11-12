#!/usr/bin/env python3
"""Manual review demotions for kaikki_relations.json.

After the automated extraction, ALL 271 strict-rule 'verified' rows were reviewed
by hand against the seed's English glosses (verified_review.txt). 83 rows failed:
the dump relation belongs to a Wiktionary sense that our seed entry does NOT use
(mostly: the target's seed gloss describes a different sense, e.g. 구매->사기 where
our 사기 means 'morale', not 'buying'; or the pair is a loose association, not a
near-synonym/antonym, e.g. 가방->지갑 wallet, 버스->승합차 van).

Those rows are moved from similar/opposite into sense_unverified with
sense_verified=false and a manual_review reason. They are NOT deleted, so the v3
merge step (or a human) can re-examine them. Verified rows drop 271 -> 188.
"""
import json
import os

BASE = os.path.expanduser("~/workspace/hangulmate/tools/dataset_v3_phase0")
OUT = os.path.join(BASE, "kaikki_relations.json")

# (headword, kind, target, short reason)
DEMOTE = [
    ("가난뱅이", "opposite", "부자", "target sense mismatch: relation needs 부자='rich person', seed 부자='father and son'"),
    ("가방", "similar", "지갑", "loose association, not a synonym: wallet is not a bag"),
    ("가방", "similar", "백", "target sense mismatch: relation needs 백='bag', seed 백='one hundred'"),
    ("가족", "similar", "가속", "wrong: 가속='acceleration' is not a synonym of family"),
    ("강간", "similar", "폭행", "loose: 폭행='assault/violence' is the broader act, not a synonym of rape"),
    ("거절", "similar", "거부", "target sense mismatch: relation needs 거부='refusal', seed 거부='great wealth'"),
    ("검", "similar", "신", "wrong: 신='shoes' is not a synonym of sword"),
    ("겁", "similar", "공포", "target sense mismatch: relation needs 공포='fear', seed 공포='promulgation of a law'"),
    ("게재", "similar", "등재", "loose: 등재='recording on a ledger' is a different domain from publishing"),
    ("겨울", "similar", "동", "wrong: 동='east' is not a synonym of winter"),
    ("공경", "similar", "경지", "wrong: 경지='level/field' is not a synonym of respect"),
    ("관하다", "similar", "대하다", "target sense mismatch: relation needs 대하다='concerning', seed 대하다='to be face to face'"),
    ("교회", "similar", "상봉", "wrong: 상봉='meeting' is not a synonym of church"),
    ("교회", "opposite", "이별", "wrong: 이별='parting' is not an opposite of church"),
    ("구매", "similar", "사기", "target sense mismatch: relation needs 사기='buying', seed 사기='morale/spirit'"),
    ("국도", "similar", "수도", "target sense mismatch: seed 수도='waterworks'; no 수도 sense is a synonym of national highway"),
    ("근육", "similar", "근", "target sense mismatch: relation needs 근='muscle', seed 근='weight unit'"),
    ("긍정", "opposite", "부정", "target sense mismatch: relation needs 부정='negation', seed 부정='irregularity'"),
    ("기만", "similar", "기사", "wrong: 기사='driver' is not a synonym of deception"),
    ("기만", "similar", "사기", "target sense mismatch: relation needs 사기='fraud', seed 사기='morale/spirit'"),
    ("나그네", "similar", "여인", "wrong: 여인='adult female' is not a synonym of wanderer"),
    ("나이", "similar", "살", "target sense mismatch: relation needs 살='years of age', seed 살='flesh'"),
    ("논", "similar", "답", "target sense mismatch: relation needs 답='paddy field', seed 답='reply'"),
    ("누다", "similar", "싸다", "target sense mismatch: relation needs 싸다='to excrete', seed 싸다='to wrap'"),
    ("누이", "opposite", "형", "not an opposite: contrasting kin term, not 반대말"),
    ("누이", "opposite", "오빠", "not an opposite: contrasting kin term, not 반대말"),
    ("누이", "opposite", "언니", "not an opposite: contrasting kin term, not 반대말"),
    ("눈동자", "similar", "모자", "wrong: 모자='hat' is not a synonym of pupil"),
    ("눈동자", "similar", "수확", "wrong: 수확='harvest' is not a synonym of pupil"),
    ("눈동자", "similar", "안정", "wrong: 안정='stability' is not a synonym of pupil"),
    ("덕분", "similar", "덕", "target sense mismatch: relation needs 덕='favor/help', seed 덕='moral character'"),
    ("돌멩이", "similar", "돌", "target sense mismatch: relation needs 돌='stone', seed 돌='first birthday'"),
    ("동전", "similar", "동화", "wrong: 동화='becoming the same' is not a synonym of coin"),
    ("딸", "similar", "영양", "wrong: 영양='nutrition' is not a synonym of daughter"),
    ("뜰", "similar", "공원", "wrong: seed 공원='factory worker' (mis-glossed); not a synonym of yard"),
    ("무덤", "similar", "구분", "wrong: 구분='categorization' is not a synonym of grave"),
    ("물고기", "similar", "고기", "loose/wrong-sense: 고기='meat' (flesh), not a synonym of live fish"),
    ("민족", "similar", "국민", "loose: 'nationals' is not a synonym of 'ethnic group'"),
    ("민족", "similar", "부족", "target sense mismatch: relation needs 부족='tribe', seed 부족='insufficiency'"),
    ("방언", "similar", "방어", "wrong: 방어='defense' is not a synonym of dialect"),
    ("버스", "similar", "승합차", "loose: a van/minibus is not a synonym of bus"),
    ("부부", "similar", "내외", "target sense mismatch: relation needs 내외='husband and wife', seed 내외='inside and outside'"),
    ("부설", "similar", "놓다", "wrong: 놓다='to put down' is not a synonym of establishment"),
    ("상황", "similar", "정세", "loose: 정세='political situation' is a hyponym, not a synonym of situation"),
    ("설치", "similar", "전설", "wrong: 전설='legend' is not a synonym of installation"),
    ("성공", "opposite", "실패", "target sense mismatch: relation needs 실패='failure', seed 실패='thread spool'"),
    ("속담", "similar", "속어", "wrong: 속어='slang' is a different thing from a proverb"),
    ("솜", "similar", "면", "target sense mismatch: relation needs 면='cotton', seed 면='rural district'"),
    ("수요일", "similar", "수요", "wrong: 수요='demand' is not a synonym of Wednesday"),
    ("스타킹", "similar", "양말", "loose: socks are not a synonym of stockings"),
    ("신형", "opposite", "구형", "target sense mismatch: relation needs 구형='old model', seed 구형='sentencing demand'"),
    ("씩씩거리다", "similar", "씩씩하다", "wrong: 씩씩하다='vigorous' is a different meaning from panting"),
    ("액자", "similar", "틀", "loose: seed 틀='production mold', not a picture frame"),
    ("억지", "similar", "떼", "wrong: 떼='crowd' is not a synonym of obstinacy"),
    ("여류", "similar", "여당", "wrong: 여당='ruling party' is not a synonym of woman professional"),
    ("여름", "similar", "하", "wrong: 하='sound of blowing' is not a synonym of summer"),
    ("영하", "opposite", "영상", "target sense mismatch: relation needs 영상='above zero', seed 영상='picture/image'"),
    ("유골", "similar", "유해", "target sense mismatch: relation needs 유해='remains', seed 유해='hazardous'"),
    ("유해", "similar", "유아", "wrong: 유아='young child' is not a synonym of hazardous"),
    ("유해", "similar", "유골", "target sense mismatch: relation needs 유해='remains', seed 유해='hazardous'"),
    ("이득", "similar", "저리", "wrong: 저리='to that place' is not a synonym of profit"),
    ("이벤트", "similar", "행사", "target sense mismatch: relation needs 행사='event', seed 행사='performing a task'"),
    ("이별", "opposite", "교회", "wrong: 교회='church' is not an opposite of parting"),
    ("인하", "opposite", "인상", "target sense mismatch: relation needs 인상='price rise', seed 인상='face appearance'"),
    ("일본", "similar", "왜", "target sense mismatch: relation needs 왜='Japan (old name)', seed 왜='why'; archaic and misleading for learners"),
    ("자동차", "similar", "차", "target sense mismatch: relation needs 차='vehicle', seed 차='tea'"),
    ("자살", "similar", "자재", "wrong: 자재='materials' is not a synonym of suicide"),
    ("장가", "opposite", "시집", "not an opposite: both are marriage words, not 반대말"),
    ("저가", "opposite", "고가", "target sense mismatch: relation needs 고가='high price', seed 고가='old song'"),
    ("전구", "similar", "전율", "wrong: 전율='trembling' is not a synonym of light bulb"),
    ("제사", "similar", "제", "wrong: 제='humble I' is not a synonym of ancestral rite"),
    ("죄", "similar", "악", "seed gloss wrong: 악 is glossed in the seed as 'energy one musters up' (should be 'evil'); relation is real but cannot be verified against our entry as written -- fix the 악 gloss first"),
    ("청구", "similar", "구청", "wrong: 구청='district office' is not a synonym of billing/demand"),
    ("체면", "similar", "예의", "loose: 예의='politeness' is related but not a synonym of face/dignity"),
    ("출세", "similar", "출신", "wrong: 출신='family background/origin' is not a synonym of rising to a high position"),
    ("타인", "similar", "남", "target sense mismatch: relation needs 남='other people', seed 남='south'"),
    ("하양", "similar", "백", "target sense mismatch: relation needs 백='white', seed 백='one hundred'"),
    ("한국", "similar", "국한", "wrong: 국한='restricting' is not a synonym of Korea"),
    ("한국", "similar", "한정", "wrong: 한정='limiting' is not a synonym of Korea"),
    ("행위", "similar", "소위", "target sense mismatch: relation needs 소위='so-called', seed 소위='second lieutenant'"),
    ("허파", "similar", "폐장", "target sense mismatch: relation needs 폐장='lungs', seed 폐장='closing of a theater/market'"),
    ("현실", "opposite", "이상", "target sense mismatch: relation needs 이상='ideal', seed 이상='above a point'"),
    ("화가", "similar", "화장", "wrong: 화장='makeup' is not a synonym of painter"),
]

d = json.load(open(OUT, encoding="utf-8"))
words = d["words"]
moved = 0
missing = []
for hw, kind, tgt, reason in DEMOTE:
    rows = words.get(hw, {}).get(kind, [])
    hit = [r for r in rows if r["korean"] == tgt]
    if not hit:
        missing.append((hw, kind, tgt))
        continue
    r = hit[0]
    words[hw][kind] = [x for x in rows if x["korean"] != tgt]
    if not words[hw][kind]:
        del words[hw][kind]
    r["sense_verified"] = False
    r["manual_review"] = reason
    r["kind"] = kind
    words[hw].setdefault("sense_unverified", []).append(r)
    moved += 1

assert not missing, f"demote targets not found in verified rows: {missing}"
# recount
vs = sum(len(w.get("similar", [])) for w in words.values())
vo = sum(len(w.get("opposite", [])) for w in words.values())
us = sum(1 for w in words.values() for r in w.get("sense_unverified", []) if r["kind"] == "similar")
uo = sum(1 for w in words.values() for r in w.get("sense_unverified", []) if r["kind"] == "opposite")
d["header"]["counts"]["verified_similar_rows"] = vs
d["header"]["counts"]["verified_opposite_rows"] = vo
d["header"]["counts"]["unverified_similar_rows"] = us
d["header"]["counts"]["unverified_opposite_rows"] = uo
d["header"]["counts"]["words_with_verified_similar"] = sum(1 for w in words.values() if w.get("similar"))
d["header"]["counts"]["words_with_verified_opposite"] = sum(1 for w in words.values() if w.get("opposite"))
d["header"]["counts"]["words_with_unverified_relations"] = sum(1 for w in words.values() if w.get("sense_unverified"))
d["header"]["counts"]["words_with_only_unverified_relations"] = sum(
    1 for w in words.values()
    if w.get("sense_unverified") and not w.get("similar") and not w.get("opposite"))
d["header"]["manual_review"] = {
    "reviewed": "all 271 strict-rule verified rows, by hand against the seed's English glosses (verified_review.txt)",
    "demoted_to_unverified": moved,
    "reason_summary": "The automated strict rule (sense-agnostic + single-sense headword + POS match) cannot catch the case where the DUMP relation belongs to a Wiktionary sense our seed entry does not carry. The target's own seed gloss exposes it: e.g. 구매->사기 is real only for 사기='buying', but our 사기='morale'. 83 rows demoted for target-sense mismatch (most), outright wrong pair, loose association, or not-an-opposite. 3 of the 30 spot-check sampled rows were among the demoted ones (구매->사기, 긍정->부정, 일본->왜) -- the spot-check confirms dump presence, NOT sense fitness.",
    "seed_gloss_bugs_found": ["악 glossed as 'what energy that one musters up' (should be 'evil') -- affects 죄->악; fix the 악 seed gloss before using that relation.",
                               "공원 glossed as 'a person who works at a factory' (should be 'park') -- affects 뜰->공원."],
}
with open(OUT, "w", encoding="utf-8") as f:
    json.dump(d, f, ensure_ascii=False, indent=1)
print(f"moved {moved} rows; verified now similar={vs} opposite={vo}; unverified similar={us} opposite={uo}")
print("out bytes:", os.path.getsize(OUT))
