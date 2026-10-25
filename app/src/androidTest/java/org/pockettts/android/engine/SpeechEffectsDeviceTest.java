package org.pockettts.android.engine;

import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Measures pitch and duration separately on a deterministic signal through the actual JNI. */
final class SpeechEffectsDeviceTest {
    static void run() throws Exception {
        verify(100, 100, 24000, 200);
        verify(200, 100, 12000, 200);
        verify(50, 100, 48000, 200);
        verify(100, 200, 24000, 400);
        verify(100, 50, 24000, 100);
        verify(150, 150, 16000, 300);
    }

    private static void verify(int rate, int pitch, int expectedSamples, int expectedFrequency) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NativeSpeechEffects effects = new NativeSpeechEffects(24000, rate, pitch)) {
            for (int offset = 0; offset < 24000; offset += 613) {
                float[] input = new float[Math.min(613, 24000 - offset)];
                for (int i = 0; i < input.length; i++)
                    input[i] = (float) (0.4 * Math.sin(2 * Math.PI * 200 * (offset + i) / 24000));
                append(bytes, effects.process(input));
            }
            append(bytes, effects.finish());
        }
        ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        float[] samples = new float[data.remaining() / 4];
        for (int i = 0; i < samples.length; i++) samples[i] = data.getFloat();
        if (Math.abs(samples.length - expectedSamples) > 800)
            throw new AssertionError("Wrong duration: rate=" + rate + " pitch=" + pitch + " samples=" + samples.length);
        int crossings = 0, from = 2000, to = samples.length - 2000;
        for (int i = from + 1; i < to; i++) if (samples[i - 1] <= 0 && samples[i] > 0) crossings++;
        double frequency = crossings * 24000.0 / (to - from);
        if (Math.abs(frequency - expectedFrequency) > 20)
            throw new AssertionError("Wrong pitch: " + frequency + " expected=" + expectedFrequency);
        Log.i("PocketTtsIntegration", "EFFECT_OK rate=" + rate + " pitch=" + pitch
                + " samples=" + samples.length + " frequency=" + frequency);
    }

    private static void append(ByteArrayOutputStream output, float[] samples) throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(samples.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float sample : samples) buffer.putFloat(sample);
        output.write(buffer.array());
    }
}
