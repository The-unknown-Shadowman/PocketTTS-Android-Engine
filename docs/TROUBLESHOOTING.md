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

First check whether the in-app self-test and Android system TTS sample work.
For an app-specific failure, state which of those checks succeeds. Then run
this filtered capture and trigger the failing sample once:

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

## Uber Driver voice test

1. Confirm Pocket TTS 0.5.5 or newer and a pack/voice for the navigation
   language are installed.
2. Select Pocket TTS as Android's preferred engine and verify the system sample.
3. Fully restart Uber Driver so it can recreate any cached TTS client.
4. With an existing driver account, open **Sounds and Voice** and press its
   voice-test button. The reporter says an active trip or going online is not
   needed for this test.
5. Report the Uber version, system language/region, navigation language, and
   whether the sample uses Pocket TTS. If it still fails, capture that single
   test using the filter above.

No driver credentials, customer information, or trip details are needed.
[Issue #28](https://github.com/The-unknown-Shadowman/PocketTTS-Android-Engine/issues/28)
remains open until the actual Uber workflow is verified. Generic Android TTS
success and static APK inspection do not establish Uber compatibility.
