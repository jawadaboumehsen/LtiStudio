/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.recipe

import org.ide.lti.core.domain.setup.RecipeConfig

public class AndroidToolsRecipe : BuildRecipe<RecipeConfig.AndroidTools> {

    companion object {
        private const val CONFIGURE_TIMEOUT_SECONDS = 120L
        private const val MAKE_TIMEOUT_SECONDS = 3_600L

        private val GIT_AM_IDENTITY = mapOf(
            "GIT_AUTHOR_NAME" to "LtiRom Studio",
            "GIT_AUTHOR_EMAIL" to "ltirom@localhost",
            "GIT_COMMITTER_NAME" to "LtiRom Studio",
            "GIT_COMMITTER_EMAIL" to "ltirom@localhost",
        )
    }

    @Suppress("ReturnCount")
    override suspend fun build(ctx: RecipeContext, config: RecipeConfig.AndroidTools): RecipeResult {
        val runner = RecipeCommandRunner(ctx.cli)
        val src = "${ctx.extDir}/android-tools"
        if (!runner.exists(ctx.distro, src, "-d") || !runner.exists(ctx.distro, "$src/CMakeLists.txt", "-f")) {
            return RecipeResult.Unsupported(listOf("$src/CMakeLists.txt"))
        }

        if (!ctx.forceRebuild &&
            runner.isRecorded(ctx.repository, "adb") &&
            runner.groupComplete(ctx.distro, ctx.binDir, ctx.group)
        ) {
            ctx.log("[Step 5/5] android-tools: every tool is present in ${ctx.binDir}. Skipping CMake/Make.")
            return RecipeResult.Built(ctx.group.outputs.map { it.file })
        }

        ctx.log("[Step 5/5] Compiling android-tools via CMake and Make...")
        val buildDir = "$src/build"
        runner.run(ctx.distro, listOf("mkdir", "-p", buildDir))
        if (ctx.forceRebuild) {
            runner.run(ctx.distro, listOf("rm", "-rf", "$buildDir/CMakeCache.txt", "$buildDir/CMakeFiles"))
            ctx.log("[Step 5/5] Rebuild: discarded the previous CMake configuration in $buildDir.")
        }

        val configureArgs = listOf(
            "-B", "build",
            "-S", src,
            "-DCMAKE_BUILD_TYPE=Release",
            "-DCMAKE_C_COMPILER=clang",
            "-DCMAKE_CXX_COMPILER=clang++",
            "-DANDROID_TOOLS_USE_BUNDLED_FMT=ON",
            "-DANDROID_TOOLS_USE_BUNDLED_LIBUSB=ON",
        ) + (if (config.patchVendor) listOf("-DANDROID_TOOLS_PATCH_VENDOR=ON") else emptyList()) + config.cmakeArgs

        val cmakeEnv = if (config.gitIdentity) GIT_AM_IDENTITY else emptyMap()
        val cmakeRes = runner.executeBuildCommand(
            ctx = ctx,
            workingDir = src,
            toolId = "cmake",
            arguments = configureArgs,
            timeoutSeconds = CONFIGURE_TIMEOUT_SECONDS,
            actionName = "android-tools:cmake",
            environment = cmakeEnv,
        ).getOrElse { return RecipeResult.Failed(it.message ?: "android-tools cmake failed") }

        if (cmakeRes != null) {
            return RecipeResult.Failed("android-tools cmake configuration failed", cmakeRes)
        }

        val makeRes = runner.executeBuildCommand(
            ctx = ctx,
            workingDir = buildDir,
            toolId = "make",
            arguments = listOf("-C", buildDir, "-j4"),
            timeoutSeconds = MAKE_TIMEOUT_SECONDS,
            actionName = "android-tools:make",
        ).getOrElse { return RecipeResult.Failed(it.message ?: "android-tools make failed") }

        if (makeRes != null) {
            return RecipeResult.Failed("android-tools make build failed", makeRes)
        }

        val vendor = "$src/vendor"
        val failure = runner.firstFailure(
            {
                if (runner.exists(ctx.distro, "$buildDir/vendor", "-d")) {
                    runner.copy(ctx.distro, "$buildDir/vendor/.", "${ctx.binDir}/")
                } else {
                    "the build produced no vendor outputs"
                }
            },
            { runner.copyIfPresent(ctx.distro, "$vendor/avb/avbtool.py", "${ctx.binDir}/avbtool") },
            { runner.copyIfPresent(ctx.distro, "$vendor/mkbootimg/mkbootimg.py", "${ctx.binDir}/mkbootimg.py") },
            { runner.copyIfPresent(ctx.distro, "$vendor/mkbootimg/gki", "${ctx.binDir}/") },
            {
                runner.copyIfPresent(
                    ctx.distro,
                    "$vendor/extras/ext4_utils/mkuserimg_mke2fs.py",
                    "${ctx.binDir}/mkuserimg_mke2fs",
                )
            },
            {
                runner.linkIfPresent(
                    ctx.distro,
                    "${ctx.binDir}/mke2fs.android",
                    "mke2fs.android",
                    "${ctx.binDir}/mke2fs",
                )
            },
            {
                runner.linkIfPresent(
                    ctx.distro,
                    "${ctx.binDir}/mkbootimg.py",
                    "mkbootimg.py",
                    "${ctx.binDir}/mkbootimg",
                )
            },
            {
                runner.checked(
                    ctx.distro,
                    listOf(
                        "chmod",
                        "+x",
                        "${ctx.binDir}/avbtool",
                        "${ctx.binDir}/mkbootimg",
                        "${ctx.binDir}/mke2fs",
                        "${ctx.binDir}/mkuserimg_mke2fs",
                    ),
                )
            },
        )
        if (failure != null) {
            return RecipeResult.Failed("android-tools built, but installing its outputs failed: $failure")
        }

        runner.recordInstalledBinaries(ctx)
        return RecipeResult.Built(ctx.group.outputs.map { it.file })
    }
}
