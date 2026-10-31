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

import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.BuildPropStamper
import org.ide.lti.core.domain.pipeline.text.FstabEditor
import org.ide.lti.core.domain.pipeline.toolkit.KernelOverrides
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Stage 3 — assemble the editable `work/` tree from the extracted partitions (contract Stage 3):
 * rsync per partition with sidecars, optional `system_ext` fold-in, boot images with AVB footers
 * erased, AOT purge, and the `build.prop` stamp.
 */
public class WorkTreeAssemblyStage : StageDefinition {
    override val id: StageId = StageId.WORK_TREE_ASSEMBLY

    override val requiredToolIds: Set<String> = setOf("rsync", "avbtool", "find", "cp", "mkdir", "cat", "sha256sum")

    /**
     * Debloat and Patches & mods edit work/ in place, so a change to either needs a fresh tree: their inputs are
     * part of this key, and a new tree makes every later stage run again (never on an already edited tree).
     */
    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String = CacheKeys.sha256(
        CacheKeys.stage3AssemblyKey(previousStageCacheKey, ctx.target, ctx.snapshot) +
            ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.debloat) +
            ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.customization),
    )

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

    override fun plan(ctx: StageContext): List<PipelineStep> = validate(ctx)?.let(::listOf) ?: assemblySteps(ctx)

    private fun assemblySteps(ctx: StageContext): List<PipelineStep> {
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

        for (b in ctx.target.bootPartitions) {
            val kernel = kernelSteps(ctx, b)
            steps += kernel
            if (kernel.last() is PipelineStep.Check) return steps
        }

        if (ctx.snapshot.assembly.baselineCleanup) {
            steps += PipelineStep.Tool(
                stepCommand("find", listOf(work, "-type", "d", "-name", "oat", "-exec", "rm", "-rf", "{}", "+"), ws),
                "purge oat dirs",
            )
            steps += PipelineStep.Tool(
                stepCommand(
                    "find",
                    // Symlinks too: framework/<arch>/boot-*.vdex point at framework/boot-*.vdex (LtiRom aot_purge.sh).
                    listOf(work, "(", "-type", "f", "-o", "-type", "l", ")", "(") +
                        AOT_NAME_ARGS +
                        listOf(")", "-delete"),
                    ws,
                ),
                "purge aot files",
            )
        }

        if (ctx.snapshot.assembly.disableDataEncryption) steps += dataEncryptionSteps(ctx)
        if (ctx.snapshot.assembly.removeIntegrityFlags) steps += integritySteps(ctx)

        val baseStamper = BuildPropStamper.stamper(ctx)
        val overrides = ctx.snapshot.assembly.propertyOverrides
        val transform: (String) -> String = { original ->
            val stamped = baseStamper(original)
            if (overrides.isEmpty()) stamped else applyPropertyOverrides(stamped, overrides)
        }
        steps += PipelineStep.EditFile(BUILD_PROP, transform, "stamp build.prop")
        return steps
    }

    /** Copies boot image [b] (stock, or the Studio's override checked by hash) into work/kernel/ (FR-024). */
    private fun kernelSteps(ctx: StageContext, b: String): List<PipelineStep> {
        val ws = ctx.wsPath
        val work = ctx.abs("work")
        val infoKey = RuntimeKeys.kernelInfo(b)
        val overrideSha = ctx.snapshot.assembly.kernelOverrides[b]
        val steps = mutableListOf<PipelineStep>()
        if (overrideSha == null) {
            steps += PipelineStep.Tool(
                stepCommand(
                    "cp",
                    listOf("-a", "${ctx.abs(FirmwareExtractionStage.EXTRACTED)}/$b.img", "$work/kernel/$b.img"),
                    ws,
                ),
                "cp kernel $b",
            )
        } else {
            // An image edited in the Studio (boot workbench): used only while it is the file the settings name.
            val shaKey = "kernel.$b.override.sha256"
            steps += PipelineStep.Tool(
                stepCommand("cp", listOf("-a", ctx.abs(KernelOverrides.path(b)), "$work/kernel/$b.img"), ws),
                "cp kernel override $b",
            )
            steps += PipelineStep.Tool(
                command = stepCommand("sha256sum", listOf("$work/kernel/$b.img"), ws),
                label = "hash kernel override $b",
                resultKey = shaKey,
            )
            val actual = ctx.value(shaKey)?.digestToken()
            if (actual != null && !actual.equals(overrideSha, ignoreCase = true)) {
                return steps + PipelineStep.Check("verify kernel override $b") {
                    "OVERRIDE_CHANGED: ${KernelOverrides.path(b)} is not the image saved in the Studio " +
                        "(expected $overrideSha, found $actual). Repack or import it again."
                }
            }
        }
        steps += PipelineStep.Tool(
            command = stepCommand("avbtool", listOf("info_image", "--image", "$work/kernel/$b.img"), ws),
            label = "avbtool info kernel $b",
            resultKey = infoKey,
            allowFailure = true,
        )
        val eraseFooter = ctx.snapshot.assembly.bootFooterPolicy.equals("erase", ignoreCase = true)
        if (eraseFooter && ctx.exitOf(infoKey) == 0) {
            steps += PipelineStep.Tool(
                stepCommand("avbtool", listOf("erase_footer", "--image", "$work/kernel/$b.img"), ws),
                "erase footer $b",
            )
        }
        return steps
    }

    /** Every fstab in work/ that mounts `/data` loses that line's encryption flags ([FstabEditor]). */
    private fun dataEncryptionSteps(ctx: StageContext): List<PipelineStep> {
        val find = PipelineStep.Tool(
            command = stepCommand("sh", listOf("-c", FIND_DATA_FSTABS, "sh", ctx.abs("work")), ctx.wsPath),
            label = "find /data fstabs",
            resultKey = DATA_FSTABS_KEY,
        )
        val files = ctx.value(DATA_FSTABS_KEY)?.lines()?.map(String::trim)?.filter(String::isNotEmpty)
        return listOf(find) + when {
            files == null -> emptyList()

            files.isEmpty() -> listOf(
                PipelineStep.Check("data encryption") {
                    "NO_DATA_FSTAB: no fstab in work/ mounts /data, so its encryption cannot be turned off. " +
                        "Turn the option off in Assemble (Mount table)."
                },
            )

            else -> files.map { rel ->
                PipelineStep.EditFile("work/$rel", FstabEditor::withoutDataEncryption, "decrypt /data in $rel")
            }
        }
    }

    /** Every fstab in work/ asking for AVB or dm-verity loses those flags ([FstabEditor.withoutIntegrity]). */
    private fun integritySteps(ctx: StageContext): List<PipelineStep> {
        val find = PipelineStep.Tool(
            command = stepCommand("sh", listOf("-c", FIND_VERIFIED_FSTABS, "sh", ctx.abs("work")), ctx.wsPath),
            label = "find verified fstabs",
            resultKey = VERIFIED_FSTABS_KEY,
        )
        val files = ctx.value(VERIFIED_FSTABS_KEY)?.lines()?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
        return listOf(find) + files.map { rel ->
            PipelineStep.EditFile("work/$rel", FstabEditor::withoutIntegrity, "remove integrity flags in $rel")
        }
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
        private const val DATA_FSTABS_KEY = "assembly.data.fstabs"

        /** `sh -c FIND_DATA_FSTABS sh <work>`: fstab files (relative to work/) with a line mounting `/data`. */
        private const val FIND_DATA_FSTABS =
            "cd \"\$1\" && grep -rlE '^[^#[:space:]]+[[:space:]]+/data[[:space:]]' --include='fstab*' . " +
                "| sed 's#^\\./##' || true"
        private const val VERIFIED_FSTABS_KEY = "assembly.verified.fstabs"

        /** `sh -c FIND_VERIFIED_FSTABS sh <work>`: fstab files (relative to work/) with an avb/verify fs_mgr flag. */
        private const val FIND_VERIFIED_FSTABS =
            "cd \"\$1\" && grep -rlE '[[:space:],](avb|avb_keys|verify|verifyatboot)([=,[:space:]]|$)' " +
                "--include='fstab*' . | sed 's#^\\./##' || true"
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
