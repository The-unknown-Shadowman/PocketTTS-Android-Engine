# Changelog

## 0.5.6-rc1 — Uber discovery compatibility candidate

- Add a real plain-text share destination and a read-aloud screen with review, selected pack/voice, explicit playback, and stop controls in all five UI languages.
- Make the engine discoverable through the `SEND` / `text/plain` query used by Uber Driver 4.602.10001, addressing the reporter-confirmed package filtering in #28.
- Add a separate-UID discovery regression client without a TTS-service query, covering default-engine synthesis and the share workflow.
- Keep existing model packs, ARM64 ABI, Android minimum, and release certificate. This is a prerelease; signed-in Uber confirmation remains pending.


## [0.5.5](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/tag/v0.5.5) — 2026-10-25

- Fix Settings voice-data checks by returning ISO3 language/country IDs and matching per-voice variants.
- Normalize ISO2/ISO3 language and country requests, report matching country/variant availability, and provide localized sample text through `GET_SAMPLE_TEXT`.
- Apply Android speech-rate and pitch requests independently through streaming Sonic processing, keeping the output sample rate at 24 kHz. Neutral requests retain the original audio path.
- Add host and on-device DSP measurements, repeated metadata checks, speech-rate regression, and a standalone Settings probe that closes and reopens the actual system page without terminating the engine process.
- Validate the signed APK on NX809J / Android 16 and a Google Play Android 14 emulator. Settings controls remain enabled on both opens and sample playback succeeds.
- Keep existing model packs and signing certificate. Uber-specific engine routing remains tracked in #28.

## [0.5.4](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/tag/v0.5.4) — 2026-10-22

- Fix Android system TTS output: synthesize and deliver audio synchronously
  inside `onSynthesizeText`, on Android's existing synthesis thread. Removing
  the extra executor keeps the callback valid until `done` or `error`.
- Add a device regression runner through Android's public `TextToSpeech`
  client, covering German and English non-silent WAV output and German playback.
- Compile the regression runner in CI and document how to run it against an
  installed release without removing user models or settings.
- Verify the signed update on NX809J / Android 16. The same client test fails
  on 0.5.3 and passes on 0.5.4. See [validation details](docs/VALIDATION.md).
- Keep the existing signing certificate and all seven previously released
  model packs. Existing model imports remain usable.

## [0.5.3](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/releases/tag/v0.5.3) — 2026-10-10

- Add a language-pack download button and setup guidance in all five UI languages.
- Document ready-to-use release packs and the manual ZIP import workflow.
- Update the Android build toolchain, AndroidX dependencies, and CI setup actions.

Users experiencing silent Android TTS output on this version should update to
0.5.4. Build checks and the native self-test alone did not detect the service
callback lifetime failure.
