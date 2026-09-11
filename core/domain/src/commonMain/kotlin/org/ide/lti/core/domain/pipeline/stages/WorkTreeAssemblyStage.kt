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
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.BuildPropStamper
import org.ide.lti.core.model.run.StageId

/**
 * Stage 3 — assemble the editable `work/` tree from the extracted partitions (contract Stage 3):
 * rsync per partition with sidecars, optional `system_ext` fold-in, boot images with AVB footers
 * erased, AOT purge, and the `build.prop` stamp.
 */
public class WorkTreeAssemblyStage : StageDefinition {
    override val id: StageId = StageId.WORK_TREE_ASSEMBLY

    override val requiredToolIds: Set<String> = setOf("rsync", "avbtool", "find", "cp", "mkdir", "cat")

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String =
        CacheKeys.stage3AssemblyKey(previousStageCacheKey, ctx.target, ctx.snapshot)

    override fun outputs(ctx: StageContext): List<String> =
        listOf(BUILD_PROP) + syncedPartitions(ctx).flatMap { p ->
            listOf("work/configs/fs_config-$p", "work/configs/file_context-$p")
        }

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(FirmwareExtractionStage.EXTRACTED)
        val work = ctx.abs("work")
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand("mkdir", listOf("-p", "$work/configs", "$work/kernel"), ws),
            "mkdir work",
        )

        syncedPartitions(ctx).forEach { p ->
            steps += PipelineStep.Tool(
                stepCommand("rsync", listOf("-a", "--delete", "$extracted/$p/", "$work/$p/"), ws),
                "rsync $p",
            )
            steps += PipelineStep.Tool(
                stepCommand("cp", listOf("-a", "$extracted/fs_config-$p", "$work/configs/"), ws),
                "cp fs_config $p",
            )
            steps += PipelineStep.Tool(
                stepCommand("cp", listOf("-a", "$extracted/file_context-$p", "$work/configs/"), ws),
                "cp file_context $p",
            )
        }
        if (foldsSystemExt(ctx)) steps += systemExtFoldIn(ctx)

        ctx.target.bootPartitions.forEach { b ->
            val infoKey = RuntimeKeys.kernelInfo(b)
            steps += PipelineStep.Tool(
                stepCommand("cp", listOf("-a", "$extracted/$b.img", "$work/kernel/$b.img"), ws),
                "cp kernel $b",
            )
            steps += PipelineStep.Tool(
                command = stepCommand("avbtool", listOf("info_image", "--image", "$work/kernel/$b.img"), ws),
                label = "avbtool info kernel $b",
                resultKey = infoKey,
                allowFailure = true,
            )
            if (ctx.exitOf(infoKey) == 0) {
                steps += PipelineStep.Tool(
                    stepCommand("avbtool", listOf("erase_footer", "--image", "$work/kernel/$b.img"), ws),
                    "erase footer $b",
                )
            }
        }

        steps += PipelineStep.Tool(
            stepCommand("find", listOf(work, "-type", "d", "-name", "oat", "-exec", "rm", "-rf", "{}", "+"), ws),
            "purge oat dirs",
        )
        steps += PipelineStep.Tool(
            stepCommand(
                "find",
                listOf(work, "-type", "f", "(") + AOT_NAME_ARGS + listOf(")", "-delete"),
                ws,
            ),
            "purge aot files",
        )
        steps += PipelineStep.EditFile(BUILD_PROP, BuildPropStamper.stamper(ctx), "stamp build.prop")
        return steps
    }

    private fun systemExtFoldIn(ctx: StageContext): List<PipelineStep> {
        val ws = ctx.wsPath
        val extracted = ctx.abs(FirmwareExtractionStage.EXTRACTED)
        val target = ctx.abs("work/system/system/system_ext")
        return listOf(
            PipelineStep.Tool(stepCommand("mkdir", listOf("-p", target), ws), "mkdir system_ext fold"),
            PipelineStep.Tool(
                stepCommand("rsync", listOf("-a", "--delete", "$extracted/system_ext/", "$target/"), ws),
                "rsync system_ext fold",
            ),
            PipelineStep.Tool(
                command = stepCommand("cat", listOf("$extracted/fs_config-system_ext"), ws),
                label = "read fs_config system_ext",
                resultKey = "fold.system_ext.fs_config",
            ),
            PipelineStep.Tool(
                command = stepCommand("cat", listOf("$extracted/file_context-system_ext"), ws),
                label = "read file_context system_ext",
                resultKey = "fold.system_ext.file_context",
            ),
            // Sidecar lines of the folded partition are re-prefixed under system/system_ext.
            PipelineStep.EditFile(
                "work/configs/fs_config-system",
                { it + foldedSidecar(ctx, "fs_config") },
                "fold fs_config",
            ),
            PipelineStep.EditFile(
                "work/configs/file_context-system",
                { it + foldedSidecar(ctx, "file_context") },
                "fold file_context",
            ),
        )
    }

    /** Sidecar text of `system_ext` re-prefixed for its folded location; read at plan time from runtime values. */
    private fun foldedSidecar(ctx: StageContext, kind: String): String {
        val text = ctx.value("fold.system_ext.$kind") ?: return ""
        return text.lineSequence().filter { it.isNotBlank() }.joinToString("\n", postfix = "\n") { line ->
            if (kind == "fs_config") {
                line.replaceFirst(Regex("^system_ext(?=[ /])"), "system/system_ext")
            } else {
                line.replaceFirst(Regex("^/system_ext(?=[ /])"), "/system/system_ext")
            }
        }
    }

    private fun foldsSystemExt(ctx: StageContext): Boolean =
        !ctx.target.hasStandaloneSystemExt && "system_ext" in ctx.target.dynamicPartitions

    private fun syncedPartitions(ctx: StageContext): List<String> =
        ctx.target.dynamicPartitions.filter { it != "system_ext" || ctx.target.hasStandaloneSystemExt }

    public companion object {
        public const val BUILD_PROP: String = "work/system/system/build.prop"
        private val AOT_NAME_ARGS = listOf(
            "-name", "*.vdex", "-o", "-name", "*.odex", "-o", "-name", "*.art",
            "-o", "-name", "boot-image.prof", "-o", "-name", "boot-image.bprof",
        )
    }
}
