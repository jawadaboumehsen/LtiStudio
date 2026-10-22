package org.ide.lti.example.hook;

import android.util.Log;

/** Called before services.jar collects a package's certificates (see Mod.kt). */
public final class ExampleHook {
    private ExampleHook() {
    }

    public static void onCollectCertificates(Object parsedPackage) {
        Log.d("ExampleHook", "Collecting certificates of " + parsedPackage);
    }
}
