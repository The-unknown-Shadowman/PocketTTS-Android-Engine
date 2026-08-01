# Model packs

> **Security:** ONNX files are complex native-runtime inputs. Import model packs
> only from sources you trust. The Android importer rejects path traversal,
> excessive entry counts, individual entries larger than 2 GiB, and archives
> expanding beyond 3 GiB, but these checks cannot make an untrusted model safe.

Model weights are deliberately separate from the APK. The app imports a ZIP
with the following structure:

```text
manifest.json
MODEL_LICENSE.txt                  optional but recommended for redistribution
VOICE_ATTRIBUTION.md               required when redistributing bundled voices
models/
  mimi_encoder.onnx
  text_conditioner.onnx
  tokenizer.model
  flow_lm_main.onnx                or flow_lm_main_int8.onnx
  flow_lm_flow.onnx                or flow_lm_flow_int8.onnx
  mimi_decoder.onnx                or mimi_decoder_int8.onnx
voices/
  optional-reference.wav
```

## Manifest format

```json
{
  "format": 1,
  "id": "german-fp32",
  "name": "German (FP32)",
  "languageTag": "de-DE",
  "precision": "fp32",
  "temperature": 0.7,
  "lsdSteps": 1,
  "threads": 2,
  "voices": []
}
```

Pack IDs must be stable and unique. `languageTag` is a valid BCP-47 language
tag. `precision` is `fp32` or `int8` and determines which three model filenames
the native runtime loads.

## Export from the pinned upstream source

First review and accept the conditions on
[kyutai/pocket-tts](https://huggingface.co/kyutai/pocket-tts), then authenticate
with Hugging Face. Install Python 3.12, `uv`, and FFmpeg.

```bash
huggingface-cli login
./scripts/export_model_pack.sh german de-DE "German (FP32)" fp32
```

Other examples:

```bash
./scripts/export_model_pack.sh english en-US "English (FP32)" fp32
./scripts/export_model_pack.sh italian it-IT "Italian (FP32)" fp32
./scripts/export_model_pack.sh spanish es-ES "Spanish (FP32)" fp32
./scripts/export_model_pack.sh portuguese pt-PT "Portuguese (FP32)" fp32
./scripts/export_model_pack.sh german_24l de-DE "German 24-layer (FP32)" fp32
```

The exporter downloads the official PyTorch weights and the corresponding
official Pocket TTS default voice, exports the graph through the patched
`vendor/PocketTTS.cpp/export_onnx.py`, and packages the required files with
model and voice attribution. The process needs several gigabytes of free disk
space and may take a long time on CPU. The five default mappings are Alba
(English), Jürgen (German), Giovanni (Italian), Rafael (Portuguese), and Lola
(Spanish).

## INT8

Use `int8` as the final argument to retain the dynamically quantized MatMul
variants:

```bash
./scripts/export_model_pack.sh german de-DE "German (INT8)" int8
```

INT8 substantially reduces the three quantized model files. The encoder and
text conditioner remain FP32. Quality and device performance vary, so compare
the same prompts and voice sample before publishing an INT8 release.

## Add an authorized bundled voice while building

The safest public pack contains no recording. Users can import a consented WAV
through the Android app. If you have the right to redistribute a reference
recording, build manually with one or more `--voice` arguments:

```bash
python scripts/build_model_pack.py \
  --models-dir build/onnx/german \
  --output build/model-packs/german-custom.zip \
  --id german-custom --name "German custom" --language de-DE \
  --precision fp32 \
  --license-file MODEL_LICENSE.md \
  --voice-attribution-file VOICE_ATTRIBUTION.md \
  --voice "speaker-a=Speaker A=/absolute/path/reference.wav"
```

Every voice is converted to 24 kHz mono PCM WAV. A bundled recording may have a
different license from the model and requires separate attribution and consent.
Official release packs use only Kyutai's default catalog recordings documented
in `VOICE_ATTRIBUTION.md`; they never use locally imported recordings.

## Remove recordings from an existing pack

Maintainers can rebuild an existing pack without any bundled audio before
redistribution:

```bash
python scripts/repack_without_voices.py input.zip output.zip \
  --license-file MODEL_LICENSE.md
```

The command removes all voice/audio entries, clears the manifest's voice list,
adds the model license, and normalizes ZIP metadata. The model files are
recompressed, so allow enough time and free disk space.
