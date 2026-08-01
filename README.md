# Pocket TTS Android Engine

An unofficial, fully on-device Android text-to-speech engine for
[Kyutai Pocket TTS](https://github.com/kyutai-labs/pocket-tts). It wraps the
[PocketTTS.cpp](https://github.com/VolgaGerm/PocketTTS.cpp) ONNX runtime in JNI
and exposes it through Android's `TextToSpeechService`, so applications such as
[Layla](https://www.layla-network.ai/) can use Pocket TTS as a native system
voice.

![Pocket TTS Android Engine artwork](app/src/main/res/drawable-nodpi/pocket_voice_hero.png)

> Status: experimental community software. The current build targets ARM64
> Android devices and has primarily been tested on Android 16.

## Features

- Offline, on-device synthesis after installation and model import
- Android system TTS integration
- Streaming PCM audio through the Android TTS callback
- Importable FP32 or INT8 ONNX language packs
- Multiple language packs and multiple voices per language
- Voice cloning from a WAV reference selected in the app
- Per-model temperature, LSD steps, and CPU-thread settings
- Configurable silence after sentence boundaries (250 ms by default)
- Configurable maximum text tokens per generated segment (50 by default)
- Token-based generation length guard matching the upstream Python runtime
- Safe removal of installed language packs after confirmation
- Automatic model selection by requested language, with English fallback
- Stable per-voice identifiers for TTS clients such as Layla
- In-app project information and a direct link to this repository

The Android application ID is `org.pockettts.android.engine`.

Recommended starting values from an Android ARM64 benchmark (108 syntheses,
78 additionally checked with speech recognition):

| Model pack | Temperature | LSD steps | Threads | Sentence pause | Segment size |
| --- | ---: | ---: | ---: | ---: | ---: |
| German FP32 | 0.5 | 1 | 4 | 250 ms | 50 tokens |
| German 24L FP32 | 0.3 | 1 | 3 | 250 ms | 50 tokens |

These are defaults for newly imported release packs, not hard limits. Existing
per-pack settings are preserved when the app is upgraded.

> **Upgrade note for pre-0.4.0 test builds:** version 0.4.0 changed the
> application ID from `ai.layla.pockettts`. Android therefore installs it as a
> separate application. Import model packs and reference voices again, select
> the new engine in Android's text-to-speech settings, and remove the old test
> app only after confirming the new installation works.

## Install and use

1. Install the APK from the repository's Releases page.
2. Download or build a compatible model-pack ZIP.
3. Open **Pocket TTS Android Engine** and import the model pack.
4. Select the official bundled voice, or import a WAV reference that you have
   permission to use. A clear 5-15 second mono sample usually works well.
5. Save the selected voice and generation parameters.
6. Open Android **Settings > Text-to-speech output > Preferred engine** and
   select **Pocket TTS Android Engine**.
7. Restart the client application if it caches Android voices.

For Layla-specific details, see [docs/LAYLA.md](docs/LAYLA.md).

## Voice-cloning sample quality

The reference recording is part of the synthesis input, not merely a speaker
identifier. Pocket TTS explicitly notes that characteristics and defects in the
sample are reproduced, so prompt quality can have a larger audible effect than
small changes to the generation parameters. Use a clean, dry recording with
natural speech and consistent volume; avoid background noise, music, echo,
reverberation, clipping, aggressive noise reduction, and long leading or
trailing silence. Testing several 5-15 second excerpts is worthwhile.

In one local German comparison, a clean excerpt from the
[Thorsten-Voice](https://github.com/thorstenMueller/Thorsten-Voice) project
produced fewer artifacts than the bundled Jürgen reference. This is an
observation about the tested prompt recordings, not a universal ranking of the
speakers or models. Thorsten-Voice also provides German models for
[Piper TTS](https://huggingface.co/Thorsten-Voice/Piper); Piper itself is not
used by this Android engine. See the
[Pocket TTS voice-cloning guidance](https://github.com/kyutai-labs/pocket-tts#trying-it-with-the-cli)
and always verify the source and license of any recording before importing or
redistributing it.

## Build the Android app

The repository intentionally does not commit generated ONNX Runtime binaries,
model weights, reference voices, APKs, or local SDK paths.

Requirements:

- JDK 17
- Android SDK 35
- Android NDK `27.2.12479018`
- CMake 3.22.1
- Git, curl, unzip, and tar
- An ARM64 Android device for runtime testing

```bash
git clone <repository-url>
cd PocketTTS-Android-Engine
./scripts/prepare_android_native_deps.sh
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.
See [docs/BUILDING.md](docs/BUILDING.md) for Android Studio, command-line, and
device-testing instructions.

## Build a model pack

Model conversion requires Python 3.12, `uv`, FFmpeg, and access to Kyutai's
gated model repository. Review and accept the upstream model conditions before
running the exporter.

```bash
huggingface-cli login
./scripts/export_model_pack.sh german de-DE "German (FP32)" fp32
```

The resulting ZIP is written under `build/model-packs/`. Release packs may
bundle the corresponding official Pocket TTS default voice under CC BY 4.0;
see [VOICE_ATTRIBUTION.md](VOICE_ATTRIBUTION.md). Custom builds may omit voices
or include only recordings the distributor is authorized to redistribute.

Supported upstream configurations include `english`, `german`, `italian`,
`spanish`, `portuguese`, and the larger `*_24l` variants exposed by the pinned
Pocket TTS source snapshot. Larger models generally improve quality but consume
more storage, memory, and generation time.

See [docs/MODEL_PACKS.md](docs/MODEL_PACKS.md) for the pack format, manual
packaging, INT8 notes, and adding voices.

## Repository layout

```text
app/                         Android UI, TTS service, JNI bridge, and resources
scripts/                     Native dependency and model-pack build tools
vendor/PocketTTS.cpp/        Patched C++ runtime and ONNX exporter
vendor/pocket-tts/           Pinned upstream Python source snapshot
docs/                        Build, model, and Layla integration guides
MODEL_LICENSE.md             Model attribution and use conditions
VOICE_ATTRIBUTION.md         Official bundled-voice sources and licenses
THIRD_PARTY_NOTICES.md       Dependency provenance and licenses
```

## Privacy and responsible use

Inference is local. Imported model packs and voice samples are stored in the
app's private Android data directory. Do not use a person's voice without their
explicit and lawful consent, and do not use synthesized speech to deceive or
harm people. Review [MODEL_LICENSE.md](MODEL_LICENSE.md) before distributing
converted weights.

## Licensing

Original code in this repository is released under the [MIT License](LICENSE).
Third-party code retains its original license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Pocket TTS model weights and
converted ONNX derivatives are licensed separately; see
[MODEL_LICENSE.md](MODEL_LICENSE.md).

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).
