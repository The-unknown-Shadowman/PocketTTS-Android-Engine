package org.pockettts.android.engine;

import android.app.Activity;
import android.app.Instrumentation;
import android.os.Bundle;

/** Run outside the engine's process so finishing the test leaves the Settings service binding alive. */
public final class SettingsOnlyInstrumentation extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        int code = Activity.RESULT_CANCELED;
        try {
            SettingsUiDeviceTest.run(this);
            result.putString("result", "PASS: actual settings controls enabled on both opens; sample clicked");
            code = Activity.RESULT_OK;
        } catch (Throwable error) {
            result.putString("result", "FAIL: " + error);
        }
        finish(code, result);
    }
}
