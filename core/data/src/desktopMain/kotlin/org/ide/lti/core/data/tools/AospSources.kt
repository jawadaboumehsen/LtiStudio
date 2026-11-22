/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.tools.ToolError
import java.util.Base64

/**
 * Readies an [RecipeConfig.AospCMake] source for its CMake build: each of its AOSP projects fetched to
 * `aosp/<path>` at its commit (checked), and the build files the app carries (resources `tool-recipes/<bundle>/`)
 * written to [BUNDLED_DIR]. [run] runs a tool in the source and returns its exit code; an exit outside the codes it
 * is given fails the install. Files are written by a command (base64 in an argument): the build service takes
 * uploads only into workspaces, and the tools folder is not one.
 */
internal class AospSources(private val run: suspend (tool: String, args: List<String>, accepted: Set<Int>) -> Int) {

    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    /** The folder of the written build files (CMake's source). */
    suspend fun prepare(srcDir: String, recipe: RecipeConfig.AospCMake): String {
        for (project in recipe.projects) {
            KotlinStoreLayout.validateRelativePath(project.path)
            val args = listOf("-c", FETCH_PROJECT, "sh", "$srcDir/aosp/${project.path}", project.url, project.commit)
            // One retry: a shallow fetch over a slow link may drop; the folder is made again. Exit 5 is final.
            if (run("sh", args, setOf(0, 1)) != 0) run("sh", args, setOf(0))
        }
        val buildFiles = "$srcDir/$BUNDLED_DIR"
        for (file in recipe.bundledFiles) {
            KotlinStoreLayout.validateRelativePath(file)
            val bytes = withContext(ioDispatcher) {
                javaClass.getResourceAsStream("/tool-recipes/${recipe.bundle}/$file")?.use { it.readBytes() }
            } ?: throw ToolErrorException(
                ToolError.BuildFailed("The app lacks its build file ${recipe.bundle}/$file"),
            )
            val base64 = Base64.getEncoder().encodeToString(bytes)
            run("sh", listOf("-c", WRITE_FILE, "sh", "$buildFiles/$file", base64), setOf(0))
        }
        return buildFiles
    }

    companion object {
        /** An [RecipeConfig.AospCMake] build's own files, under its source (CMake's source folder). */
        const val BUNDLED_DIR = "ltirom-build"
    }
}

/**
 * `$1` folder, `$2` repository, `$3` commit: a shallow fetch of exactly that commit; exit 1 when the fetch fails
 * (retried), 5 when the repository gives another commit.
 */
private const val FETCH_PROJECT = """d="$1"; url="$2"; c="$3"
rm -rf "${'$'}d" && mkdir -p "${'$'}d" && cd "${'$'}d" || exit 1
git init -q && git fetch -q --depth 1 "${'$'}url" "${'$'}c" &&
  git -c advice.detachedHead=false checkout -q FETCH_HEAD || exit 1
[ "${'$'}(git rev-parse HEAD)" = "${'$'}c" ] || { echo "${'$'}url is not at ${'$'}c" >&2; exit 5; }"""

/** `$1` file, `$2` its bytes in base64: its folder made, the file written. */
private const val WRITE_FILE = """mkdir -p "${'$'}(dirname "$1")" && printf '%s' "$2" | base64 -d > "$1""""
