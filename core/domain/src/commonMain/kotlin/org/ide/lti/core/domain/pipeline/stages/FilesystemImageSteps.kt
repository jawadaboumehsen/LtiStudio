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

import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.ext4MeasureKey
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.ext4TrialKey
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.f2fsPrepareKey
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage.Companion.f2fsTrialKey
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.toolkit.Ext4Image
import org.ide.lti.core.domain.pipeline.toolkit.F2fsImage

/** The Build stage's ext4 and F2FS image steps; EROFS is one mkfs.erofs call in the stage itself. */
internal object FilesystemImageSteps {
    private const val MIB = 1024L * 1024
    private const val INODES_PER_MIB = 64

    /**
     * [p] as a read-only ext4 image (`Ext4Image`): measured, built once to learn its size, then built at that
     * size and checked with e2fsck, into [steps]. False while the measure or the trial has not run yet.
     */
    fun ext4(ctx: StageContext, p: String, steps: MutableList<PipelineStep>): Boolean {
        val ws = ctx.wsPath
        val img = ctx.abs("out/images/$p.img")
        val dir = ctx.abs("out/ext4/$p")
        val tree = ctx.abs("work/$p")
        val mountPoint = if (p == "system") "/" else "/$p"
        val blockSize = ctx.snapshot.build.blockSize
        val trial = "$dir/trial.img"
        steps += listOf(
            PipelineStep.Tool(stepCommand("mkdir", listOf("-p", dir), ws), "mkdir ext4 $p"),
            PipelineStep.Tool(
                stepCommand(
                    "sh",
                    listOf(
                        "-c",
                        Ext4Image.SIDECARS_SCRIPT,
                        "sh",
                        ctx.abs("work/configs/fs_config-$p"),
                        ctx.abs("work/configs/file_context-$p"),
                        dir,
                        p,
                    ),
                    ws,
                ),
                "ext4 sidecars $p",
            ),
            PipelineStep.Tool(
                stepCommand("sh", listOf("-c", Ext4Image.MEASURE_SCRIPT, "sh", tree), ws),
                "measure $p",
                ext4MeasureKey(p),
            ),
        )
        val measure = ctx.value(ext4MeasureKey(p))?.let(Ext4Image::parseMeasure) ?: return false
        val trialBlocks = Ext4Image.trialBlocks(measure, blockSize)
        steps += PipelineStep.Tool(stepCommand("rm", listOf("-f", trial, img), ws), "clear ext4 $p")
        steps += PipelineStep.Tool(
            stepCommand(
                "mke2fs",
                Ext4Image.mke2fsArgs(p, mountPoint, blockSize, measure.inodes, trial, trialBlocks),
                ws,
            ),
            "mke2fs trial $p",
        )
        steps += PipelineStep.Tool(
            stepCommand("e2fsdroid", Ext4Image.e2fsdroidArgs(mountPoint, dir, tree, trial), ws),
            "e2fsdroid trial $p",
        )
        steps += PipelineStep.Tool(
            stepCommand("dumpe2fs", listOf("-h", trial), ws),
            "dumpe2fs trial $p",
            ext4TrialKey(p),
        )
        val free = ctx.value(ext4TrialKey(p))?.let(Ext4Image::parseFreeBlocks)
        val content = free?.let { Ext4Image.finalBlocks(trialBlocks, it) }
        val fixed = ctx.snapshot.build.partitionSizeBytes[p]?.div(blockSize)
        if (content != null && fixed != null && fixed < content) {
            steps += tooSmall(p, "ext4", fixed * blockSize, content * blockSize)
        } else if (content != null) {
            // Free space asked for (or a fixed size), with inodes to match (one per 16 KiB), so files can be added.
            val freeMib = ctx.snapshot.build.partitionFreeMib[p] ?: 0
            val blocks = fixed ?: (content + freeMib * MIB / blockSize)
            val inodes = measure.inodes + (blocks - content) * blockSize / MIB * INODES_PER_MIB
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-f", trial), ws), "drop ext4 trial $p")
            steps += PipelineStep.Tool(
                stepCommand("mke2fs", Ext4Image.mke2fsArgs(p, mountPoint, blockSize, inodes, img, blocks), ws),
                "mke2fs $p",
            )
            steps += PipelineStep.Tool(
                stepCommand("e2fsdroid", Ext4Image.e2fsdroidArgs(mountPoint, dir, tree, img), ws),
                "e2fsdroid $p",
            )
            // The filesystem's own consistency check, read-only (-n): a corrupt image is caught here.
            steps += PipelineStep.Tool(stepCommand("e2fsck", listOf("-fn", img), ws), "e2fsck $p")
        }
        return free != null
    }

    /**
     * [p] as a read-only F2FS image (`F2fsImage`): a generous trial tells sload's max image size, then it is built
     * at that size and checked with fsck.f2fs, into [steps]. False while the prepare or the trial has not run yet.
     */
    fun f2fs(ctx: StageContext, p: String, steps: MutableList<PipelineStep>): Boolean {
        val ws = ctx.wsPath
        val img = ctx.abs("out/images/$p.img")
        val dir = ctx.abs("out/f2fs/$p")
        val tree = ctx.abs("work/$p")
        val fsConfig = ctx.abs("work/configs/fs_config-$p")
        val mountPoint = if (p == "system") "/" else "/$p"
        val trial = "$dir/trial.img"
        steps += PipelineStep.Tool(stepCommand("mkdir", listOf("-p", dir), ws), "mkdir f2fs $p")
        steps += PipelineStep.Tool(
            stepCommand(
                "sh",
                listOf(
                    "-c",
                    F2fsImage.PREPARE_SCRIPT,
                    "sh",
                    fsConfig,
                    ctx.abs("work/configs/file_context-$p"),
                    dir,
                    p,
                    tree,
                ),
                ws,
            ),
            "f2fs prepare $p",
            f2fsPrepareKey(p),
        )
        val prepared = ctx.value(f2fsPrepareKey(p))?.let(F2fsImage::parsePrepare) ?: return false
        fun build(bytes: Long, target: String, label: String, resultKey: String? = null) {
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-f", target), ws), "clear $label")
            steps += PipelineStep.Tool(
                stepCommand("truncate", listOf("-s", bytes.toString(), target), ws),
                "size $label",
            )
            steps += PipelineStep.Tool(
                stepCommand("make_f2fs", F2fsImage.makeArgs(prepared.rootOwner, target), ws),
                "make_f2fs $label",
            )
            steps += PipelineStep.Tool(
                stepCommand(
                    "sload_f2fs",
                    F2fsImage.sloadArgs(fsConfig, "$dir/file_contexts", mountPoint, tree, target),
                    ws,
                ),
                "sload_f2fs $label",
                resultKey,
            )
        }
        build(F2fsImage.trialBytes(prepared.measure), trial, "trial $p", f2fsTrialKey(p))
        val maxMib = ctx.value(f2fsTrialKey(p))?.let(F2fsImage::parseMaxImageMib)
        val content = maxMib?.let(F2fsImage::finalBytes)
        val fixed = ctx.snapshot.build.partitionSizeBytes[p]
        if (content != null && fixed != null && fixed < content) {
            steps += tooSmall(p, "F2FS", fixed, content)
        } else if (content != null) {
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-f", trial), ws), "drop f2fs trial $p")
            build(fixed ?: content, img, p)
            // The filesystem's own consistency check, without fixing anything: a corrupt image is caught here.
            steps += PipelineStep.Tool(stepCommand("fsck.f2fs", listOf("--dry-run", "-f", img), ws), "fsck.f2fs $p")
        }
        return maxMib != null
    }

    /** A fixed size ([org.ide.lti.core.model.workspace.BuildSettings.partitionSizeBytes]) the content does not fit. */
    private fun tooSmall(p: String, fs: String, fixedBytes: Long, neededBytes: Long) =
        PipelineStep.Check("partition size $p") {
            "PARTITION_TOO_SMALL: $p needs ${neededBytes.ceilKib()} KiB as $fs, but its size is set to " +
                "${fixedBytes / KIB} KiB. Raise it in Build & Sign > Compression, or set it back to automatic."
        }

    private const val KIB = 1024L

    private fun Long.ceilKib(): Long = (this + KIB - 1) / KIB
}
