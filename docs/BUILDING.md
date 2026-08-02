# Building and testing

## Prerequisites

- 64-bit Windows, Linux, or macOS development host
- JDK 17
- Android SDK Platform 35 and Build Tools 35
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

## Architecture support

The current Gradle configuration builds only `arm64-v8a`. Supporting another
ABI requires a matching ONNX Runtime native library and testing all native
dependencies for that ABI.

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
