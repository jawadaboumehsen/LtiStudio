package io.ltirom.tooling.codegen.spec

enum class ToolFamily(val directoryName: String) {
    ANDROID_DEVICE("android-device"),
    ANDROID_PACKAGE("android-package"),
    IMAGE("image"),
    SECURITY("security"),
    PUBLISHING("publishing");

    public companion object {
        fun forTool(toolName: String): ToolFamily = when (toolName.lowercase()) {
            "adb", "fastboot" -> ANDROID_DEVICE
            "aapt2", "apktool" -> ANDROID_PACKAGE
            "avbtool", "signapk", "zipalign" -> SECURITY
            "gh" -> PUBLISHING
            else -> IMAGE
        }
    }
}
