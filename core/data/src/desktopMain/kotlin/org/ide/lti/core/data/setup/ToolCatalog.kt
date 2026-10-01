/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolOutputKind

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
 * Rewritten as a derived view over [ToolGroupCatalog] (FR-001, US7).
 */
public object ToolCatalog {

    public val entries: List<ToolCatalogEntry>
        get() = ToolGroupCatalog.allOutputs.map { output ->
            val group = ToolGroupCatalog.groupByToolId(output.toolId)?.id?.value ?: ""
            ToolCatalogEntry(
                id = output.toolId,
                kind = when (output.kind) {
                    ToolOutputKind.JAR -> ToolKind.JAR
                    ToolOutputKind.NATIVE, ToolOutputKind.SCRIPT -> ToolKind.NATIVE
                },
                recipeGroup = group,
                requiredForProduct = output.requiredForProduct,
            )
        }

    public val byId: Map<String, ToolCatalogEntry> get() = entries.associateBy { it.id }

    public val ALL_TOOL_IDS: Set<String> get() = entries.map { it.id }.toSet()

    public val REQUIRED_PRODUCT_TOOLS: Set<String> get() =
        entries.filter { it.requiredForProduct }.map { it.id }.toSet()

    public val CORE_BINARIES: List<String> = listOf(
        "adb",
        "fastboot",
        "mke2fs",
        "dump.erofs",
        "mkfs.erofs",
        "img2sdat",
    )

    public val ALL_CORE_TOOLS: List<String> get() = entries.map { it.id }

    public fun isCatalogTool(toolId: String): Boolean = toolId in byId

    public fun entryFor(toolId: String): ToolCatalogEntry? = byId[toolId]

    /** How [ToolCatalog.probePlan] proves a tool runs: the existence check, then the probe command. */
    public data class ProbePlan(
        val path: String,
        val existsCheck: List<String>,
        val argv: List<String>,
        val timeoutSeconds: Long,
    )

    /**
     * Packaging-aware probe, the same for Test and repair verification: a JAR is checked readable (not
     * executable, as publication accepts it) and run through `java -jar`; a native tool is checked
     * executable and run directly. [fallbackBinaryName] covers a tool the catalog doesn't list.
     */
    public fun probePlan(toolId: String, binDir: String, fallbackBinaryName: String): ProbePlan {
        val path = expectedPath(binDir, toolId) ?: "${binDir.trimEnd('/')}/$fallbackBinaryName"
        return if (entryFor(toolId)?.kind == ToolKind.JAR) {
            ProbePlan(
                path = path,
                existsCheck = listOf("test", "-r", path),
                argv = listOf("java", "-jar", path) + ToolCapabilities.probeArgumentsFor(toolId),
                timeoutSeconds = JAR_PROBE_TIMEOUT_SECONDS,
            )
        } else {
            ProbePlan(
                path = path,
                existsCheck = listOf("test", "-x", path),
                argv = ToolCapabilities.probeArgumentsFor(toolId, path),
                timeoutSeconds = NATIVE_PROBE_TIMEOUT_SECONDS,
            )
        }
    }

    private const val NATIVE_PROBE_TIMEOUT_SECONDS = 5L

    /** JVM start plus class loading (apktool) needs more than a native binary's 5 s. */
    private const val JAR_PROBE_TIMEOUT_SECONDS = 20L

    /** Where a catalog tool is installed and published from. */
    public fun expectedPath(binDir: String, toolId: String): String? =
        entryFor(toolId)?.let { "${binDir.trimEnd('/')}/${it.binaryName}" }

    /**
     * The one publication rule, shared by publishing and the readiness check: the service resolves the tool
     * from this app's manifest ([ToolSource.DYNAMIC]) at its expected path in [binDir]. A system or fallback
     * copy of the same name doesn't count: it would mask a missing project artifact.
     */
    public fun isProjectPublished(status: ToolStatus, binDir: String): Boolean =
        status.installed && status.source == ToolSource.DYNAMIC && status.path == expectedPath(binDir, status.tool)
}
