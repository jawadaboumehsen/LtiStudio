/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts

/**
 * Rebuild only what changed: an EROFS image whose tree, sidecars and recipe are what the last build made it from is
 * not built again. The image left in out/images by that build has its footer erased and then goes through the same
 * read-back verify, hash and footer steps as a new one, so a stale image is still caught by the verify.
 *
 * The fingerprint is each entry's path, kind, size, time, mode and link target, both sidecar files and the recipe
 * (filesystem, compression, dedupe, cluster, block size, tool paths). It is removed before an image is built and
 * written only after its footer, so an image from a build that stopped part way is never reused.
 */
internal object ReusedImage {
    // ponytail: size and time stand for the content (hashing every file costs tens of seconds a build); a file
    // rewritten with the same size and an old time is missed. Hash the files whose time is newer if that bites.

    fun key(p: String): String = "build.reuse.$p"

    private fun recipeFile(p: String) = "out/images/$p.recipe"

    /**
     * Adds [p]'s reuse steps to [steps]. True: the last build's image is in place for the steps that follow; false: not
     * decided yet (its fingerprint is being read); null: build it (its old recipe is removed first).
     */
    fun steps(ctx: StageContext, p: String, steps: MutableList<PipelineStep>): Boolean? {
        val recipe = recipe(ctx, p)
        return when {
            BuildFilesystems.of(ctx.snapshot.build, p) != BuildFilesystems.EROFS -> null
            recipe == null -> false
            else -> decide(ctx, p, recipe, steps)
        }
    }

    private fun decide(ctx: StageContext, p: String, recipe: String, steps: MutableList<PipelineStep>): Boolean? {
        val ws = ctx.wsPath
        val image = ctx.abs("out/images/$p.img")
        val sidecars = listOf(ctx.abs("work/configs/fs_config-$p"), ctx.abs("work/configs/file_context-$p"))
        val args = listOf("-c", FINGERPRINT_SCRIPT, "sh", ctx.abs("work/$p")) + sidecars +
            listOf(image, ctx.abs(recipeFile(p)), recipe)
        steps += PipelineStep.Tool(stepCommand("sh", args, ws), label = "fingerprint $p", resultKey = key(p))
        val found = ctx.value(key(p))
        return when {
            found == null -> false

            found.startsWith(SAME) -> {
                val avbtool = ctx.toolPaths["avbtool"] ?: "avbtool"
                val erase = listOf("-c", KeptPartition.ERASE_SCRIPT, "sh", avbtool, image)
                steps += PipelineStep.Tool(stepCommand("sh", erase, ws), "reuse $p from the last build")
                true
            }

            else -> {
                steps += PipelineStep.Tool(
                    stepCommand("rm", listOf("-f", ctx.abs(recipeFile(p))), ws),
                    "forget $p recipe",
                )
                null
            }
        }
    }

    /** Records [p]'s fingerprint once its image is complete (after its footer). */
    fun record(ctx: StageContext, p: String): PipelineStep? = ctx.value(
        key(p),
    )?.substringAfter(' ')?.let { fingerprint ->
        PipelineStep.Tool(
            stepCommand(
                "sh",
                listOf("-c", "printf '%s' \"\$2\" > \"\$1\"", "sh", ctx.abs(recipeFile(p)), fingerprint),
                ctx.wsPath,
            ),
            "record $p recipe",
        )
    }

    /** Whether [p]'s image came from the last build this run. */
    fun reused(ctx: StageContext, p: String): Boolean = ctx.value(key(p))?.startsWith(SAME) == true

    /** What the image's bytes depend on besides the tree; null until the boot kernel is read (dedupe follows it). */
    private fun recipe(ctx: StageContext, p: String): String? {
        val kernel = ctx.value(BuildChecks.KERNEL_KEY) ?: return null
        val build = ctx.snapshot.build
        return listOf(
            CacheKeys.PIPELINE_VERSION,
            BuildFilesystems.EROFS,
            BuildFilesystems.erofsCompression(build, p),
            BuildFilesystems.erofsDedupe(build, p, KernelFacts.parse(kernel)?.version).toString(),
            BuildFilesystems.erofsCluster(build, p)?.toString().orEmpty(),
            build.blockSize.toString(),
            ctx.toolPaths["mkfs.erofs"].orEmpty(),
        ).joinToString("|")
    }

    private const val SAME = "same "

    /**
     * `$1` tree, `$2`/`$3` sidecars, `$4` image, `$5` recorded fingerprint, `$6` recipe. Prints `same <fp>` when the
     * image is there and was built from this fingerprint, else `changed <fp>`.
     */
    private const val FINGERPRINT_SCRIPT = """
fp=${'$'}( { printf '%s\n' "$6"; (cd "$1" && find . -printf '%P\t%y\t%s\t%T@\t%m\t%l\n' | LC_ALL=C sort); cat "$2" "$3"; } | sha256sum | cut -d' ' -f1)
if [ -f "$4" ] && [ -f "$5" ] && [ "${'$'}(cat "$5")" = "${'$'}fp" ]; then echo "same ${'$'}fp"; else echo "changed ${'$'}fp"; fi"""
}
