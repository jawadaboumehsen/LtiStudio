package org.ide.lti.core.data.setup

import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector

/**
 * Approved, version-aware package catalog for WSL / Debian-based distributions.
 * Acts as the authoritative static security boundary for package remediation.
 */
public object PackageCatalog {
    public val APPROVED_APT_PACKAGES: Map<String, List<String>> = mapOf(
        "host_compilers" to listOf("build-essential", "cmake", "make", "clang"),
        "host_libraries" to NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES,
        "python_crypto" to listOf("python3", "python3-pip", "python3-cryptography", "python3-pyasn1"),
        "jdk_dual" to listOf("openjdk-17-jdk", "openjdk-21-jdk"),
        "selinux_attr" to listOf("attr"),
        "archive_tools" to listOf("zip", "unzip", "brotli"),
        "transfer_tools" to listOf("curl", "git", "rsync", "tar", "file", "xxd"),
        "dev_fuse" to listOf("fuse3", "libfuse3-dev"),
        "openssl_tools" to listOf("openssl"),
        "build_cache" to listOf("ccache"),
    )

    public val ALL_APPROVED_PACKAGES: Set<String> = APPROVED_APT_PACKAGES.values.flatten().toSet()

    public fun isPackageApproved(packageName: String): Boolean = packageName in ALL_APPROVED_PACKAGES
}
