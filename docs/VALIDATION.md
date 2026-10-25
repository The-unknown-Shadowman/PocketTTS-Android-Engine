# Release 0.5.5 validation

Test date: October 25, 2026. Signed release APK, versionCode 25, using the same
certificate as earlier releases. Test environments:

- NX809J / Android 16 over wireless ADB, with existing installed model packs.
- Google Play Android 14 emulator, using the same signed ARM64 APK through the
  image's ARM64 native-bridge support and the public German/English FP32 packs.

## Regression results

| Check | Result |
| --- | --- |
| 0.5.4 baseline metadata | FAIL: returned `[de-DE, en-US]`, missing required ISO3 IDs |
| 0.5.5 repeated metadata and sample-text activities | PASS on both environments |
| German and English non-silent WAV synthesis | PASS on both environments |
| 2x German speech-rate request | PASS; output shorter than 80% of neutral |
| Independent native speed/pitch signal measurements, six combinations | PASS on both environments |
| Android playback | Completion received on both; physical speaker tested on NX809J |
| Settings page closed and reopened, both sliders and play enabled | PASS on both environments |
| Settings sample button | Click succeeds on both; NX809J service reports `SYNTH_DONE result=0` |
| JVM tests | 11 passed: 7 import-security and 4 locale-compatibility tests |
| Host streaming DSP test | Six duration/frequency cases passed |
| Native allocation regression | Sanitized host test passed; allocation multiplication is validated before use |

The synthetic 200 Hz signal stays near 200 Hz at 2x speed, becomes about 400 Hz
at 2x pitch while retaining approximately its original duration, and passes
combined speed/pitch tests. Output is drained between 613-sample input chunks,
including the final flush. This checks streaming boundaries as well as factors.

The Settings test runs in a standalone probe process. It first closes an
existing Settings fragment because an APK update or the in-process synthesis
runner may have disconnected that fragment's old service binding. It then
verifies two fresh openings without changing the preferred engine or sliders.

The emulator's ARM64 translation makes model inference much slower than the
physical device. Its virtual audio output completed; host audio audibility was
not part of that check.

## Remaining Uber-specific work

[Issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28)
requires a test inside Uber Driver. Version 4.602.10001 (317956) was obtained
from APKMirror and installed only in the Android 14 emulator. The installed
base and split APK signatures were verified and match the certificate published
for `com.ubercab.driver` in [Uber's official Digital Asset Links](https://www.uber.com/.well-known/assetlinks.json).

The app opens to “Sign up or Log in with Uber” and requests a phone number or
another sign-in method. No account was created and no credentials were entered.
Uber's [Driver App Basics](https://www.uber.com/us/en/drive/basics/) describes an
active driver account before going online. A signed-in driver or the reporter's
on-device retest is therefore still needed to verify navigation speech. No Uber
routing success is claimed from the generic TTS tests above.

## Previous release validation

### Release 0.5.4 validation

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
routing in specific clients on Android 14 and 15. At the time of the 0.5.4 release, those separate reports remained open.
The 0.5.5 checks above cover the Settings controls; Uber-specific routing is
tracked separately in issue #28.
