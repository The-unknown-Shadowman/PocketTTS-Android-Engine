# Release 0.5.4 validation

Test date: October 22, 2026. Device: NX809J, Android 16, connected using
wireless ADB. APK: official signed 0.5.4, versionCode 24. The installed APK,
locally tested APK, and downloaded GitHub release APK have identical SHA-256
hashes. The signing certificate matches earlier official releases.

## Android client regression

The test runner uses `TextToSpeech` to bind to the installed engine. It checks
completion callbacks and parses generated WAV output for non-silent PCM.

| Test | Result |
| --- | --- |
| Same client against 0.5.3: first German file request | Failed: timeout; service logged `SYNTH_START_REJECTED` |
| 0.5.4: German file synthesis | Passed; 131,084-byte WAV with non-silent PCM |
| 0.5.4: English file synthesis | Passed; 123,404-byte WAV with non-silent PCM |
| 0.5.4: German Android speaker playback | Passed; Android reported start and completion |
| Full runner | PASS; instrumentation result code -1 |

The temporary test package was removed afterwards. The production app remains
installed. No private voice recordings or generated audio are published.

## Build and release checks

- Seven unit tests passed.
- Debug and Release Android Lint passed.
- Release APK signature verification passed with the existing official key.
- GitHub Android CI and all CodeQL language jobs passed for release source
  commit `cc3463d722f60e3ab833240c95dc23b941e5aaf4`.
- All nine release assets were verified against their GitHub SHA-256 digests.

Reproduction instructions are in
[Building and testing](BUILDING.md#android-tts-integration-regression-test).

## Reported compatibility work

[Issue #26](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/26)
reports disabled speed/pitch/sample controls in Android settings and engine
routing in specific clients on Android 14 and 15. The Android 16 client tests
above do not establish that those separate reports are resolved; the issue
remains open for reproduction and follow-up.
