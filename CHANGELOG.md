# Changelog

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
