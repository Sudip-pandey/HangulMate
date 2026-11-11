# HangulMate — ML Assets

Last verified: 2026-11-11 (during asset preparation).

## 1. Image-classification model

| Field | Value |
|---|---|
| Model | MobileNetV1 1.0, 224×224, quantized (uint8) |
| File | `app/src/main/assets/ml/mobilenet_v1_1.0_224_quant.tflite` |
| Provenance | https://storage.googleapis.com/download.tensorflow.org/models/mobilenet_v1_2018_08_02/mobilenet_v1_1.0_224_quant.tgz (official TensorFlow model zoo; the archive also contains the checkpoint, frozen graph and eval files — only the `.tflite` was extracted and bundled) |
| SHA-256 | `ecc3a67c47c5a609ec35f6a58a7d97532834e43df4cb7d3f1204a8164b7d20dd` |
| Size | 4,276,352 bytes (~4.08 MiB) |
| Input | 1 × 224 × 224 × 3, uint8 — tensor name `input` |
| Output | 1 × 1001, uint8 (softmax scores over the 1001 ImageNet classes, index 0 = background) — tensor name `MobilenetV1/Predictions/Reshape_1` |
| Quantization | Full uint8 post-training quantization (weights + activations) |
| Licence | Apache 2.0 (TensorFlow model zoo) |

### Verification performed

1. Downloaded the `.tgz` with curl (3 retries), SHA-256 of the archive:
   `d32432d28673a936b2d6281ab0600c71cf7226dfe4cdcef3012555f691744166`.
2. Listed the archive — it contains `mobilenet_v1_1.0_224_quant.tflite` plus the
   checkpoint (`.ckpt.*`), frozen graph (`.pb`) and eval files, as expected for
   this official release.
3. The extracted `.tflite` is 4,276,352 bytes and bytes 4–8 read `TFL3`
   (the TFLite flatbuffer magic), SHA-256 as recorded above.
4. `tflite_runtime` could **not** be installed in this environment (no wheel
   available for the Python 3.12 VM), so instead the model flatbuffer was parsed
   directly with a small hand-written parser (using the `flatbuffers` package
   for nothing more than byte reads): the single subgraph's tensors were
   enumerated and the input tensor `input` was confirmed as shape
   `[1, 224, 224, 3]` UINT8 and the output tensor
   `MobilenetV1/Predictions/Reshape_1` as `[1, 1001]` UINT8, with 31 operators
   and 89 tensors in total. The model was **not** executed end-to-end here;
   final on-device inference must still be confirmed by the Android build on a
   real device.

## 2. Labels

| Field | Value |
|---|---|
| File | `app/src/main/assets/ml/labels.txt` |
| Provenance | https://storage.googleapis.com/download.tensorflow.org/data/ImageNetLabels.txt (official TensorFlow data bucket) |
| SHA-256 | `536feacc519de3d418de26b2effb4d75694a8c4c0063e36499a46fa8061e2da9` |
| Count | 1001 lines (index 0 = `background`, indices 1–1000 = the 1000 ImageNet classes) |
| Format | One plain label per line, no commas; lowercase except for proper names (e.g. `Granny Smith`, `Persian cat`) |

Note: the originally requested labels URL
(`raw.githubusercontent.com/tensorflow/examples/master/lite/examples/image_classification/android/app/src/main/assets/labels.txt`)
returns **404** — the tensorflow/examples repo was restructured and that path no
longer exists. The file above is the equivalent official ImageNet label list
(the same 1001 labels, same order, same first line `background`), so it is a
drop-in replacement. Two other candidates were also 404 and were not used:
`tensorflow/lite/examples/label_image/testdata/labels.txt` and the
`image_classification/metadata/labels.txt` path in tensorflow/examples.

## 3. Label → Korean alias map

File: `app/src/main/assets/ml/label_aliases.json`

- Format: `{"aliases": [{"label": "tabby", "korean": "고양이"}, ...], "notes": "..."}`
- Alias count: **269** mappings.
- Every `label` was checked programmatically against the bundled `labels.txt`
  (case-insensitive) — all 269 exist. Every `korean` value is from the app's
  core vocabulary list, so a matched word always exists in the vocabulary
  dataset.
- Many labels map to one Korean word (e.g. 118 dog breeds → 개, 45 bird
  species → 새, 15 vehicle types → 자동차, 13 boats/ships → 배).
- Coverage is deliberately partial: only labels the author is confident about
  are included. Wild animals with no core-vocabulary equivalent (tiger, lion,
  wolf, elephant…), most foods, most household objects and people occupations
  are **not** mapped. A few deliberate judgement calls are documented here:
  - `police van` → 경찰 and `fire engine` → 소방관 (the vehicle stands for the
    profession it belongs to), `stethoscope` → 의사 (the doctor's tool).
  - Computer parts (`mouse`, `computer keyboard`, `modem`, `monitor`,
    `screen`) → 컴퓨터.
  - `Granny Smith` → 사과 (the only apple class), `espresso` / `coffee mug` /
    `espresso maker` → 커피, `water bottle` → 물, `milk can` → 우유.
  - `groom` → 남자; `ballplayer`, `scuba diver` → 사람 (gender-neutral).
  - `streetcar` → 기차 (rail vehicle), `tricycle` → 자전거.

### How matching works (for the code owner)

1. Run inference, take the top-1 label index, look up the label string in
   `labels.txt`.
2. Lowercase the label and look it up in `label_aliases.json`.
3. On a hit, show the Korean word and link it to the matching vocabulary entry.
4. On a miss, see the fallback below.

### Honest fallback — what happens when no alias matches

The large majority of the 1001 classes have no alias. In that case the app
**must** show an honest "could not identify" state (e.g. "이 물체를 인식하지 못했어요 —
try another object, or look it up in the dictionary") and must **not** guess,
approximate, or invent a Korean word for the object. No hardcoded or random
recognition results are ever used; the only vocabulary word shown is one
reached through a real model prediction plus a real alias entry.

## 4. OCR note (Scan Text / Translate)

Korean text recognition is **not** part of the bundled assets above. It uses
ML Kit's on-device Korean text-recognition model, which is a **separate
Play-services model downloaded on first use** (needs internet once, then works
offline). It is **not** bundled in the APK and must not be described as
bundled. If the download has not happened yet, the Scan Text feature must say
so honestly instead of pretending to scan.

## 5. If the download had failed

The model download **succeeded**, so this section is a record, not a live
gap. Had it failed after 3 attempts per URL, the procedure was: place **no**
model file, write this document explaining the exact failure (URL, HTTP
status / curl error), and report the failure plainly to the coordinator so the
Scan Object feature is disabled or deferred rather than shipped with a fake or
placeholder model. That rule still applies to any future asset refresh: a
missing model must never be replaced by a fabricated file.
