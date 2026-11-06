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

## Download ready-to-use packs

Open the [latest release](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest)
and expand **Assets**. Download `PocketTTS-<language>-FP32.zip` for English,
German, Italian, Portuguese, or Spanish, then use **Import language pack (.zip)**
in the app. The APK and model ZIP are separate downloads. The source-code ZIP
is not a model pack. Model export below is optional for contributors and custom
packs.

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

The exporter requires the voice-cloning weights for **both** FP32 and INT8
packs, because Android voices are reference WAV files. If access to those
weights fails, the upstream loader can fall back to weights without voice
cloning. The exporter rejects that fallback before writing model files; it
cannot produce a working WAV encoder from those weights. Accept the upstream
model conditions, run `hf auth login`, and retry the export.

English INT8 is experimental. Issue [#35](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/35)
reports occasional metallic speech even after replacing the encoder with the
one from the released English FP32 pack. This is not yet a validated release
configuration. To investigate, include the repository commit, Python/PyTorch/
ONNX/ONNX Runtime versions, export validation output, the SHA-256 of each
pack's encoder, and a short failing text with the voice and generation settings.
Compare against FP32 with the same reference WAV. Replacing an encoder is a
diagnostic workaround, not proof that quantization caused the encoder problem:
the standard INT8 export already leaves it in FP32.

### Isolate English INT8 artifacts

The default `int8` command still quantizes all three generation graphs. For
controlled comparisons, an optional fifth argument selects which graphs the
pack actually uses in INT8:

| Profile | Main LM | Flow network | Audio decoder |
| --- | --- | --- | --- |
| `all` (default) | INT8 | INT8 | INT8 |
| `main-only` | INT8 | FP32 | FP32 |
| `main-flow` | INT8 | INT8 | FP32 |
| `main-decoder` | INT8 | FP32 | INT8 |

Start with a smaller quantization scope:

```bash
./scripts/export_model_pack.sh english en-US "English (INT8 main only)" int8 main-only
```

This produces `pockettts-english-int8-main-only.zip`, with pack ID
`english-int8-main-only`. The other non-default profiles get their own suffix,
so the packs can coexist with `english-int8` for comparisons. Select Alba and
keep all generation settings equal. For issue #35, use temperature 0.3, LSD
steps 1, two threads, 250 ms sentence pause, 50 text tokens and:
"The quick brown fox jumps over the very lazy dog." Repeat each condition,
because the reported artifact is intermittent.

All graphs are still exported and quantized, but packaging selects the graph
bytes specified above. Unquantized auxiliary graphs retain the `_int8.onnx`
filenames required by the existing Android runtime. The manifest records the
selection as `int8Profile`; this informational field does not require an APK
change. `main-only` packs are larger than `all` packs.

To reuse an existing complete export without downloading or converting the
weights again, add `--int8-profile main-only` (or another profile) to the
manual `scripts/build_model_pack.py` command below, together with
`--precision int8` and a distinct `--id` and `--output`. Include the same
authorized voice recording in every comparison pack.

The upstream PyTorch implementation also leaves the flow network and Mimi
decoder in FP32; its quantization backend differs from this ONNX path. These
profiles are diagnostic candidates, not a confirmed fix or an official English
INT8 release. Numeric export checks and successful word recognition do not
establish artifact-free audio. Device listening confirmation is still needed.

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
