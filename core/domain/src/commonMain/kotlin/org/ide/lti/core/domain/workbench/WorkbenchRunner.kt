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

import kotlinx.coroutines.CancellationException
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
import org.ide.lti.core.domain.ports.linkInWorkspace
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
    /** The active WSL distribution: a workspace found in the work folder records none of its own. */
    private val distro: () -> String? = { null },
    /** Where every job's start, end and reported lines go (the Studio's Output panel). */
    public val log: WorkbenchLog = WorkbenchLog(),
) {
    /** Whether [feature] (a [RiskyFeature]) was acknowledged in [workspaceId]; asked once per workspace (FR-035). */
    public suspend fun isAcknowledged(workspaceId: String, feature: String): Boolean =
        acknowledgements.isAcknowledged(workspace(workspaceId), feature)

    public suspend fun acknowledge(workspaceId: String, feature: String) {
        acknowledgements.acknowledge(workspace(workspaceId), feature, Clock.System.now().toString())
    }

    private suspend fun workspace(id: String): Workspace =
        checkNotNull(workspaces.getWorkspace(id)) { "Workspace $id not found" }

    /**
     * Runs [block] holding [workspaceId]'s job lock as [jobName]; refuses when another job holds it. Also refused
     * while the workspace holds a link where the tools work ([checkLinks]: only freeing a folder goes without).
     */
    public suspend fun <T> job(
        workspaceId: String,
        jobName: String,
        checkLinks: Boolean = true,
        block: suspend Job.() -> T,
    ): T {
        val ws = workspace(workspaceId)
        return when (val acquired = lock.acquire(ws, jobName)) {
            is LockResult.Locked -> throw RefusalException(RefusalCatalog.workspaceLocked(holder = acquired.holderName))

            is LockResult.Acquired -> try {
                log.add(LogLevel.INF, "$jobName: started in ${ws.name}")
                refuseDuringBuild(workspaceId)
                val job = Job(ws)
                if (checkLinks) job.requireNoLinks()
                job.block().also { log.add(LogLevel.OK, "$jobName: done") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                when (e) {
                    is CancellationException -> log.add(LogLevel.WRN, "$jobName: cancelled")
                    is RefusalException -> log.add(LogLevel.ERR, "$jobName: ${e.refusal.cause} (${e.refusal.item})")
                    else -> log.add(LogLevel.ERR, "$jobName: ${e.message ?: e::class.simpleName}")
                }
                throw e
            } finally {
                // Released even when the job was cancelled: a cancelled coroutine cannot run the remote delete.
                withContext(NonCancellable) { acquired.handle.release() }
            }
        }
    }

    /**
     * Reads without the job lock or the service's workspace lock (listing, viewing): a build's next step must never
     * find the workspace held by a page that only looks at it. Its commands are refused while a build runs.
     */
    public suspend fun <T> read(workspaceId: String, block: suspend Job.() -> T): T =
        Job(workspace(workspaceId), exclusive = false).block()

    private suspend fun refuseDuringBuild(workspaceId: String) {
        runs?.latestActive(workspaceId)?.let { throw RefusalException(RefusalCatalog.buildRunning(it.id)) }
    }

    public inner class Job internal constructor(public val ws: Workspace, private val exclusive: Boolean = true) {
        public val root: String = checkNotNull(ws.linuxPath) { "workspace ${ws.id} has no WSL folder" }

        public fun abs(rel: String): String = if (rel.startsWith("/")) rel else "$root/$rel"

        /** A line of this job's own in the Output panel. */
        public fun report(level: LogLevel, text: String) {
            log.add(level, text)
        }

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

        public suspend fun tryRun(tool: String, vararg args: String): ToolResult {
            if (!exclusive) refuseDuringBuild(ws.id)
            return port.runToEnd(ws, tool, args.toList(), exclusive)
        }

        /**
         * Whether the folder holding [rel], with every link on its way followed, lies inside [base] (workspace-
         * relative, "" for the workspace), and [base], resolved the same way, inside the workspace: a link at the
         * base would otherwise move both out together. [rel] itself may be a link, which this does not judge: the
         * caller replaces it (`rm` first, or `mv -T`), never writes through it. Folders that do not exist yet are
         * judged by the part that does.
         */
        public suspend fun folderResolvesInside(rel: String, base: String): Boolean = tryRun(
            "sh",
            "-c",
            "r=$(realpath -m -- \"$(dirname -- \"$1\")\") && w=$(realpath -m -- \"$2\") && " +
                "g=$(realpath -m -- \"$3\") && case \"\$w/\" in \"\$g\"/*) ;; *) exit 1;; esac && " +
                "case \"\$r/\" in \"\$w\"/*) ;; *) exit 1;; esac",
            "sh",
            abs(rel),
            abs(base).trimEnd('/'),
            root,
        ).exitCode == 0

        /**
         * The tools clear and write `workbench/` freely, so it must hold only their own folders and files: a link
         * there would take an `rm -rf` or a redirect out of the workspace. Checked once for every tool, before any
         * of them runs. Ramdisk and APEX contents are firmware, where links are normal; they are read or replaced
         * as files (`find -type f`), never written through. The rest of the workspace is [linkInWorkspace]'s.
         */
        internal suspend fun requireNoLinks() {
            val link = scratchLink() ?: port.linkInWorkspace(ws) ?: return
            throw RefusalException(
                Refusal(
                    "The workspace holds a link where the tools work, which could lead them out of it.",
                    link.removePrefix("$root/"),
                    "Remove it, or free the folder in Workspace > Disk usage; nothing was changed.",
                ),
            )
        }

        private suspend fun scratchLink(): String? {
            if (!exists(SCRATCH)) return null
            val scan = tryRun(
                "find",
                abs(SCRATCH),
                "(",
                "-path",
                "*/$SCRATCH/boot/*/ramdisk/*",
                "-o",
                "-path",
                "*/$SCRATCH/apex/*/*",
                ")",
                "-prune",
                "-o",
                "-type",
                "l",
                "-print",
            )
            check(scan.exitCode == 0) { "The scratch folder could not be checked for links: ${scan.stderr.trim()}" }
            return scan.stdout.lineSequence().firstOrNull(String::isNotBlank)
        }

        /** Refuses a write to [rel] unless [folderResolvesInside]. */
        public suspend fun requireFolderInside(rel: String, base: String) {
            if (!folderResolvesInside(rel, base)) {
                throw RefusalException(
                    Refusal(
                        "This path leaves the workspace through a link.",
                        rel,
                        "Remove the link and try again; nothing was changed.",
                    ),
                )
            }
        }

        /** Runs a POSIX shell [script] whose inputs are passed as `$1..$n` ([args]), never spliced into it. */
        public suspend fun sh(script: String, vararg args: String): String = run("sh", "-c", script, "sh", *args)

        /** [rel] as Windows sees it (`\\wsl.localhost\<distro>\…`), for opening in Explorer; null without a distro. */
        public fun hostPath(rel: String): String? = (ws.environmentId?.distro ?: distro())?.let { name ->
            "\\\\wsl.localhost\\$name\\" + abs(rel).trim('/').replace('/', '\\')
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
        const val SCRATCH = "workbench"
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
