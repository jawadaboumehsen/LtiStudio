package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.ToolCapabilities

/**
 * Declares the execution packaging kind of a toolchain binary.
 */
public enum class ToolKind {
    NATIVE,
    JAR,
}

/**
 * Authoritative descriptor for one toolchain tool managed by the application (FR-028).
 *
 * @property id Clean unique tool identifier (e.g. "signapk", "erofsfuse").
 * @property kind Execution packaging kind: [ToolKind.NATIVE] executable or [ToolKind.JAR] java archive.
 * @property recipeGroup Associated recipe group name defined in [ToolCapabilities].
 * @property requiredForProduct True if this tool is strictly required for core
 * Android ROM dynamic partition operations.
 */
public data class ToolCatalogEntry(
    val id: String,
    val kind: ToolKind,
    val recipeGroup: String,
    val requiredForProduct: Boolean = false,
) {
    /**
     * Expected binary filename on the filesystem in `$binDir`.
     */
    val binaryName: String
        get() = if (kind == ToolKind.JAR) "$id.jar" else id
}

/**
 * Authoritative single tool list for publication, readiness checking, and plan derivation (FR-028).
 *
 * Eliminates duplicated tool lists across WslToolchainProvisioner, ToolchainReadinessPolicy,
 * SetupPlanFactory, and ToolPublicationAdapter.
 */
public object ToolCatalog {

    public val entries: List<ToolCatalogEntry> = listOf(
        // android-tools recipe group
        ToolCatalogEntry("adb", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("fastboot", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("mke2fs", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("e2fsdroid", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("lpmake", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS, requiredForProduct = true),
        ToolCatalogEntry("lpunpack", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS, requiredForProduct = true),
        ToolCatalogEntry("lpdump", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("lpadd", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("lpflash", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("simg2img", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS, requiredForProduct = true),
        ToolCatalogEntry("img2simg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("ext2simg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("append2simg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("mkbootimg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry(
            id = "unpack_bootimg",
            kind = ToolKind.NATIVE,
            recipeGroup = ToolCapabilities.GROUP_ANDROID_TOOLS,
            requiredForProduct = true,
        ),
        ToolCatalogEntry("repack_bootimg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("mkbootfs", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("mkdtboimg", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("avbtool", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS, requiredForProduct = true),
        ToolCatalogEntry("fec", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),
        ToolCatalogEntry("mkuserimg_mke2fs", ToolKind.NATIVE, ToolCapabilities.GROUP_ANDROID_TOOLS),

        // erofs-utils recipe group
        ToolCatalogEntry("mkfs.erofs", ToolKind.NATIVE, ToolCapabilities.GROUP_EROFS_UTILS, requiredForProduct = true),
        ToolCatalogEntry("dump.erofs", ToolKind.NATIVE, ToolCapabilities.GROUP_EROFS_UTILS),
        ToolCatalogEntry("fsck.erofs", ToolKind.NATIVE, ToolCapabilities.GROUP_EROFS_UTILS),
        ToolCatalogEntry("erofsfuse", ToolKind.NATIVE, ToolCapabilities.GROUP_EROFS_UTILS, requiredForProduct = true),

        // img2sdat recipe group
        ToolCatalogEntry("img2sdat", ToolKind.NATIVE, ToolCapabilities.GROUP_IMG2SDAT, requiredForProduct = true),

        // apktool recipe group
        ToolCatalogEntry("apktool", ToolKind.JAR, ToolCapabilities.GROUP_APKTOOL),

        // signapk recipe group
        ToolCatalogEntry("signapk", ToolKind.JAR, ToolCapabilities.GROUP_SIGNAPK, requiredForProduct = true),

        // payload-dumper-go recipe group
        ToolCatalogEntry(
            id = "payload-dumper-go",
            kind = ToolKind.NATIVE,
            recipeGroup = ToolCapabilities.GROUP_PAYLOAD_DUMPER_GO,
            requiredForProduct = true,
        ),

        // gh recipe group (pinned release; the publishing adapter runs it through the service)
        ToolCatalogEntry("gh", ToolKind.NATIVE, ToolCapabilities.GROUP_GH),
    )

    public val byId: Map<String, ToolCatalogEntry> = entries.associateBy { it.id }

    public val ALL_TOOL_IDS: Set<String> = entries.map { it.id }.toSet()

    public val REQUIRED_PRODUCT_TOOLS: Set<String> =
        entries.filter { it.requiredForProduct }.map { it.id }.toSet()

    public val CORE_BINARIES: List<String> = listOf(
        "adb",
        "fastboot",
        "mke2fs",
        "dump.erofs",
        "mkfs.erofs",
        "img2sdat",
    )

    public val ALL_CORE_TOOLS: List<String> = entries.map { it.id }

    public fun isCatalogTool(toolId: String): Boolean = toolId in byId

    public fun entryFor(toolId: String): ToolCatalogEntry? = byId[toolId]
}
