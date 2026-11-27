package io.ltirom.tooling.core

import java.util.Locale

enum class OS {
    LINUX, WINDOWS, MACOS, UNKNOWN;

    fun isUnix(): Boolean = this == LINUX || this == MACOS
}

enum class Arch {
    X86_64, AARCH64, UNKNOWN
}

data class PlatformIdentity(
    val os: OS,
    val arch: Arch
) {
    fun key(): String = "${os.name.lowercase(Locale.US)}-${arch.name.lowercase(Locale.US)}"

    companion object {
        val CURRENT: PlatformIdentity by lazy {
            val osName = System.getProperty("os.name").lowercase(Locale.US)
            val os = when {
                osName.contains("linux") -> OS.LINUX
                osName.contains("windows") -> OS.WINDOWS
                osName.contains("mac") || osName.contains("darwin") -> OS.MACOS
                else -> OS.UNKNOWN
            }

            val archName = System.getProperty("os.arch").lowercase(Locale.US)
            val arch = when {
                archName.contains("x86_64") || archName.contains("amd64") -> Arch.X86_64
                archName.contains("aarch64") || archName.contains("arm64") -> Arch.AARCH64
                else -> Arch.UNKNOWN
            }

            PlatformIdentity(os, arch)
        }
    }
}
