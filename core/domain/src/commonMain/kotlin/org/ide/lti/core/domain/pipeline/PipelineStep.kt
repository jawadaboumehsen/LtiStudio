/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

/**
 * One unit of work in a stage plan. Plans are pure functions of [StageContext]; every value that
 * is only known at run time (a checksum, an image size, a probe result) enters the context through
 * a step's `resultKey`, after which the orchestrator re-plans the stage and continues.
 */
public sealed interface PipelineStep {
    /** Stable, human-readable identity used in run events and for prefix stability checks. */
    public val label: String

    /**
     * A single tool invocation executed as a durable run on the execution service.
     *
     * @property resultKey when set, the step's trimmed stdout is stored under this key in
     *   `runtimeValues` (and `"$resultKey.exit"` receives the exit code).
     * @property allowFailure when true a non-zero exit does not fail the stage; the caller reads
     *   `"$resultKey.exit"` on re-plan to branch.
     * @property expectStdoutContains every entry must appear in stdout, otherwise the stage fails.
     */
    public data class Tool(
        val command: StepCommand,
        override val label: String,
        val resultKey: String? = null,
        val allowFailure: Boolean = false,
        val expectStdoutContains: List<String> = emptyList(),
    ) : PipelineStep

    /** Writes Kotlin-generated content to `<workspace>/<relPath>` through the binary upload API. */
    public class WriteFile(
        val relPath: String,
        val content: ByteArray,
        override val label: String = "write $relPath",
    ) : PipelineStep

    /**
     * Writes a product-vendored binary (e.g. the recovery `update-binary`) resolved through
     * [org.ide.lti.core.domain.ports.VendoredFilePort]; the stage fails when it is absent or its
     * pinned digest does not match — nothing is ever substituted.
     */
    public data class WriteVendoredFile(
        val relPath: String,
        val resourceId: String,
        override val label: String = "write vendored $resourceId",
    ) : PipelineStep

    /** Reads `<workspace>/<relPath>`, applies [transform], and writes it back. */
    public class EditFile(
        val relPath: String,
        val transform: (String) -> String,
        override val label: String = "edit $relPath",
    ) : PipelineStep

    /**
     * Binary search for the smallest AVB partition size whose `--calc_max_image_size` fits the
     * image (contract Stage 5 step 3). Each probe is one `avbtool` call; the result is stored
     * under [resultKey].
     */
    public data class AvbSizeSearch(
        val imageSizeBytes: Long,
        val resultKey: String,
        override val label: String,
    ) : PipelineStep

    /** Pure assertion over the current runtime values; returns a failure message or null. */
    public class Check(
        override val label: String,
        val verify: (Map<String, String>) -> String?,
    ) : PipelineStep
}

/** Result of a synchronous (non-durable) tool call used for quick queries such as `stat`. */
public data class ToolResult(val exitCode: Int, val stdout: String, val stderr: String = "")

/** Naming helpers for the runtime-value keys shared between stages and the orchestrator. */
public object RuntimeKeys {
    public const val RUN_DATE: String = "run.date"
    public const val RUN_TIMESTAMP: String = "run.timestamp"
    public const val ARCHIVE_MIME: String = "firmware.archive.mime"
    public const val ARCHIVE_SHA256: String = "firmware.archive.sha256"
    public const val ARCHIVE_CHECKSUM_STATE: String = "firmware.archive.checksum"
    public const val DOWNLOAD_RESUME: String = "firmware.download.resume"
    public const val SUPER_MAGIC: String = "firmware.super.magic"
    public const val BUILD_PROP: String = "firmware.build.prop"
    public const val AVB_PUBKEY_SHA1: String = "keys.avb.pubkey.sha1"
    public const val ZIP_SHA256: String = "package.zip.sha256"
    public const val ZIP_SIZE: String = "package.zip.size"
    public const val ZIP_LISTING: String = "package.zip.listing"

    public fun statLines(partition: String): String = "sidecar.$partition.stat"
    public fun selinuxDump(partition: String): String = "sidecar.$partition.selinux"
    public fun bootInfo(image: String): String = "boot.$image.info"
    public fun bootSize(image: String): String = "boot.$image.size"
    public fun kernelInfo(image: String): String = "kernel.$image.info"
    public fun imageSize(partition: String): String = "image.$partition.size"
    public fun imageSha256(partition: String): String = "image.$partition.sha256"
    public fun partitionSize(partition: String): String = "image.$partition.partitionSize"
    public fun footerInfo(partition: String): String = "image.$partition.info"

    /** Exit code companion of a [PipelineStep.Tool] result key. */
    public fun exitOf(resultKey: String): String = "$resultKey.exit"
}

/** First whitespace-separated token of a `sha256sum`/`sha1sum` line. */
public fun String.digestToken(): String = trim().split(Regex("\\s+")).firstOrNull().orEmpty()
