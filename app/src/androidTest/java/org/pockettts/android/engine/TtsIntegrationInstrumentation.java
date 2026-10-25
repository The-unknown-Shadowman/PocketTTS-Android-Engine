package org.pockettts.android.engine;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;
import java.util.UUID;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.util.Log;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Device regression test through Android's public TTS client, not the native self-test. */
public final class TtsIntegrationInstrumentation extends Instrumentation {
    private static final String TAG = "PocketTtsIntegration";
    private TextToSpeech tts;
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            CountDownLatch initialized = new CountDownLatch(1);
            int[] status = {TextToSpeech.ERROR};
            runOnMainSync(() -> tts = new TextToSpeech(getTargetContext(), value -> {
                status[0] = value;
                initialized.countDown();
            }, "org.pockettts.android.engine"));
            require(initialized.await(30, TimeUnit.SECONDS), "TTS initialization timed out");
            require(status[0] == TextToSpeech.SUCCESS, "TTS initialization failed");
            String mode = arguments.getString("mode", "full");
            verifyMetadata();
            require(tts.isLanguageAvailable(Locale.GERMANY) >= TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "German country support missing");
            require(tts.isLanguageAvailable(Locale.US) >= TextToSpeech.LANG_COUNTRY_AVAILABLE,
                    "English country support missing");
            if ("metadata".equals(mode)) {
                result.putString("result", "PASS: repeated Android metadata and sample text checks");
                code = Activity.RESULT_OK;
                return;
            }
            SpeechEffectsDeviceTest.run();
            tts.setSpeechRate(1f);
            tts.setPitch(1f);
            long normalBytes = synthesize("german-fp32", "Dies ist ein Test der Android-Sprachausgabe.", "german", false);
            if (!"true".equals(arguments.getString("baseline"))) {
                synthesize("english-fp32", "This is a test of Android speech output.", "english", false);
                tts.setSpeechRate(2f);
                long fastBytes = synthesize("german-fp32", "Dies ist ein Test der Android-Sprachausgabe.", "german-fast", false);
                require(fastBytes < normalBytes * 0.8, "Speech-rate request did not shorten output");
                tts.setSpeechRate(1f);
                tts.setPitch(1.5f);
                synthesize("german-fp32", "Die Tonhöhe wird von Android gesteuert.", "german-pitch", false);
                tts.setPitch(1f);
                synthesize("german-fp32", "Pocket TTS funktioniert jetzt auch als Android-Sprachausgabe.", "playback", true);
            }
            result.putString("result", "PASS: Android metadata, independent rate/pitch, file synthesis and playback");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            Log.e(TAG, "INTEGRATION_FAILED", error);
            result.putString("result", "FAIL: " + error);
        } finally {
            if (tts != null) runOnMainSync(() -> tts.shutdown());
            finish(code, result);
        }
    }

    private long synthesize(String pack, String text, String id, boolean playback) throws Exception {
        Voice voice = tts.getVoices().stream()
                .filter(value -> value.getName().startsWith(pack + "::"))
                .findFirst().orElseThrow(() -> new AssertionError("Required model pack: " + pack));
        require(tts.setVoice(voice) == TextToSpeech.SUCCESS, "Could not select " + pack);
        CountDownLatch finished = new CountDownLatch(1);
        int[] errorCode = {0};
        boolean[] started = {false};
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String utteranceId) { started[0] = true; }
            @Override public void onDone(String utteranceId) { finished.countDown(); }
            @Override public void onError(String utteranceId) { onError(utteranceId, TextToSpeech.ERROR); }
            @Override public void onError(String utteranceId, int error) {
                errorCode[0] = error;
                finished.countDown();
            }
            @Override public void onStop(String utteranceId, boolean interrupted) {
                errorCode[0] = TextToSpeech.STOPPED;
                finished.countDown();
            }
        });
        File output = new File(getTargetContext().getCacheDir(), "tts-integration-" + id + ".wav");
        try {
            int queued = playback ? tts.speak(text, TextToSpeech.QUEUE_FLUSH, new Bundle(), id)
                    : tts.synthesizeToFile(text, new Bundle(), output, id);
            require(queued == TextToSpeech.SUCCESS, "Request rejected: " + id);
            require(finished.await(120, TimeUnit.SECONDS), "Synthesis timed out: " + id);
            require(errorCode[0] == 0, "Android TTS callback error " + errorCode[0] + ": " + id);
            require(started[0], "Missing onStart: " + id);
            if (!playback) verifyWave(output);
            Log.i(TAG, "INTEGRATION_OK mode=" + id + " bytes=" + (playback ? 0 : output.length()));
            return playback ? 0 : output.length();
        } finally {
            if (output.exists() && !output.delete()) Log.w(TAG, "Could not delete test WAV");
        }
    }

    private void verifyMetadata() throws Exception {
        String nonce = UUID.randomUUID().toString();
        CountDownLatch replies = new CountDownLatch(3);
        Intent[] results = new Intent[3];
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (!nonce.equals(intent.getStringExtra("nonce"))) return;
                int index = intent.getIntExtra("phase", 0) - 1;
                if (index >= 0 && index < 3 && results[index] == null) {
                    results[index] = intent;
                    replies.countDown();
                }
            }
        };
        Context context = getTargetContext();
        IntentFilter filter = new IntentFilter("org.pockettts.android.engine.METADATA_PROBE_RESULT");
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED);
        try {
            context.startActivity(new Intent().setClassName("org.pockettts.android.engine.test",
                    "org.pockettts.android.engine.MetadataProbeActivity")
                    .putExtra("nonce", nonce).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            require(replies.await(30, TimeUnit.SECONDS), "Metadata activity timed out");
            for (int i = 0; i < 2; i++) {
                require(results[i].getIntExtra("code", -999) == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS,
                        "Voice-data check failed");
                ArrayList<String> ids = results[i].getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
                require(ids != null && ids.contains("deu-DEU") && ids.contains("eng-USA"),
                        "ISO3 voice metadata missing: " + ids);
                for (Voice voice : tts.getVoices()) {
                    Locale locale = voice.getLocale();
                    String id = locale.getISO3Language() + "-" + locale.getISO3Country();
                    if (!locale.getVariant().isEmpty()) id += "-" + locale.getVariant();
                    require(ids.contains(id), "Advertised voice absent from legacy check: " + id);
                }
            }
            require(results[2].getIntExtra("code", -999) == TextToSpeech.LANG_AVAILABLE,
                    "Sample-text action unavailable");
            String sample = results[2].getStringExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT);
            require(sample != null && !sample.trim().isEmpty(), "Empty sample text");
            Log.i(TAG, "METADATA_OK repeated-checks=2 sample-text=available");
        } finally {
            context.unregisterReceiver(receiver);
        }
    }

    private static void verifyWave(File file) throws Exception {
        require(file.length() > 44, "No PCM samples in output");
        try (RandomAccessFile wav = new RandomAccessFile(file, "r")) {
            require(wav.readInt() == 0x52494646, "Missing RIFF header");
            wav.seek(8);
            require(wav.readInt() == 0x57415645, "Missing WAVE header");
            wav.seek(44);
            long nonzero = 0;
            while (wav.getFilePointer() < wav.length()) if (wav.readShort() != 0) nonzero++;
            require(nonzero > 100, "Output contains only silence");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
