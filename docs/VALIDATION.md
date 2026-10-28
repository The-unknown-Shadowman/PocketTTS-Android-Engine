# Validation and compatibility checks

## Release 0.5.5

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

## Community confirmation and support scope

On October 26, 2026, the reporter of
[issue #26 confirmed 0.5.5](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/26#issuecomment-6405685427)
on an Android 14 phone, an Android 15 tablet, and an Oppo Android 15 phone.
Closing and reopening system TTS Settings, speech-rate and pitch controls,
and Tecarta Bible 8.0.2 controls were reported working. These are community
results, separate from the developer-run tests above. Slow synthesis and
glitches were also reported on the weaker devices; dedicated weak-device
performance optimization is outside the project's development goals.

The same report describes no speech output in Waydroid with Android 13
LineageOS, despite enabled system controls. This is an unresolved environment
compatibility report, not evidence of a general Android 13 defect. Waydroid
is experimental and not officially supported. A limited log review can check
for a general app defect; a dedicated Waydroid or x86 port is outside the
current scope. There has been no developer reproduction of that environment.

The technical minimum remains Android 8.0 / API 26, with an ARM64-only APK.
Physical Android 8 through 13 devices have not been validated by these tests.
See [support scope](../README.md#compatibility-and-support) and
[diagnostic steps](TROUBLESHOOTING.md).

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

The reporter identified a **Sounds and Voice** test button that can reproduce
the selected navigation voice without going online. On October 26, a focused
[0.5.5 retest was requested](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28#issuecomment-6411380460),
including system language/region and navigation language.

Static inspection of the same APK found a navigation engine-selection branch
controlled by `enable_google_text_to_speech_as_default_engine`. If enabled and
Google TTS is available, that branch explicitly requests Google TTS; otherwise
it uses Android's default engine. The compiled flag default is false. This does
not establish the reporter's effective configuration or prove the cause of
issue #28. The inspected navigation language check accepts language-only
availability, so the country-support difference with another TTS engine does
not by itself justify a compatibility patch. The signed-in voice test remains
unverified by the developer.

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

## 0.5.6-rc1: Uber discovery candidate, October 28, 2026

Signed ARM64 release candidate, versionCode 26, with the existing official
certificate and previously imported English/German FP32 packs retained after
an in-place update. Stable release remains 0.5.5. Tested on the Google Play
Android 14 x86_64 emulator with ARM64 translation; no new physical-device
or signed-in Uber result is claimed.

The reporter supplied an `AppsFilter` trace confirming Uber-to-Pocket package
filtering, followed by Google TTS binding:
[issue #28 evidence](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28#issuecomment-6455963203).
The candidate exposes a genuine `SEND` / `text/plain` read-aloud destination,
matching a discovery query already present in Uber Driver 4.602.10001.

| Test | 0.5.5 | 0.5.6-rc1 |
| --- | --- | --- |
| Separate UID with complete queries from Uber APK | Only Google visible; effective engine Google | Google and Pocket visible; effective engine Pocket, `english-fp32::alba` |
| Fresh-installed client with only SEND/text/plain query | Only Google visible despite Pocket system preference | Pocket visible and used by default |
| Non-silent WAV from that default client | Not requested | PASS, 36,720 samples through Pocket |
| Share text, no automatic speech, then explicit Read aloud | Not available | PASS; playback completion received |
| Stop button | Not available | PASS; reading stops |
| Empty text and oversized shared text | Not available | PASS; no speech request, oversized text rejected |
| JVM tests, host DSP tests | Previous validation above | PASS: 11 JVM tests and both host DSP/allocation tests |
| Debug/release build and lint; separate probe build/lint | Previous validation above | PASS; lint has no errors |

Visibility was tested before the client opened Pocket; direct interaction can
itself grant package visibility. The complete-query client and the narrow-query
client run under different UIDs from the engine. The narrow client was freshly
installed for the candidate run. No `--force-queryable`, global visibility
setting, or modification of Uber was used. The system preferred engine remained
Pocket throughout the before/after comparison.

The candidate does not override explicit Google selection or an account-side
Uber policy. The reporter still needs to restart Uber with Pocket preferred
and test **Sounds and Voice**. Issue #28 remains open until that result arrives.
The old model packs are unchanged and need no new download.

Candidate APK SHA-256: `0c563477bfaa8b8eb71e6b98a78885f72843c65447a7c5546974d94365d06b79`.
