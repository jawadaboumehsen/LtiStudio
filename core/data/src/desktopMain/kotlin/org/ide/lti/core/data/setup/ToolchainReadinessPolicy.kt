package org.ide.lti.core.data.setup

/**
 * Authoritative common policy for required tools, versions, execution verification probes,
 * and publication evidence across EnvironmentProbe, EnvironmentReadinessAdapter, and ToolPublicationAdapter.
 *
 * Enforces:
 * - "Readiness uses a common authoritative required-tool/version/publication policy;
 *    installation, execution verification and publication are distinct measured facts.
 *    Null daemon health and stale results fail closed." (FR-002)
 * - "Empty tool inventory is not healthy."
 */
public object ToolchainReadinessPolicy {
    /** Minimum supported Java runtime major version on host/daemon. */
    public const val MIN_JAVA_VERSION: Int = 17

    /**
     * The 10 core native toolchain binaries required for Android dynamic partition and ROM operations.
     */
    public val REQUIRED_PRODUCT_TOOLS: Set<String> = setOf(
        "payload-dumper-go",
        "lpunpack",
        "lpmake",
        "simg2img",
        "erofsfuse",
        "mkfs.erofs",
        "avbtool",
        "unpack_bootimg",
        "img2sdat",
        "signapk",
    )

    /**
     * Host environment utilities and runtime dependencies required for tool execution and packaging.
     */
    public val REQUIRED_HOST_PACKAGES: Set<String> = setOf(
        "curl", "unzip", "zip", "sha256sum", "sha1sum", "rsync", "getfattr", "fusermount3", "brotli", "file",
        "xxd", "truncate", "find", "stat", "cat", "test", "cp", "mv", "rm", "mkdir", "java"
    )

    /**
     * Required capability check for mkfs.erofs: must support --fs-config-file.
     */
    public const val EROFS_REQUIRED_FLAG: String = "--fs-config-file"

    /**
     * Validates that Java version string meets [MIN_JAVA_VERSION].
     */
    public fun isJavaVersionSupported(versionStr: String?): Boolean {
        val trimmed = versionStr?.trim() ?: return false
        val match = Regex("""^(\d+)""").find(trimmed)
            ?: Regex("""^1\.(\d+)""").find(trimmed)
        val major = match?.groupValues?.get(1)?.toIntOrNull()
        return major != null && major >= MIN_JAVA_VERSION
    }
}
