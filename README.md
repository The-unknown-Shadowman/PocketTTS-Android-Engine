# Pocket TTS Android Engine

An unofficial, fully on-device Android text-to-speech engine for
[Kyutai Pocket TTS](https://github.com/kyutai-labs/pocket-tts). It wraps the
[PocketTTS.cpp](https://github.com/VolgaGerm/PocketTTS.cpp) ONNX runtime in JNI
and exposes it through Android's `TextToSpeechService`, so applications such as
[Layla](https://www.layla-network.ai/) can use Pocket TTS as a native system
voice.

![Pocket TTS Android Engine artwork](app/src/main/res/drawable-nodpi/pocket_voice_hero.png)

> Status: experimental community software for ARM64 Android devices.
> See [compatibility and support](#compatibility-and-support) for requirements
> and the environments covered by testing.

## Current release: 0.5.5

[Download the signed APK](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/download/v0.5.5/PocketTTS-Android-Engine-0.5.5.apk)
· [All downloads and checksums](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/tag/v0.5.5)
· [Changelog](CHANGELOG.md)

Version 0.5.5 fixes disabled Android TTS settings controls after reopening the
settings page and adds independent speech-rate and pitch processing. It returns
Android's ISO3 language/country identifiers, handles the sample-text activity,
and normalizes both ISO2 and ISO3 language requests. Install it over the previous
official APK to keep imported models and settings; the signing certificate is
unchanged.

On October 25, 2026, the signed APK passed public-client metadata, German and
English file synthesis, independent speed/pitch measurements, and Android
playback on an NX809J / Android 16 and an Android 14 emulator. Separate UI tests
confirmed enabled sliders and sample playback controls after closing and
reopening Settings on both. See [validation details](docs/VALIDATION.md) and the
[device tests](docs/BUILDING.md#android-tts-integration-regression-test).

[Issue #26](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/26)
tracks the original settings report. Uber Driver routing is a separate
[issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28)
and has not yet been verified inside Uber Driver.

## Uber compatibility candidate: 0.5.6-rc1

[Download the signed test APK](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/tag/v0.5.6-rc1).
The stable release remains 0.5.5. This candidate adds **Read text aloud** and
an Android **Share → Pocket TTS** destination for plain text. Text is shown
for review; speech starts only when you press **Read aloud**, using the pack
and voice selected in Pocket TTS. **Stop** or leaving the screen stops reading.
Share a selection within Android's 4,000-character TTS input limit.

The share destination also makes Pocket discoverable to clients such as
Uber Driver 4.602.10001 whose package queries include plain-text sharing but
omit TTS-service discovery. This addresses the package filtering confirmed in
[issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28).
It does not override a client that explicitly selects Google. A signed-in Uber
voice-test confirmation is still required before claiming full compatibility.
Existing imported model packs remain usable; install over the official APK.

## Compatibility and support

The APK requires **Android 8.0 / API 26 or newer** and **ARM64 (`arm64-v8a`)**.
The minimum Android version is a technical installation requirement; it does
not mean every Android version and device has been tested.

Developer tests cover an Android 16 ARM64 phone and an Android 14 emulator
with ARM64 translation. Community testing additionally confirms working
system TTS controls, speech, rate, and pitch on Android 14 and 15 phones and
tablets. See the [validation report](docs/VALIDATION.md) for the exact scope.
Android 8 through 13 on physical devices has not been validated by these tests.

Waydroid is experimental and **not officially supported**. Its Android 13
environment has a report of no speech output. On an x86 host, this ARM64 APK
also depends on a working ARM translation layer; the Android version alone
does not establish the cause. A limited diagnostic review can identify a
general app defect, but dedicated Waydroid support and an x86 port are outside
the current project scope. This report does not change the Android minimum.

Dedicated performance optimization for weaker devices is not a development
goal. Synthesis latency and playback smoothness depend on the hardware and
model. General crashes and functional defects can still be investigated.
Uber Driver compatibility remains an active investigation in
[issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28).

For a reproducible compatibility report, use the focused
[diagnostic steps](docs/TROUBLESHOOTING.md).

## Features

- Offline, on-device synthesis after installation and model import
- Android system TTS integration
- Independent Android speech-rate and pitch controls
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

Official APKs published by this repository use the following
release-certificate SHA-256 fingerprint:
`5B:5C:DF:D1:5C:B0:4C:3D:77:7A:DD:21:E2:91:5F:55:AE:2B:7E:70:49:3D:8F:ED:07:9F:1A:C6:96:09:5B:78`.
Locally built debug APKs intentionally have a different certificate.

Recommended starting values from an Android ARM64 benchmark (108 syntheses,
78 additionally checked with speech recognition):

| Model pack | Temperature | LSD steps | Threads | Sentence pause | Segment size |
| --- | ---: | ---: | ---: | ---: | ---: |
| German FP32 | 0.5 | 1 | 4 | 250 ms | 50 tokens |
| German 24L FP32 | 0.3 | 1 | 3 | 250 ms | 50 tokens |

These are defaults for newly imported release packs, not hard limits. Existing
per-pack settings are preserved when the app is upgraded.

The 24-layer packs are experimental. They require considerably more memory and
can show variable latency on Android even when the same text and settings are
used repeatedly. Test them on the target device before relying on them in an
accessibility or interactive workflow.

## Install and use

The APK does **not** include model weights. Ready-to-use language packs are
provided as separate ZIP downloads in the same release; you do **not** need to
export or build a model yourself.

1. Install the APK from the [latest release](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest).
2. In the app, tap **Download language packs**, expand **Assets** on the release
   page, and download a model ZIP (not the source-code ZIP).
3. Open **Pocket TTS Android Engine** and import the model pack.
4. Select the official bundled voice, or import a WAV reference that you have
   permission to use. A clear 5-15 second mono sample usually works well.
5. Save the selected voice and generation parameters.
6. Open Android **Settings > Text-to-speech output > Preferred engine** and
   select **Pocket TTS Android Engine**.
7. Restart the client application if it caches Android voices.

Ready-to-use packs are included in the current release. These model files
are unchanged from v0.5.2; existing installations can keep their imported packs:

| Language | Release asset |
| --- | --- |
| English | [PocketTTS-english-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-english-FP32.zip) |
| German | [PocketTTS-german-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-german-FP32.zip) |
| Italian | [PocketTTS-italian-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-italian-FP32.zip) |
| Portuguese | [PocketTTS-portuguese-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-portuguese-FP32.zip) |
| Spanish | [PocketTTS-spanish-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-spanish-FP32.zip) |

German experimental variants are also available as
[PocketTTS-german-24L-FP32.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-german-24L-FP32.zip) and
[PocketTTS-german-24L-INT8.zip](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest/download/PocketTTS-german-24L-INT8.zip).
Start with the regular FP32 pack; see the memory and latency notes above for 24L.
For older app versions without the download button, open the
[release downloads](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/latest)
in a browser, download a model ZIP, and import it in the app.

Only import model packs from trusted sources. ONNX graphs are processed by a
native inference runtime; archive validation and size limits reduce accidental
damage but cannot make a malicious model safe.

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
- Android SDK Platform 37.0 and Build Tools 36.0.0
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
device-testing and release-signing instructions.

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

## Acknowledgements

Development of this project was assisted by OpenAI Codex. The project owner
reviewed the implementation, performed on-device testing, and is responsible
for the published releases. This project is not affiliated with or endorsed by
OpenAI.

## Licensing

Original code in this repository is released under the [MIT License](LICENSE).
Third-party code retains its original license; see
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). Pocket TTS model weights and
converted ONNX derivatives are licensed separately; see
[MODEL_LICENSE.md](MODEL_LICENSE.md).

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).
