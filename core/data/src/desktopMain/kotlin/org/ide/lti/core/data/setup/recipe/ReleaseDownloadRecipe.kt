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

public class ReleaseDownloadRecipe : BuildRecipe<RecipeConfig.Release> {

    companion object {
        private const val DOWNLOAD_TIMEOUT_SECONDS: Long = 300L
    }

    @Suppress("ReturnCount")
    override suspend fun build(ctx: RecipeContext, config: RecipeConfig.Release): RecipeResult {
        if (config.versions.isEmpty()) {
            return RecipeResult.Unsupported(listOf("No release versions configured for ${ctx.group.id.value}"))
        }

        val releaseInput = ctx.resolvedInput as? org.ide.lti.core.domain.setup.ResolvedInput.Release
        val release = if (releaseInput != null) {
            config.versions.firstOrNull { it.version == releaseInput.version } ?: config.versions.first()
        } else {
            config.versions.first()
        }
        val runner = RecipeCommandRunner(ctx.cli)
        val tool = ctx.group.outputs.firstOrNull()?.toolId ?: ctx.group.id.value
        val dest = "${ctx.binDir}/$tool"
        val scratch = "${ctx.extDir}/tmp/$tool"

        val label = "$tool ${release.version}"
        val currentSha = runner.sha256Of(ctx.distro, dest)
        if (!ctx.forceRebuild && currentSha == release.sha256) {
            ctx.log("[Step 5/5] $label already installed (checksum verified).")
            runner.recordInstalledBinaries(ctx)
            return RecipeResult.Built(listOf(tool))
        }

        val archive = "$scratch/release.tar.gz"
        val extracted = "$scratch/extract/${release.archiveMember}"
        runner.run(ctx.distro, listOf("rm", "-rf", "$scratch/extract"))
        runner.run(ctx.distro, listOf("mkdir", "-p", "$scratch/extract"))

        val dlRes = runner.run(
            ctx.distro,
            listOf("curl", "-fsSL", "--retry", "3", "-o", archive, release.url),
            timeoutSeconds = DOWNLOAD_TIMEOUT_SECONDS,
        )
        if (dlRes.exitCode != 0) {
            val err = dlRes.error.ifBlank { dlRes.output }.ifBlank { "exit ${dlRes.exitCode}" }
            val msg = "Could not install $label from ${release.url}: download failed: $err"
            ctx.log("[Step 5/5] ERROR: $msg")
            return RecipeResult.Failed(msg, err)
        }

        val extractRes = runner.run(
            ctx.distro,
            listOf("tar", "-xzf", archive, "-C", "$scratch/extract", release.archiveMember),
            timeoutSeconds = DOWNLOAD_TIMEOUT_SECONDS,
        )
        if (extractRes.exitCode != 0) {
            val err = extractRes.error.ifBlank { extractRes.output }.ifBlank { "exit ${extractRes.exitCode}" }
            val msg = "Could not install $label from ${release.url}: extract failed: $err"
            ctx.log("[Step 5/5] ERROR: $msg")
            return RecipeResult.Failed(msg, err)
        }

        val actualSha = runner.sha256Of(ctx.distro, extracted)
        if (actualSha != release.sha256) {
            runner.run(ctx.distro, listOf("rm", "-rf", "$scratch/extract", archive))
            val msg = "Could not install $label from ${release.url}: " +
                "checksum mismatch (expected ${release.sha256}, got ${actualSha ?: "none"}); not installed"
            ctx.log("[Step 5/5] ERROR: $msg")
            return RecipeResult.Failed(msg, "checksum mismatch")
        }

        val installed = runner.run(ctx.distro, listOf("install", "-m", "755", extracted, "$dest.new")).exitCode == 0 &&
            runner.run(ctx.distro, listOf("mv", "-f", "$dest.new", dest)).exitCode == 0

        runner.run(ctx.distro, listOf("rm", "-rf", "$scratch/extract", archive))
        if (!installed) {
            val msg = "Could not install $label: could not write $dest"
            ctx.log("[Step 5/5] ERROR: $msg")
            return RecipeResult.Failed(msg, "could not write $dest")
        }

        ctx.log("[Step 5/5] Installed $label (checksum verified).")
        runner.recordInstalledBinaries(ctx)
        return RecipeResult.Built(listOf(tool))
    }
}
