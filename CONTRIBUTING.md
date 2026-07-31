# Contributing

Thanks for helping improve Pocket TTS Android Engine.

## Before opening a pull request

1. Discuss substantial behavior or model-format changes in an issue first.
2. Keep changes focused and preserve Android system TTS compatibility.
3. Build the app and test synthesis on an ARM64 Android device.
4. Do not commit model files, APKs, credentials, device logs containing private
   text, or voice recordings.
5. Confirm that every new dependency has a redistribution-compatible license
   and add it to `THIRD_PARTY_NOTICES.md`.

## Development checks

```bash
./scripts/prepare_android_native_deps.sh
./gradlew clean :app:assembleDebug :app:lintDebug
./scripts/check_publishable.sh
```

For TTS changes, test all of the following:

- Android's preferred-engine selector lists the service.
- The Android TTS test produces complete audio.
- Cancellation returns promptly and does not crash the service.
- Two voices in one model can be selected independently.
- Switching between languages resolves the requested model.
- Layla discovers and plays each advertised Android voice after restart.

## Licensing contributions

By submitting a contribution, you agree to license your contribution under the
repository's MIT license. Do not submit code, models, or recordings that you do
not have the right to redistribute.

