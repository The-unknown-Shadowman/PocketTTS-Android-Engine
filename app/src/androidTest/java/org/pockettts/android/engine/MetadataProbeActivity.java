package org.pockettts.android.engine;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

/** Test-only external client for the exported CHECK_TTS_DATA / GET_SAMPLE_TEXT activities. */
public final class MetadataProbeActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        request(1);
    }

    private void request(int phase) {
        String action = phase < 3 ? TextToSpeech.Engine.ACTION_CHECK_TTS_DATA
                : TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT;
        try {
            startActivityForResult(new Intent(action).setPackage("org.pockettts.android.engine")
                    .putExtra("language", "deu").putExtra("country", "DEU"), phase);
        } catch (RuntimeException error) {
            report(phase, -999, null);
            finish();
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        report(request, result, data);
        if (request < 3) request(request + 1); else finish();
    }

    private void report(int phase, int code, Intent data) {
        Intent result = new Intent("org.pockettts.android.engine.METADATA_PROBE_RESULT")
                .setPackage("org.pockettts.android.engine")
                .putExtra("nonce", getIntent().getStringExtra("nonce"))
                .putExtra("phase", phase).putExtra("code", code);
        if (data != null) result.putExtras(data);
        sendBroadcast(result);
    }
}
