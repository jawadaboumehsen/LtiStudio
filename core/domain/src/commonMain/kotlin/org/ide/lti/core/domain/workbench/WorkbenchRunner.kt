/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.pipeline.toolkit.AckStore
import org.ide.lti.core.domain.pipeline.toolkit.JobLock
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.pipeline.toolkit.Refusal
import org.ide.lti.core.domain.pipeline.toolkit.RefusalCatalog
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.runToEnd
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.workspace.Workspace

/**
 * Runs the interactive ROM tools (boot, images, content, restore) in a workspace's WSL folder through the
 * build service. One editing job per workspace at a time (FR-039); every command is cancellable (FR-036) and
 * every failure is a [Refusal] naming cause, item and next step (FR-037).
 */
public class WorkbenchRunner(
    private val workspaces: WorkspaceRepository,
    private val port: PipelineExecutionPort,
    private val lock: JobLock,
    private val restorePoints: RestorePointStore,
    private val acknowledgements: AckStore,
    /** A workbench job never changes a workspace while one of its builds is queued or running. */
    private val runs: RunRepository? = null,
) {
    /** Whether [feature] (a [RiskyFeature]) was acknowledged in [workspaceId]; asked once per workspace (FR-035). */
    public suspend fun isAcknowledged(workspaceId: String, feature: String): Boolean =
        acknowledgements.isAcknowledged(workspace(workspaceId), feature)

    public suspend fun acknowledge(workspaceId: String, feature: String) {
        acknowledgements.acknowledge(workspace(workspaceId), feature, Clock.System.now().toString())
    }

    private suspend fun workspace(id: String): Workspace =
        checkNotNull(workspaces.getWorkspace(id)) { "Workspace $id not found" }

    /** Runs [block] holding [workspaceId]'s job lock as [jobName]; refuses when another job holds it. */
    public suspend fun <T> job(workspaceId: String, jobName: String, block: suspend Job.() -> T): T {
        val ws = workspace(workspaceId)
        return when (val acquired = lock.acquire(ws, jobName)) {
            is LockResult.Locked -> throw RefusalException(RefusalCatalog.workspaceLocked(holder = acquired.holderName))

            is LockResult.Acquired -> try {
                runs?.latestActive(workspaceId)?.let { run ->
                    throw RefusalException(
                        Refusal(
                            "A build is running in this workspace.",
                            run.id,
                            "Wait for it to finish, or cancel it, then try again.",
                        ),
                    )
                }
                Job(ws).block()
            } finally {
                // Released even when the job was cancelled: a cancelled coroutine cannot run the remote delete.
                withContext(NonCancellable) { acquired.handle.release() }
            }
        }
    }

    /** Reads without taking the job lock (listing, viewing). */
    public suspend fun <T> read(workspaceId: String, block: suspend Job.() -> T): T =
        Job(workspace(workspaceId)).block()

    public inner class Job internal constructor(public val ws: Workspace) {
        public val root: String = checkNotNull(ws.linuxPath) { "workspace ${ws.id} has no WSL folder" }

        public fun abs(rel: String): String = if (rel.startsWith("/")) rel else "$root/$rel"

        /** Runs [tool] to the end (cancelling the job stops it); refuses with its error output on failure. */
        public suspend fun run(tool: String, vararg args: String): String {
            val result = tryRun(tool, *args)
            if (result.exitCode != 0) {
                throw RefusalException(
                    Refusal(
                        cause = "$tool failed (exit ${result.exitCode}).",
                        item = args.lastOrNull { it.startsWith("/") }?.removePrefix("$root/") ?: tool,
                        nextStep = "Check the details, fix the input and try again.",
                        details = result.stderr.ifBlank { result.stdout }.trim().takeLast(DETAILS_LIMIT),
                    ),
                )
            }
            return result.stdout
        }

        public suspend fun tryRun(tool: String, vararg args: String): ToolResult =
            port.runToEnd(ws, tool, args.toList())

        /** Runs a POSIX shell [script] whose inputs are passed as `$1..$n` ([args]), never spliced into it. */
        public suspend fun sh(script: String, vararg args: String): String = run("sh", "-c", script, "sh", *args)

        /** [rel] as Windows sees it (`\\wsl.localhost\<distro>\…`), for opening in Explorer; null without a distro. */
        public fun hostPath(rel: String): String? = ws.environmentId?.distro?.let { distro ->
            "\\\\wsl.localhost\\$distro\\" + abs(rel).trim('/').replace('/', '\\')
        }

        /**
         * The executable the build service runs for [toolId] (its `~/.ltirom/tools.d/<id>.json` manifest), for a
         * shell script that needs the tool or a file shipped beside it.
         */
        public suspend fun toolPath(toolId: String): String =
            sh(TOOL_PATH_SCRIPT, toolId).trim().ifEmpty { throw RefusalException(RefusalCatalog.toolMissing(toolId)) }

        public suspend fun exists(rel: String): Boolean = port.stat(ws, rel).exists

        public suspend fun readText(rel: String): String? = port.readFile(ws, rel)

        public suspend fun writeText(rel: String, text: String) {
            check(port.uploadFile(ws, rel, text.encodeToByteArray())) { "Could not write $rel" }
        }

        /**
         * Copies [itemRel] (file or folder) to `restore/<tool>/<stamp>/<itemRel>` and records it in the workspace
         * restore index before a destructive change (FR-038).
         */
        public suspend fun backup(tool: String, itemRel: String): RestorePoint {
            val now = Clock.System.now()
            val backupRel = "restore/$tool/${now.toEpochMilliseconds()}/$itemRel"
            // An item the tool is about to create has nothing to copy: its point records that it was added.
            val added = !exists(itemRel)
            if (!added) {
                sh("mkdir -p \"$(dirname \"$2\")\" && cp -a -T \"$1\" \"$2\"", abs(itemRel), abs(backupRel))
            }
            val point = RestorePoint(
                id = "$tool:${now.toEpochMilliseconds()}:$itemRel",
                tool = tool,
                itemPath = itemRel,
                createdAt = now.toString(),
                backupPath = backupRel,
                added = added,
            )
            restorePoints.addRestorePoint(ws, point)
            return point
        }
    }

    private companion object {
        const val DETAILS_LIMIT = 4_000
        const val TOOL_PATH_SCRIPT =
            """f="${'$'}HOME/.ltirom/tools.d/${'$'}1.json"; [ -f "${'$'}f" ] && sed -n 's/.*"executable" *: *"\([^"]*\)".*/\1/p' "${'$'}f" || true"""
    }
}

/** Features that change what the device boots and so are acknowledged before their first use in a workspace. */
public object RiskyFeature {
    public const val ROOT_PATCH: String = "root-patch"
    public const val VBMETA_FLAGS: String = "vbmeta-flags"
    public const val KERNEL_OVERRIDE: String = "kernel-override"
    public const val LIBRARY_PATCH: String = "library-patch"
    public const val PACKAGE_REBUILD: String = "package-rebuild"
}
