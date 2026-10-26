# Building and testing

## Prerequisites

- 64-bit Windows, Linux, or macOS development host
- JDK 17
- Android SDK Platform 37.0 and Build Tools 36.0.0
- Android NDK `27.2.12479018`
- CMake 3.22.1 and Ninja
- Git, curl, unzip, and tar

Android Studio can install the SDK, NDK, CMake, and Ninja from its SDK Manager.
The command-line build also works from WSL when the SDK and NDK paths are
available to Linux.

## Prepare ONNX Runtime

The app uses ONNX Runtime Android 1.20.0. Downloaded binaries and headers are
verified with pinned SHA-256 checksums. Runtime updates are tested explicitly
because tested releases from 1.21.1 through 1.28.0 produced either an
illegal-instruction crash or severe 24-layer regressions on a Snapdragon 8
Elite device. Re-evaluate this pin as newer Android builds become available.

```bash
./scripts/prepare_android_native_deps.sh
```

This downloads the official Android AAR and matching public headers, then
extracts only the ARM64 runtime needed by the app. Generated files are ignored
by Git.

## Build from the command line

Set `ANDROID_HOME` or create an untracked `local.properties` containing the SDK
path, then run:

```bash
./gradlew :app:assembleDebug
```

To use an NDK outside the SDK installation, set `ANDROID_NDK_HOME` to the full
NDK directory. The Gradle build otherwise uses NDK `27.2.12479018` from the
Android SDK.

## Build in Android Studio

1. Open the repository root.
2. Allow Gradle sync to finish.
3. Install any requested SDK/NDK/CMake components.
4. Run `scripts/prepare_android_native_deps.sh` in a terminal.
5. Select an ARM64 device and run the `app` configuration.

## Install and inspect logs

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb logcat -c
adb logcat -s PocketTTS AndroidRuntime
```

After importing a pack and a voice, use Android's TTS settings or any Android
TTS client to synthesize a short sentence. Log entries named `VOICE_LOADED`,
`ENGINE_READY`, `SYNTH_AUDIO`, and `SYNTH_DONE` show the selection and streaming
path.

For device architecture checks and a short filtered capture for Waydroid or
Uber reports, see [Compatibility diagnostics](TROUBLESHOOTING.md).

## Architecture support

The current Gradle configuration builds only `arm64-v8a`. Supporting another
ABI requires a matching ONNX Runtime native library and testing all native
dependencies for that ABI.

The minimum Android version remains Android 8.0 / API 26. Check the
[project support scope](../README.md#compatibility-and-support) before adding
an ABI, changing the minimum version, or proposing device-specific work.

## Create a signed release build

Release credentials must stay outside the repository. Set all four variables
below; if any is missing, Gradle deliberately creates an unsigned release APK.

```bash
export POCKETTTS_KEYSTORE_PATH=/absolute/private/path/release.jks
export POCKETTTS_KEYSTORE_PASSWORD='store password'
export POCKETTTS_KEY_ALIAS='pockettts-release'
export POCKETTTS_KEY_PASSWORD='key password'
./gradlew clean :app:assembleRelease :app:lintRelease
```

Back up the keystore and its recovery secret securely. Every future update for
the same Android application ID must be signed with this exact key. Never add a
keystore, password file, or signing environment script to Git.

## Android TTS integration regression test

The native self-test does not exercise Android's `TextToSpeechService` callback
lifetime. The device regression test uses a public `TextToSpeech` client to
synthesize German and English WAV files, checks that they contain non-silent
PCM, and plays a German sentence through Android's audio output. It fails if
Android reports an error, stops the utterance, or never completes it.

Use a test device with the regular German and English FP32 packs installed:

```bash
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w org.pockettts.android.engine.test/org.pockettts.android.engine.TtsIntegrationInstrumentation
```

A successful run reports `PASS` and instrumentation result code `-1`. The
runner deletes its generated WAVs. Installing a debug APK over a release APK
requires a matching certificate; for an existing release installation, build
the signed release and sign the test APK with that same key before installing
it. Do not uninstall a user's release app to run the test: that removes their
imported models and settings.

To reproduce only the first file-synthesis request on an older APK, add
`-e baseline true` before the instrumentation component. CI builds the test
APK; running it requires a connected device with the model packs.


## Settings, language metadata, speech rate, and pitch tests

The integration runner now checks two successive `CHECK_TTS_DATA` results,
ISO3 language/country and per-voice identifiers, localized sample text, native
streaming pitch/duration measurements, German/English WAV synthesis, a 2x
speech-rate request, and playback. Run the host DSP test as well:

```bash
python3 scripts/test_speech_effects.py
./gradlew :app:testDebugUnitTest :app:assembleDebugAndroidTest :settings-probe:assembleDebug
```

The Settings probe is a separate test-only APK. It runs in its own process, so
ending instrumentation does not tear down the engine while Settings is still
bound to it. Select Pocket TTS as the device's preferred engine before running:

```bash
adb install -r settings-probe/build/outputs/apk/debug/settings-probe-debug.apk
adb shell am instrument -w org.pockettts.android.settingsprobe/org.pockettts.android.engine.SettingsOnlyInstrumentation
```

It closes any existing TTS Settings page, opens and closes it, reopens it,
requires both enabled sliders and the enabled sample button, and clicks the
sample button. Supported button labels include English and German. Run it
after the synthesis instrumentation, since the latter runs inside the engine
process and Android ends that process when instrumentation finishes.

The `settings-probe` APK is a development tool, not a release artifact. Remove
it after testing. To check just the intent metadata against an older release,
use `-e mode metadata` with `TtsIntegrationInstrumentation`.

Sonic is pinned under `vendor/sonic`, licensed under Apache-2.0. Its license
and notice are included in the production APK. Rates and pitches above zero
are normalized against Android's 100-percent default and limited to Sonic's
supported 0.05x–20x range. Non-positive inputs use 1x.
