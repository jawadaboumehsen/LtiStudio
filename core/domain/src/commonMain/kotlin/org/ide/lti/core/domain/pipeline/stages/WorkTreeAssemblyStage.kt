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

    override fun outputs(ctx: StageContext): List<String> {
        val partitions = effectiveDynamicPartitions(ctx)
        return listOf(BUILD_PROP) + syncedPartitions(ctx, partitions).flatMap { p ->
            listOf("work/configs/fs_config-$p", "work/configs/file_context-$p")
        }
    }

    private fun validate(ctx: StageContext): PipelineStep.Check? {
        val included = ctx.snapshot.assembly.includedPartitions
        val invalidIncluded = included.filter { it !in ctx.target.dynamicPartitions }
        val systemExtMode = ctx.snapshot.assembly.systemExtMode.lowercase()
        val bootFooterPolicy = ctx.snapshot.assembly.bootFooterPolicy.lowercase()
        val invalidProp = ctx.snapshot.assembly.propertyOverrides.entries.firstOrNull { (k, v) ->
            '\n' in k || '\u0000' in k || '\n' in v || '\u0000' in v
        }
        val reserved = ctx.snapshot.assembly.propertyOverrides.keys.filter { it in RESERVED_PROPERTIES }

        return when {
            invalidIncluded.isNotEmpty() ->
                PipelineStep.Check("verify included partitions on target") {
                    "PARTITION_NOT_ON_TARGET: Included partition(s) not on target: ${invalidIncluded.joinToString()}"
                }
            systemExtMode !in SUPPORTED_SYSTEM_EXT_MODES ->
                PipelineStep.Check("verify system_ext mode") {
                    "UNSUPPORTED_SYSTEM_EXT_MODE: Unsupported system_ext mode '${ctx.snapshot.assembly.systemExtMode}'"
                }
            bootFooterPolicy !in SUPPORTED_FOOTER_POLICIES ->
                PipelineStep.Check("verify boot footer policy") {
                    "UNSUPPORTED_FOOTER_POLICY: Unsupported boot footer policy " +
                        "'${ctx.snapshot.assembly.bootFooterPolicy}'"
                }
            invalidProp != null ->
                PipelineStep.Check("verify property overrides") {
                    "INVALID_PROPERTY_OVERRIDE: Property override key or value contains newline or NUL: " +
                        "'${invalidProp.key}'"
                }
            reserved.isNotEmpty() ->
                PipelineStep.Check("verify property overrides") {
                    "RESERVED_PROPERTY: Property override contains reserved key(s): ${reserved.joinToString()}"
                }
            else -> null
        }
    }

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val validationError = validate(ctx)
        if (validationError != null) {
            return listOf(validationError)
        }

        val ws = ctx.wsPath
        val extracted = ctx.abs(FirmwareExtractionStage.EXTRACTED)
        val work = ctx.abs("work")
        val dynamicPartitions = effectiveDynamicPartitions(ctx)
        val steps = mutableListOf<PipelineStep>()
        steps += PipelineStep.Tool(
            stepCommand("mkdir", listOf("-p", "$work/configs", "$work/kernel"), ws),
            "mkdir work",
        )

        syncedPartitions(ctx, dynamicPartitions).forEach { p ->
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
        if (foldsSystemExt(ctx, dynamicPartitions)) steps += systemExtFoldIn(ctx)

        val shouldEraseFooter = ctx.snapshot.assembly.bootFooterPolicy.equals("erase", ignoreCase = true)
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
            if (shouldEraseFooter && ctx.exitOf(infoKey) == 0) {
                steps += PipelineStep.Tool(
                    stepCommand("avbtool", listOf("erase_footer", "--image", "$work/kernel/$b.img"), ws),
                    "erase footer $b",
                )
            }
        }

        if (ctx.snapshot.assembly.baselineCleanup) {
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
        }

        val baseStamper = BuildPropStamper.stamper(ctx)
        val overrides = ctx.snapshot.assembly.propertyOverrides
        val transform: (String) -> String = { original ->
            val stamped = baseStamper(original)
            if (overrides.isEmpty()) stamped else applyPropertyOverrides(stamped, overrides)
        }
        steps += PipelineStep.EditFile(BUILD_PROP, transform, "stamp build.prop")
        return steps
    }

    private fun effectiveDynamicPartitions(ctx: StageContext): List<String> =
        ctx.snapshot.assembly.includedPartitions.ifEmpty { ctx.target.dynamicPartitions }

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

    private fun foldsSystemExt(ctx: StageContext, dynamicPartitions: List<String>): Boolean {
        if ("system_ext" !in dynamicPartitions) return false
        return when (ctx.snapshot.assembly.systemExtMode.lowercase()) {
            "auto" -> !ctx.target.hasStandaloneSystemExt
            "fold" -> true
            "standalone" -> false
            else -> !ctx.target.hasStandaloneSystemExt
        }
    }

    private fun syncedPartitions(ctx: StageContext, dynamicPartitions: List<String>): List<String> =
        dynamicPartitions.filter { it != "system_ext" || !foldsSystemExt(ctx, dynamicPartitions) }

    private fun applyPropertyOverrides(text: String, overrides: Map<String, String>): String {
        val remaining = overrides.toMutableMap()
        val lines = text.lineSequence().map { line ->
            val key = line.substringBefore('=')
            if (key in remaining) {
                val value = remaining.remove(key)
                "$key=$value"
            } else {
                line
            }
        }.toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) {
            lines.removeAt(lines.lastIndex)
        }
        for ((k, v) in remaining) {
            lines.add("$k=$v")
        }
        return lines.joinToString("\n", postfix = "\n")
    }

    public companion object {
        public const val BUILD_PROP: String = "work/system/system/build.prop"
        private val SUPPORTED_SYSTEM_EXT_MODES = setOf("auto", "fold", "standalone")
        private val SUPPORTED_FOOTER_POLICIES = setOf("erase", "preserve")
        private val RESERVED_PROPERTIES = setOf("ro.build.version.release", "ro.build.version.security_patch")
        private val AOT_NAME_ARGS = listOf(
            "-name", "*.vdex", "-o", "-name", "*.odex", "-o", "-name", "*.art",
            "-o", "-name", "boot-image.prof", "-o", "-name", "boot-image.bprof",
        )
    }
}
