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
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.FsTypes

/**
 * A dynamic partition packaged as shipped (`BuildSettings.keepStock`): its stock image is used instead of one built
 * from work/, so a build rebuilds only the partitions it changes. The stock image is copied, its vendor footer erased,
 * and it then goes through the same read-back verify, hash and workspace-key footer as a built image. Refused when the
 * partition's tree or sidecars differ from stock: those changes would silently not reach the phone.
 */
internal object KeptPartition {
    fun diffKey(p: String): String = "image.$p.kept.diff"

    fun fsKey(p: String): String = "image.$p.kept.fs"

    private const val SHOWN = 10

    /**
     * `sh -c UNCHANGED_SCRIPT sh <work tree> <stock tree> <work fs_config> <stock fs_config> <work file_context>
     * <stock file_context>`: the paths whose kind, size, time, mode or link target differ (at most 20), then a line
     * per sidecar that differs; nothing when the trees are the same. Assemble keeps the stock times, so an unchanged
     * file has the same time; a file rewritten in place to the same size gets a new one.
     */
    const val UNCHANGED_SCRIPT: String = """
        t=${'$'}(mktemp -d); trap 'rm -rf "${'$'}t"' EXIT
        (cd "$1" && find . -mindepth 1 -printf '%P\t%y\t%s\t%T@\t%m\t%l\n' | LC_ALL=C sort) > "${'$'}t/a"
        (cd "$2" && find . -mindepth 1 -printf '%P\t%y\t%s\t%T@\t%m\t%l\n' | LC_ALL=C sort) > "${'$'}t/b"
        diff "${'$'}t/a" "${'$'}t/b" | sed -n 's/^[<>] //p' | cut -f1 | LC_ALL=C sort -u | head -n 20
        cmp -s "$3" "$4" || echo "(owners and modes: fs_config)"
        cmp -s "$5" "$6" || echo "(SELinux labels: file_context)"
        exit 0
    """

    /** `sh -c ERASE_SCRIPT sh <avbtool> <image>`: the image's AVB footer and hashtree removed, when it has one. */
    const val ERASE_SCRIPT: String =
        "\"\$1\" info_image --image \"\$2\" >/dev/null 2>&1 || exit 0; \"\$1\" erase_footer --image \"\$2\""

    /** Adds [p]'s steps to [steps]; true: an image is in out/images for the steps that follow. */
    fun steps(ctx: StageContext, p: String, steps: MutableList<PipelineStep>): Boolean {
        val ws = ctx.wsPath
        val extracted = FirmwareExtractionStage.EXTRACTED
        val stock = ctx.abs("$extracted/$p.img")
        val img = ctx.abs("out/images/$p.img")
        steps += PipelineStep.Tool(
            command = stepCommand(
                "sh",
                listOf(
                    "-c", UNCHANGED_SCRIPT, "sh",
                    ctx.abs("work/$p"), ctx.abs("$extracted/$p"),
                    ctx.abs("work/configs/fs_config-$p"), ctx.abs("$extracted/fs_config-$p"),
                    ctx.abs("work/configs/file_context-$p"), ctx.abs("$extracted/file_context-$p"),
                ),
                ws,
            ),
            label = "compare $p with stock",
            resultKey = diffKey(p),
        )
        steps += PipelineStep.Check("$p unchanged from stock") { values ->
            values[diffKey(p)]?.lines()?.map(String::trim)?.filter(String::isNotEmpty)?.takeIf { it.isNotEmpty() }
                ?.let {
                    "KEPT_PARTITION_CHANGED: $p is kept as shipped, but work/$p differs from its stock image: " +
                        it.take(SHOWN).joinToString() + ". Build it instead (Build & Sign > Partitions), or undo " +
                        "those changes."
                }
        }
        steps += PipelineStep.Tool(
            command = stepCommand("xxd", listOf("-p", "-s", "1024", "-l", "64", stock), ws),
            label = "read stock $p superblock",
            resultKey = fsKey(p),
        )
        steps += PipelineStep.Tool(
            stepCommand("sh", listOf("-c", "rm -f \"\$2\" && cp --reflink=auto \"\$1\" \"\$2\"", "sh", stock, img), ws),
            "copy stock $p",
        )
        steps += PipelineStep.Tool(
            stepCommand("sh", listOf("-c", ERASE_SCRIPT, "sh", ctx.toolPaths["avbtool"] ?: "avbtool", img), ws),
            "erase stock footer $p",
        )
        return true
    }

    /** The filesystem of [p]'s image: the stock one's for a kept partition (once read), else the build's choice. */
    fun imageFilesystem(ctx: StageContext, p: String): String =
        ctx.value(fsKey(p))?.takeIf { p in ctx.snapshot.build.keepStock }
            ?.let { FsTypes.fromSuperblock(FormatDetector.fromHex(it.trim())).name.lowercase() }
            ?: BuildFilesystems.of(ctx.snapshot.build, p)
}
