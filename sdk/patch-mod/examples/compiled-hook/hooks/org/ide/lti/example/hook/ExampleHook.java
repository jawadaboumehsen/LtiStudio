package org.ide.lti.example.hook;

import android.util.Log;

/** The CompiledClassMerge payload: compiled against the firmware payload.json names by `compileModPayload`. */
public final class ExampleHook {
    private ExampleHook() {
    }

    public static void onSignatureCheck(String packageName, String signatureHash) {
        Log.d("ExampleHook", "Checking signature: " + packageName + " " + signatureHash);
    }
}
