# Using the engine with Layla

This project is independent from Layla. It uses Android's standard TTS service
API, which Layla can discover as a native voice provider.

1. Install the APK.
2. Import at least one model pack and one authorized WAV reference.
3. In Android settings, choose **Pocket TTS Android Engine** as the preferred
   text-to-speech engine.
4. Fully close and restart Layla so it refreshes Android voices.
5. Edit a character, open its advanced voice settings, and select a voice from
   the **Native** section.

Each advertised voice has a stable name in the form `pack-id::voice-id`. The
engine also assigns a stable BCP-47 locale variant because some Android clients
preserve the locale but discard the explicit voice name.

If Layla remains on **Loading** or plays the wrong voice:

- Verify that the matching language pack and voice appear in this app.
- Save the selection after changing model parameters.
- Restart Layla after importing or removing voices.
- Confirm Android still lists this app as the preferred TTS engine.
- Capture `adb logcat -s PocketTTS AndroidRuntime` while playing a preview and
  check the `SYNTH_START` line for the requested pack and voice.

Layla's general Android multilingual TTS instructions are available in its
[official guide](https://blog.layla-network.ai/post/how-to-add-multilingual-text-to-speech-for-your-characters-in-layla).

