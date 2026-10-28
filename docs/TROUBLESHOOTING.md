# Compatibility diagnostics

The target is an ARM64 Android device. The technical minimum is Android 8.0 /
API 26; the specific developer and community results are recorded in
[Validation](VALIDATION.md). Waydroid is experimental and not officially
supported. Dedicated Waydroid/x86 development and performance optimization
for weaker devices are outside the [current support scope](../README.md#compatibility-and-support).

## Identify the environment

Use Android SDK Platform Tools. For Waydroid, first connect using the
[official ADB instructions](https://docs.waydro.id/faq/using-adb-with-waydroid).
List connected devices, then replace the placeholder below with the serial
for the device you intend to test. Keep `-s` on every command when more than
one device is connected.

```sh
adb devices -l
TTS_DEVICE="REPLACE_WITH_YOUR_DEVICE_SERIAL"
adb -s "$TTS_DEVICE" shell getprop ro.build.version.release
adb -s "$TTS_DEVICE" shell getprop ro.build.version.sdk
adb -s "$TTS_DEVICE" shell getprop ro.product.cpu.abilist
adb -s "$TTS_DEVICE" shell getprop ro.product.cpu.abilist64
adb -s "$TTS_DEVICE" shell getprop ro.dalvik.vm.native.bridge
```

Report the app version, model-pack name, system language/region, and these
property values. An empty native-bridge value is normal on a native ARM64
device. On x86 Waydroid, an advertised ARM64 ABI alone does not prove the
translation layer can execute every native dependency in the APK.

## Capture one short test

First check whether Android's system TTS sample works. For an app-specific
failure, report whether the same voice works in system Settings. Then run
this filtered capture and trigger the failing sample once. A native self-test
is optional; its developer-only entry point is explained below.

```sh
adb -s "$TTS_DEVICE" logcat -T 1 -v threadtime \
  PocketTTS:I TextToSpeech:I AndroidRuntime:E linker:E libc:F '*:S'
```

Stop the capture with Ctrl+C. It follows new messages after a short initial
tail; it does not clear device logs. Use a non-sensitive test sentence and
share only the test window, removing personal or unrelated entries. Filtering
is described in the [Android logcat documentation](https://developer.android.com/tools/logcat).
If the device does not support an option, check `adb -s "$TTS_DEVICE" logcat --help`.

`SYNTH_START` shows a synthesis request reached Pocket TTS. `ENGINE_READY`,
`SYNTH_AUDIO`, and `SYNTH_DONE` show model initialization and audio progress.
An absent `SYNTH_START` alone does not explain whether a client selected a
different engine or failed earlier. Include any native-linker, Java exception,
or fatal native error from the same test window.

For Waydroid, these details allow a limited check for a general app defect.
They do not commit the project to maintaining a dedicated Waydroid port.

## Optional native self-test (ADB only)

Version 0.5.5 does not expose a self-test button in its normal app screen.
The native diagnostic is a developer activity action, separate from the
**Play** button in Android TTS Settings.

Close Android TTS Settings and select an installed pack and voice in Pocket
TTS. With `TTS_DEVICE` set as above, launch the diagnostic:

```sh
adb -s "$TTS_DEVICE" shell am start -W \
  --activity-new-task --activity-clear-task \
  -n org.pockettts.android.engine/.MainActivity \
  -a org.pockettts.android.engine.SELF_TEST
```

The activity shows **Pocket TTS self-test is running…**, followed by a success
message with a sample count or a failure message if the operation returns.
It generates a private `selftest.wav`; it does not automatically play audio.
Its log markers are `SELF_TEST_CHUNK`, `SELF_TEST_OK`, and `SELF_TEST_FAILED`.
If the screen keeps showing the running message, report the elapsed time
rather than assuming success, failure, or an audio-output problem. Reopen
Pocket TTS normally afterwards to return to the configuration screen.

## Uber Driver voice test

1. For issue #28, install the signed 0.5.6-rc1 candidate over the official
   APK and confirm a pack/voice for the navigation language is installed.
   Version 0.5.5 is affected by the confirmed package-discovery problem.
2. Select Pocket TTS as Android's preferred engine and verify the system sample.
3. Fully restart Uber Driver so it can recreate any cached TTS client.
4. With an existing driver account, open **Sounds and Voice** and press its
   voice-test button. The reporter says an active trip or going online is not
   needed for this test.
5. Report the Uber version, system language/region, navigation language, and
   whether the sample uses Pocket TTS. Existing language packs do not need
   to be downloaded again. No further full logs are needed for the already
   confirmed discovery failure.

No driver credentials, customer information, or trip details are needed.
[Issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28)
remains open until the actual Uber workflow is verified. Generic Android TTS
success and static APK inspection do not establish Uber compatibility.
