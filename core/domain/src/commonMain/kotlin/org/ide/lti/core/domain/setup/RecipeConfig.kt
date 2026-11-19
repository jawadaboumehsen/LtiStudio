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

    /**
     * `./configure CC=clang <configureArgs>`, then `make -j<n> <args>` per [make] entry, in the source root; first
     * `./autogen.sh` when [autogen] (a git checkout without a generated `configure`).
     */
    data class Autotools(
        val configureArgs: List<String> = emptyList(),
        val make: List<String> = listOf(""),
        val autogen: Boolean = false,
        override val revision: Int = 1,
    ) : RecipeConfig

    data class GradleJar(val task: String, val outputPath: String, override val revision: Int = 1) : RecipeConfig

    data class ScriptCopy(val files: List<String>, override val revision: Int = 1) : RecipeConfig

    data class Release(val versions: List<ReleaseVersion>, override val revision: Int = 1) : RecipeConfig

    /**
     * AOSP sources built with CMake outside AOSP, which ships no such build: the group's source, plus [projects]
     * fetched beside it (each at its commit, checked), plus the build files the app carries in its resources under
     * `tool-recipes/<bundle>/` ([bundledFiles], a CMakeLists.txt first), written to `ltirom-build/` in the source.
     */
    data class AospCMake(
        val projects: List<AospProject>,
        val bundle: String,
        val bundledFiles: List<String>,
        override val revision: Int = 1,
    ) : RecipeConfig
}

/** An AOSP project fetched to `aosp/<path>` beside an [RecipeConfig.AospCMake] group's source, at [commit]. */
data class AospProject(val path: String, val url: String, val commit: String)
