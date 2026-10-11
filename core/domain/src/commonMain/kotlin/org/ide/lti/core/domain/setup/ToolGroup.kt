/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import org.ide.lti.core.model.setup.ToolRef
import kotlin.jvm.JvmInline

/**
 * Type-safe identifier for a tool build/download group.
 */
@JvmInline
value class ToolGroupId(val value: String) {
    init {
        require(value.isNotBlank()) { "ToolGroupId must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * Source specification for a tool group: Git repository with pinned ref and commit, or curated Release downloads.
 */
sealed interface ToolSource {
    data class Git(val recommendedUrl: String, val pinnedRef: ToolRef, val pinnedCommit: String) : ToolSource {
        val recommendedCommit: String get() = pinnedCommit
        val recommendedLabel: String get() =
            when (pinnedRef) {
                is ToolRef.Tag -> pinnedRef.name
                is ToolRef.Branch -> pinnedRef.name
                is ToolRef.Commit -> pinnedRef.sha
                is ToolRef.ReleaseVersion -> pinnedRef.version
            }
    }

    data object Release : ToolSource
}

/**
 * Classification of an installed tool binary.
 */
enum class ToolOutputKind {
    NATIVE,
    JAR,
    SCRIPT,
}

/**
 * Verification probe specification for a tool output.
 */
data class ToolProbe(
    val args: List<String>,
    val acceptedExitCodes: Set<Int> = setOf(0),
    /**
     * Regex (case-insensitive, grep -E) the probe output must match. An exit code alone proves nothing: a stub
     * that exits 1 passes for a tool whose accepted codes include 1. Null means the usual help or version words,
     * or the tool's own name.
     */
    val signature: String? = null,
)

/**
 * An individual executable output installed by a tool group.
 */
data class ToolOutput(
    val toolId: String,
    val file: String,
    val kind: ToolOutputKind,
    val probe: ToolProbe,
    val requiredForProduct: Boolean = true,
    val aliases: Map<String, String> = emptyMap(),
    val supportFiles: List<String> = emptyList(),
    val mode: String = "exec",
    val aliasOf: String? = null,
    val requiredFor: List<String> = emptyList(),
)

/**
 * Cohesive group of tools built from a single repository or downloaded release archive.
 */
data class ToolGroup(
    val id: ToolGroupId,
    val source: ToolSource,
    val recipe: RecipeConfig,
    val layout: List<String>,
    val submodulePaths: List<String> = emptyList(),
    val outputs: List<ToolOutput>,
    val buildPackages: List<String>,
    val supportFiles: List<String> = emptyList(),
) {
    val requiredLayout: List<String> get() = layout
    val packages: List<String> get() = buildPackages
}
