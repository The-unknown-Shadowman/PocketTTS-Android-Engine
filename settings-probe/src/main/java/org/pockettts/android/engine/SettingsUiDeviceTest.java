package org.pockettts.android.engine;

import android.accessibilityservice.AccessibilityService;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Opens the actual system TTS settings twice and checks the enabled controls. */
final class SettingsUiDeviceTest {
    static void run(Instrumentation instrumentation) throws Exception {
        UiAutomation automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        // Discard an already-open Settings fragment whose engine binding may have been
        // disconnected by an APK update or by the in-process synthesis test teardown.
        instrumentation.getTargetContext().startActivity(new Intent("com.android.settings.TTS_SETTINGS")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long initialDeadline = SystemClock.uptimeMillis() + 10000;
        while (SystemClock.uptimeMillis() < initialDeadline) {
            List<AccessibilityNodeInfo> initialSliders = new ArrayList<>();
            List<AccessibilityNodeInfo> initialButtons = new ArrayList<>();
            inspect(automation.getRootInActiveWindow(), initialSliders, initialButtons, new StringBuilder());
            if (initialSliders.size() >= 2 && !initialButtons.isEmpty()) break;
            SystemClock.sleep(250);
        }
        automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
        SystemClock.sleep(750);
        for (int round = 1; round <= 2; round++) {
            instrumentation.getTargetContext().startActivity(new Intent("com.android.settings.TTS_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            long deadline = SystemClock.uptimeMillis() + 20000;
            List<AccessibilityNodeInfo> sliders = new ArrayList<>();
            List<AccessibilityNodeInfo> buttons = new ArrayList<>();
            StringBuilder description = new StringBuilder();
            boolean enabled = false;
            while (SystemClock.uptimeMillis() < deadline) {
                sliders.clear(); buttons.clear(); description.setLength(0);
                inspect(automation.getRootInActiveWindow(), sliders, buttons, description);
                enabled = sliders.size() >= 2 && sliders.stream().allMatch(AccessibilityNodeInfo::isEnabled)
                        && buttons.stream().anyMatch(AccessibilityNodeInfo::isEnabled);
                if (enabled) break;
                SystemClock.sleep(250);
            }
            if (!enabled) throw new AssertionError("TTS settings controls disabled on open " + round + ": " + description);
            Log.i("PocketTtsIntegration", "SETTINGS_OK open=" + round + " sliders=" + sliders.size() + " play=enabled");
            if (round == 1) {
                automation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK);
                SystemClock.sleep(500);
            } else {
                AccessibilityNodeInfo button = buttons.stream().filter(AccessibilityNodeInfo::isEnabled).findFirst().get();
                if (!button.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    throw new AssertionError("System sample button click failed");
                Log.i("PocketTtsIntegration", "SETTINGS_SAMPLE_CLICKED");
            }
        }
    }

    private static void inspect(AccessibilityNodeInfo node, List<AccessibilityNodeInfo> sliders,
            List<AccessibilityNodeInfo> buttons, StringBuilder description) {
        if (node == null) return;
        String type = String.valueOf(node.getClassName());
        String text = String.valueOf(node.getText());
        if (type.endsWith("SeekBar")) sliders.add(node);
        if (type.endsWith("Button") && text.toLowerCase(Locale.ROOT)
                .matches(".*(abspielen|anhören|wiederg|vorlesen|play|listen|sample|beispiel).*")) buttons.add(node);
        if (type.endsWith("SeekBar")) description.append("slider enabled=").append(node.isEnabled()).append("; ");
        if (!"null".equals(text)) description.append(text).append(" enabled=").append(node.isEnabled()).append("; ");
        for (int i = 0; i < node.getChildCount(); i++) inspect(node.getChild(i), sliders, buttons, description);
    }
}
