# MT costing — HangulMate seed v3 Nepali translation (Phase 0)

Measured 2026-11-11 from the v2 seed (`app/src/main/assets/seed/vocabulary_seed.json`).
Target set: the 9,149 words whose `nepaliStatus` is not `verified`.

## Character counts

| Payload | Characters |
|---|---|
| English gloss only (`english`) | 562,419 |
| Korean example sentence only (`exampleKo`) | 282,245 |
| English gloss + Korean example | **844,664** |
| Gloss + example + Korean headword | 868,727 |

- Average English gloss length: 61.5 chars (max 393). Average exampleKo: 30.8 chars (max 74).
- 0 of the 9,149 lack an English gloss. 32 lack a Korean example sentence (so example-context is available for 9,117).
- All 9,149 English glosses are single definitions (some are long multi-sense glosses, up to 393 chars).

## Free-tier check (research plan said ~230k chars — that estimate was too low)

- **Azure Translator F0: 2,000,000 chars/month free, permanent.** Covers the full gloss+example payload (844,664) with ~1.15M chars to spare for re-runs. ✓
- **Google Cloud Translation: 500,000 chars/month free.** Does NOT cover the full gloss+example payload in one month (844,664 > 500,000). Even English gloss alone (562,419) exceeds it. ✗ for a full two-engine pass in one month.

## Consequence for the Phase 3 two-engine plan

The "translate everything with both Azure and Google, keep where they agree" plan cannot run in full on free tiers in one month. Options, cheapest first:

1. **Azure full batch + Google cross-check on a priority subset** (e.g. EPS: High words first, then disagreements-prone categories). Google 500k covers ~59% of the payload, so most of it can still be cross-checked in month 1, the rest in month 2.
2. **Split Google across two months** (500k + 345k) — full two-engine agreement, but takes ~2 months for the cross-check half.
3. **Azure only for the bulk + Google for the disagreement-prone slice** — identical to option 1 in practice.
4. NLLB-200-distilled-600M locally as a free third vote (note: CC-BY-NC / research-only licence — check before treating its output as a permanent asset).

Whichever option is chosen, every MT-filled entry keeps `nepaliStatus: "mt_review"` with a `nepaliSource` provenance field, the 851 verified entries are never overwritten, and EPS: High words are reviewed first.
