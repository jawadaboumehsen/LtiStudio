/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import org.ide.lti.core.domain.pipeline.toolkit.FormatDetector
import org.ide.lti.core.domain.pipeline.toolkit.PackageFormat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

/** A firmware archive in the workspace folder; [ref] is what `AcquisitionSettings.archiveRef` stores. */
public data class FirmwareArchive(
    val ref: String,
    val name: String,
    val sizeBytes: Long,
    val detectedFormat: PackageFormat? = null,
)

/**
 * The firmware archives of a workspace, kept in `firmware/downloaded/` where the Acquire stage looks for them.
 * Importing copies the file inside WSL (from its `/mnt/<drive>` path), so a multi-GB archive never passes
 * through the app's memory.
 */
public class FirmwareArchives(
    private val workspaces: WorkspaceRepository,
    private val execution: PipelineExecutionPort,
    /** A Windows path as WSL sees it (`C:\a.zip` -> `/mnt/c/a.zip`). */
    private val toLinuxPath: (String) -> String,
) {
    /** The archives already in the workspace folder, by name. */
    public suspend fun list(workspaceId: String): List<FirmwareArchive> {
        val ws = workspace(workspaceId)
        val dir = "${root(ws)}/$DIR"
        run(ws, "mkdir", "-p", dir)
        val out = run(ws, "find", dir, "-maxdepth", "1", "-type", "f", "!", "-name", "*.partial", "-printf", LISTING)
        return out.lines().mapNotNull { line ->
            val (size, name) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            FirmwareArchive(ref = "$DIR/$name", name = name, sizeBytes = size.toLongOrNull() ?: 0L)
        }.sortedBy { it.name }.map { it.copy(detectedFormat = detect(ws, "$dir/${it.name}")) }
    }

    /** Copies the archive at [localPath] (a path on this computer) into the workspace folder. */
    public suspend fun import(workspaceId: String, localPath: String): FirmwareArchive {
        val ws = workspace(workspaceId)
        val source = toLinuxPath(localPath)
        val name = source.substringAfterLast('/')
        require(name.isNotBlank() && !name.endsWith(".partial")) { "Not a file: $localPath" }
        val dest = "${root(ws)}/$DIR/$name"
        run(ws, "mkdir", "-p", "${root(ws)}/$DIR")
        // Copy to a side name first: an interrupted copy never looks like a complete archive.
        run(ws, "cp", "-f", "--", source, "$dest.partial", timeoutMs = COPY_TIMEOUT_MS)
        run(ws, "mv", "-f", "--", "$dest.partial", dest)
        return FirmwareArchive(
            ref = "$DIR/$name",
            name = name,
            sizeBytes = execution.stat(ws, "$DIR/$name").sizeBytes,
            detectedFormat = detect(ws, dest),
        )
    }

    /** The container format from the file's first bytes (FR-001: content, never the name); null when unknown. */
    private suspend fun detect(ws: Workspace, path: String): PackageFormat? {
        val header = runCatching { run(ws, "xxd", "-p", "-l", HEADER_BYTES, path) }.getOrNull() ?: return null
        return FormatDetector.detect(FormatDetector.fromHex(header)).takeIf { it != PackageFormat.Unknown }
    }

    private suspend fun workspace(id: String): Workspace =
        checkNotNull(workspaces.getWorkspace(id)) { "Workspace $id not found" }

    private fun root(ws: Workspace) = ws.linuxPath ?: ws.path

    private suspend fun run(
        ws: Workspace,
        tool: String,
        vararg args: String,
        timeoutMs: Long = QUICK_TIMEOUT_MS,
    ): String {
        val result = execution.execute(ws, StepCommand(tool, args.toList(), root(ws), timeoutMs))
        check(result.exitCode == 0) { "$tool failed: ${result.stderr.ifBlank { result.stdout }.trim()}" }
        return result.stdout
    }

    public companion object {
        public const val DIR: String = "firmware/downloaded"
        private const val LISTING = "%s\\t%f\\n" // find -printf: size<TAB>name per file
        private const val QUICK_TIMEOUT_MS = 60_000L
        private const val HEADER_BYTES = "512"
        private const val COPY_TIMEOUT_MS = 3_600_000L
    }
}
