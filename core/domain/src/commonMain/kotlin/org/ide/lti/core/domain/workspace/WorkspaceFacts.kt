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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace

/** A file in the workspace folder, [path] relative to the directory it was listed from. */
public data class WorkspaceFile(val path: String, val sizeBytes: Long)

/**
 * What the build actually produced, for the stage editors: values the last runs captured (measured
 * hashes, sizes) and the files in the workspace folder. Every read is best effort: an unreachable
 * service or a missing file gives null / empty, never sample data.
 */
public class WorkspaceFacts(
    private val workspaces: WorkspaceRepository,
    private val files: PipelineExecutionPort,
    private val runs: RunRepository,
) {
    /** The newest value a run of [workspaceId] captured under [key], or null when no run captured it. */
    public suspend fun lastValue(workspaceId: String, key: String): String? = attempt {
        runs.observeRuns(workspaceId).first()
            .sortedByDescending { it.startedAt }
            .firstNotNullOfOrNull { it.runtimeValues[key] }
    }

    public suspend fun stat(workspaceId: String, relPath: String): FileStat? =
        attempt { files.stat(workspace(workspaceId), relPath).takeIf { it.exists } }

    public suspend fun read(workspaceId: String, relPath: String): String? =
        attempt { files.readFile(workspace(workspaceId), relPath) }

    /**
     * Files under [relDir] (down to [maxDepth] levels), optionally only names matching the shell pattern
     * [name]. Empty when the directory does not exist yet (its stage has not run).
     */
    public suspend fun list(
        workspaceId: String,
        relDir: String,
        maxDepth: Int = 1,
        name: String? = null,
    ): List<WorkspaceFile> = attempt {
        val ws = workspace(workspaceId)
        val base = ws.linuxPath ?: ws.path
        val root = "$base/$relDir"
        val args = listOf(root, "-mindepth", "1", "-maxdepth", maxDepth.toString(), "-type", "f") +
            (name?.let { listOf("-name", it) } ?: emptyList()) + listOf("-printf", "%s\\t%P\\n")
        val result = files.execute(ws, StepCommand("find", args, base, LIST_TIMEOUT_MS))
        if (result.exitCode != 0) return@attempt emptyList()
        result.stdout.lines().mapNotNull { line ->
            val (size, path) = line.split('\t', limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            WorkspaceFile(path, size.toLongOrNull() ?: 0L)
        }.sortedBy { it.path }
    }.orEmpty()

    /** Member names of the zip at [relPath] in the workspace, or empty when it does not exist. */
    public suspend fun zipMembers(workspaceId: String, relPath: String): List<String> = attempt {
        val ws = workspace(workspaceId)
        val base = ws.linuxPath ?: ws.path
        val result = files.execute(ws, StepCommand("unzip", listOf("-Z1", "$base/$relPath"), base, LIST_TIMEOUT_MS))
        if (result.exitCode == 0) result.stdout.lines().filter { it.isNotBlank() } else emptyList()
    }.orEmpty()

    private suspend fun workspace(id: String): Workspace =
        checkNotNull(workspaces.getWorkspace(id)) { "Workspace $id not found" }

    private suspend fun <T> attempt(block: suspend () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        co.touchlab.kermit.Logger.w(e) { "Workspace facts unavailable" }
        null
    }

    private companion object {
        const val LIST_TIMEOUT_MS = 60_000L
    }
}
