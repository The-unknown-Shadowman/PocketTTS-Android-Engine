package org.pockettts.android.discoveryprobe;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.accessibility.AccessibilityNodeInfo;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Separate UID, no engine interaction before discovery, and no TTS visibility declaration. */
public final class DiscoveryInstrumentation extends Instrumentation {
    private static final String ENGINE = "org.pockettts.android.engine";
    private TextToSpeech tts;
    private Bundle arguments;
    private UiAutomation automation;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            Context context = getTargetContext();
            String expected = arguments.getString("expected-engine", ENGINE);
            require(ENGINE.equals(Settings.Secure.getString(context.getContentResolver(), "tts_default_synth")),
                    "Select Pocket as the system preferred engine first");
            // Must precede any share launch: an interaction could itself grant visibility.
            List<ResolveInfo> engines = context.getPackageManager().queryIntentServices(
                    new Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), PackageManager.MATCH_DEFAULT_ONLY);
            List<String> names = engines.stream().map(r -> r.serviceInfo.packageName).collect(Collectors.toList());
            result.putString("visible_engines", names.toString());
            require(names.contains(ENGINE) == ENGINE.equals(expected), "Unexpected Pocket visibility: " + names);
            CountDownLatch initialized = new CountDownLatch(1);
            int[] status = {TextToSpeech.ERROR};
            runOnMainSync(() -> tts = new TextToSpeech(context, s -> { status[0] = s; initialized.countDown(); }));
            require(initialized.await(30, TimeUnit.SECONDS) && status[0] == TextToSpeech.SUCCESS, "TTS init failed");
            result.putString("effective_default_engine", tts.getDefaultEngine());
            require(expected.equals(tts.getDefaultEngine()), "Unexpected effective default");
            if (ENGINE.equals(expected) && !"discovery".equals(arguments.getString("mode"))) {
                require(tts.getVoice() != null && tts.getVoice().getName().contains("::"), "Missing Pocket voice");
                verifySynthesis();
                verifyShareUi();
            }
            result.putString("result", "PASS: discovery and requested checks");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error);
        } finally {
            if (tts != null) runOnMainSync(() -> tts.shutdown());
            finish(code, result);
        }
    }

    private void verifySynthesis() throws Exception {
        CountDownLatch completed = new CountDownLatch(1);
        boolean[] success = {false};
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override public void onStart(String id) { }
            @Override public void onDone(String id) { success[0] = true; completed.countDown(); }
            @Override public void onError(String id) { completed.countDown(); }
        });
        File wav = new File(getTargetContext().getCacheDir(), "discovery.wav");
        try {
            require(tts.synthesizeToFile("Hello from Pocket.", new Bundle(), wav, "discovery") == TextToSpeech.SUCCESS,
                    "Default-engine synthesis rejected");
            require(completed.await(120, TimeUnit.SECONDS) && success[0], "Default-engine synthesis failed");
            try (RandomAccessFile audio = new RandomAccessFile(wav, "r")) {
                require(audio.length() > 44 && audio.readInt() == 0x52494646, "Missing WAV output");
                audio.seek(44);
                int nonzero = 0;
                while (audio.getFilePointer() + 1 < audio.length()) if (audio.readShort() != 0) nonzero++;
                require(nonzero > 100, "Silent output");
            }
        } finally { wav.delete(); }
    }

    private void verifyShareUi() throws Exception {
        automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        AccessibilityServiceInfo info = automation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
        automation.setServiceInfo(info);
        share("Hello from Pocket.");
        waitFor("read_aloud_play", true, null, 30000);
        require(!node("read_aloud_stop").isEnabled(), "Share triggered automatic speech");
        require("Hello from Pocket.".contentEquals(node("read_aloud_text").getText()), "Shared text lost");
        click("read_aloud_play");
        waitFor("read_aloud_status", true, resource("read_aloud_done"), 120000);
        share("Hello from Pocket.");
        waitFor("read_aloud_play", true, null, 30000);
        click("read_aloud_play");
        waitFor("read_aloud_stop", true, null, 3000);
        click("read_aloud_stop");
        waitFor("read_aloud_status", true, resource("read_aloud_stopped"), 3000);
        share("");
        waitFor("read_aloud_play", true, null, 30000);
        click("read_aloud_play");
        waitFor("read_aloud_status", true, resource("read_aloud_text"), 3000);
        share("x".repeat(TextToSpeech.getMaxSpeechInputLength() + 1));
        waitFor("read_aloud_status", true, resource("read_aloud_too_long", TextToSpeech.getMaxSpeechInputLength()), 30000);
        require(node("read_aloud_text").getText() == null || node("read_aloud_text").getText().length() == 0
                || resource("read_aloud_text").contentEquals(node("read_aloud_text").getText()), "Oversize text not rejected");
        automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
    }

    private void share(String text) {
        getTargetContext().startActivity(new Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(ENGINE)
                .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private String resource(String name, Object... args) throws Exception {
        Resources res = getTargetContext().getPackageManager().getResourcesForApplication(ENGINE);
        return res.getString(res.getIdentifier(name, "string", ENGINE), args);
    }

    private AccessibilityNodeInfo node(String id) {
        AccessibilityNodeInfo root = automation.getRootInActiveWindow();
        if (root == null) return null;
        List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByViewId(ENGINE + ":id/" + id);
        return nodes.isEmpty() ? null : nodes.get(0);
    }

    private void waitFor(String id, boolean enabled, String text, long timeout) {
        long deadline = SystemClock.uptimeMillis() + timeout;
        while (SystemClock.uptimeMillis() < deadline) {
            AccessibilityNodeInfo view = node(id);
            if (view != null && view.isEnabled() == enabled && (text == null || text.contentEquals(view.getText()))) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("UI timeout: " + id + " expected=" + text);
    }

    private void click(String id) {
        AccessibilityNodeInfo view = node(id);
        require(view != null && view.performAction(AccessibilityNodeInfo.ACTION_CLICK), "Cannot click " + id);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
