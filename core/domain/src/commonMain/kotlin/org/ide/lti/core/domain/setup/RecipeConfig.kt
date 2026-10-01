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

/**
 * A curated, checksummed release version for downloaded binary tools.
 */
data class ReleaseVersion(val version: String, val url: String, val archiveMember: String, val sha256: String)

/**
 * Sealed configuration for building or downloading a tool group's artifacts.
 * Each configuration carries a monotonic [revision] that is incremented when the recipe changes.
 */
sealed interface RecipeConfig {
    val revision: Int

    data class AndroidTools(
        val cmakeArgs: List<String> = emptyList(),
        val gitIdentity: Boolean = true,
        val patchVendor: Boolean = true,
        override val revision: Int = 1,
    ) : RecipeConfig

    data class CMake(
        val sourceSubdir: String? = null,
        val buildSubdir: String = "build",
        val cmakeArgs: List<String> = emptyList(),
        override val revision: Int = 1,
    ) : RecipeConfig

    data class GradleJar(val task: String, val outputPath: String, override val revision: Int = 1) : RecipeConfig

    data class ScriptCopy(val files: List<String>, override val revision: Int = 1) : RecipeConfig

    data class Release(val versions: List<ReleaseVersion>, override val revision: Int = 1) : RecipeConfig
}
