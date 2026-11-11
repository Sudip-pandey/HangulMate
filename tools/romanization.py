#!/usr/bin/env python3
"""Revised Romanization (RR) of Korean, stdlib only.

Implements the morphophonemic core of RR (the system the app already
uses for its hand-written seed: hangugeo, gimchi, hakgyo):

- choseong/jungseong/jongseong are mapped with the RR tables,
- a batchim before a vowel-initial syllable is re-syllabified as the
  next syllable's onset (한국어 -> hangugeo),
- nasal assimilation before ㄴ/ㅁ (ㄱ->ng, ㄷ-family->n, ㅂ->m),
- ㅎ aspiration (ㅎ+ㄱ/ㄷ/ㅈ/ㅂ -> ㅋ/ㅌ/ㅊ/ㅍ) and ㅎ->h before a vowel,
- the ㄹ/ㄴ lateral rule (신라 -> silla),
- double batchim: before a vowel the right element moves to the onset
  (읽어 -> ilgeo); before a consonant / at the end only the right
  element is pronounced (읽다 -> ikda).

Tensing is deliberately NOT applied: RR writes the underlying
consonant (학교 -> hakgyo, not hakkyo).
"""

# choseong (initial) -> RR
CHO = [
    "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
    "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h",
]
# jungseong (vowel) -> RR
JUNG = [
    "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o", "wa", "wae",
    "oe", "yo", "u", "wo", "we", "wi", "yu", "eu", "ui", "i",
]
# jongseong (final) -> RR, when pronounced as a coda
JONG = [
    "", "k", "k", "k", "n", "n", "n", "t", "l", "k", "m", "l",
    "l", "l", "p", "l", "m", "p", "p", "t", "t", "ng", "t",
    "t", "k", "t", "p", "t",
]
# Double batchim (jong index -> (left-as-coda, right-as-onset, right-as-coda)).
DOUBLE = {
    3: ("k", "s", "k"),    # ㄳ
    5: ("n", "j", "n"),    # ㄵ
    6: ("n", "h", "n"),    # ㄶ
    9: ("l", "g", "k"),    # ㄺ
    10: ("l", "m", "m"),   # ㄻ
    11: ("l", "b", "l"),   # ㄼ
    12: ("l", "s", "l"),   # ㄽ
    13: ("l", "t", "l"),   # ㄾ
    14: ("l", "p", "p"),   # ㄿ
    15: ("l", "h", "l"),   # ㅀ
    18: ("p", "s", "p"),   # ㅄ
}
# Coda value of a single jongseong before ㄴ/ㅁ (nasal assimilation);
# anything unlisted keeps its normal coda value.
NASAL_BEFORE_NM = {
    1: "ng", 2: "ng",                       # ㄱ, ㄲ
    7: "n", 19: "n", 20: "n", 22: "n",      # ㄷ ㅅ ㅆ ㅈ
    23: "n", 25: "n", 27: "n",              # ㅊ ㅌ ㅎ
    17: "m", 18: "m",                       # ㅂ ㅄ
}
# ㅎ + onset -> aspirated onset value (ㅎ itself disappears).
H_ASPIRATE = {
    0: "k",    # ㄱ -> ㅋ
    3: "t",    # ㄷ -> ㅌ
    12: "ch",  # ㅈ -> ㅊ
    9: "ss",   # ㅅ -> ㅆ
    7: "p",    # ㅂ -> ㅍ
}
# jongseong index -> the choseong index whose sound it takes as an onset.
JONG_AS_ONSET = {
    1: 0, 2: 1, 3: 9, 4: 2, 5: 12, 6: 18, 7: 3, 8: 5, 9: 0, 10: 6,
    11: 7, 12: 9, 13: 16, 14: 17, 15: 18, 16: 6, 17: 7, 18: 9,
    19: 9, 20: 10, 21: 11, 22: 12, 23: 14, 24: 15, 25: 16, 26: 17,
    27: 18,
}


def _decompose(ch):
    code = ord(ch)
    if 0xAC00 <= code <= 0xD7A3:
        code -= 0xAC00
        return (code // 588, (code % 588) // 28, code % 28)
    return None


def romanize(text):
    """Romanize a Korean string (non-Hangul passes through unchanged)."""
    # Per-syllable working state: dicts so a syllable can rewrite the
    # next syllable's onset (ㅎ aspiration, ㄹ/ㄴ rules).
    items = []
    for ch in text:
        d = _decompose(ch)
        if d is None:
            items.append({"passthrough": ch})
        else:
            cho, jung, jong = d
            items.append({
                "onset": CHO[cho], "vowel": JUNG[jung],
                "jong": jong, "cho": cho,
            })
    n = len(items)
    for i, it in enumerate(items):
        if "passthrough" in it or it["jong"] == 0:
            continue
        jong = it["jong"]
        nxt = items[i + 1] if i + 1 < n else None
        ncho = nxt.get("cho") if nxt and "cho" in nxt else None
        if nxt is None or "passthrough" in nxt:
            # end of word / before non-Hangul: plain coda value
            it["coda"] = DOUBLE[jong][2] if jong in DOUBLE else JONG[jong]
            continue
        if ncho == 11:  # next syllable vowel-initial (ㅇ): re-syllabify
            if jong == 21:  # ㅇ batchim stays ng (병원 -> byeongwon)
                it["coda"] = "ng"
            elif jong in DOUBLE:
                if DOUBLE[jong][1] == "h":
                    # ㅎ as the right element is silent; the left element
                    # behaves like a single batchim (많아요 -> manayo,
                    # 싫어요 -> sireoyo).
                    it["coda"] = ""
                    nxt["onset"] = "n" if jong == 6 else "r"
                else:
                    it["coda"] = DOUBLE[jong][0]
                    nxt["onset"] = DOUBLE[jong][1]
            elif jong == 27:  # ㅎ before a vowel is silent (좋아요 -> joayo)
                it["coda"] = ""
            else:
                it["coda"] = ""
                nxt["onset"] = CHO[JONG_AS_ONSET[jong]]
        elif ncho in (2, 6):  # before ㄴ/ㅁ: nasal assimilation
            if jong == 8:  # ㄹ before ㄴ/ㅁ: ㄹ stays l, next ㄴ -> l
                it["coda"] = "l"
                if ncho == 2:
                    nxt["onset"] = "l"
            elif jong == 4 and ncho == 5:
                it["coda"] = "l"  # unreachable here (ㄹ is not ㄴ/ㅁ)
            else:
                if jong in DOUBLE:
                    it["coda"] = DOUBLE[jong][2]
                else:
                    it["coda"] = NASAL_BEFORE_NM.get(jong, JONG[jong])
        elif ncho == 5:  # before ㄹ
            if jong in (4, 8):  # ㄴ/ㄹ before ㄹ -> ll (신라 -> silla, 멀리 -> meolli)
                it["coda"] = "l"
                nxt["onset"] = "l"
            elif jong == 21:  # ㅇ before ㄹ -> ng + n (동료 -> dongnyo)
                it["coda"] = "ng"
                nxt["onset"] = "n"
            elif jong == 16:  # ㅁ before ㄹ -> m + n (침략 -> chimnyak)
                it["coda"] = "m"
                nxt["onset"] = "n"
            elif jong in NASAL_BEFORE_NM:  # ㄱ/ㅂ/ㄷ-family -> nasal + n (법률 -> beomnyul)
                it["coda"] = (DOUBLE[jong][2] if jong in DOUBLE
                              else NASAL_BEFORE_NM[jong])
                nxt["onset"] = "n"
            else:
                it["coda"] = DOUBLE[jong][2] if jong in DOUBLE else JONG[jong]
        elif jong == 27:  # ㅎ before a consonant: aspirate it, ㅎ drops
            asp = H_ASPIRATE.get(ncho)
            if asp is not None:
                it["coda"] = ""
                nxt["onset"] = asp
            else:
                it["coda"] = JONG[jong]
        else:
            it["coda"] = DOUBLE[jong][2] if jong in DOUBLE else JONG[jong]
    out = []
    for it in items:
        if "passthrough" in it:
            out.append(it["passthrough"])
        else:
            out.append(it["onset"] + it["vowel"] + it.get("coda", ""))
    return "".join(out)


SELFTEST = {
    "한국어": "hangugeo",
    "김치": "gimchi",
    "학교": "hakgyo",
    "안녕하세요": "annyeonghaseyo",
    "감사합니다": "gamsahamnida",
    "먹다": "meokda",
    "좋아요": "joayo",
    "많아요": "manayo",
    "싫어요": "sireoyo",
    "좋다": "jota",
    "한국말": "hangungmal",
    "신라": "silla",
    "밥": "bap",
    "집": "jip",
    "물": "mul",
    "사랑": "sarang",
    "공부": "gongbu",
    "읽다": "ikda",
    "읽어": "ilgeo",
    "한글": "hangeul",
    "맛있다": "masitda",
    "없다": "eopda",
    "있어요": "isseoyo",
}
