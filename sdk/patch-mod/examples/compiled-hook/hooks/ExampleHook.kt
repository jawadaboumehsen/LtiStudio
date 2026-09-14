package org.ide.lti.example.hook

// Note: This compiled smali is the CompiledClassMerge payload.
// It is compiled against Android framework stubs by author tooling.
object ExampleHook {
    @JvmStatic
    fun onSignatureCheck(packageName: String?, signatureHash: String?) {
        android.util.Log.d("ExampleHook", "Checking signature: " + packageName + " " + signatureHash)
    }
}
