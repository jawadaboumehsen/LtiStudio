/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.data.setup.install.GitCache
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource

/**
 * Dedicated engine for synchronizing external git submodules.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages git clone, checkout, and verification
 *   for external tool repositories.
 * - Incremental Execution & Durable Runs: Consults [ToolchainSetupRepository] and local filesystem
 *   to skip redundant clones. When [RemoteTransportPort] is provided, executes clone operations as
 *   durable runs through [JournaledRunLauncher]: every run is journaled under environment/attempt/child
 *   identity before submission (no static idempotency keys), and an unjournaled durable run is refused.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class SubmoduleSyncEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
    private val transport: RemoteTransportPort? = null,
) {
    public data class PinnedSubmodule(val name: String, val url: String, val commit: String)

    public companion object {
        /**
         * Real upstream commits (verified to exist): android-tools = `sixteen` head; erofs = the
         * erofs-tools release tag v1.8.10-251217, the last layout with build/cmake and fuse.erofs (`main`
         * was restructured and no longer builds a fuse binary). Kept identical to
         * WslToolchainProvisioner.SUBMODULES (tested).
         */
        public val DEFAULT_SUBMODULES: List<PinnedSubmodule>
            get() = ToolGroupCatalog.allGroups.mapNotNull { group ->
                val git = group.source as? ToolSource.Git ?: return@mapNotNull null
                PinnedSubmodule(
                    name = group.id.value,
                    url = git.recommendedUrl,
                    commit = git.recommendedCommit,
                )
            }

        /** A recursive clone of android-tools (with its vendor submodules) takes minutes. */
        private const val CLONE_TIMEOUT_SECONDS: Long = 1_200
    }

    /** What is on disk where a source repository should be. */
    private enum class CloneState { MISSING, AT_PIN, OTHER_COMMIT, NOT_A_CLONE, ORIGIN_MISMATCH }

    /**
     * Synchronizes all required source repositories into [extDir], each its own clone at its pinned commit
     * with its nested submodules at the versions that commit records.
     *
     * "Synced" is measured, never taken from the journal: a directory counts only when it is its own git
     * clone whose HEAD is the pin. A directory that is not its own clone (e.g. part of a larger imported
     * repository) is moved aside - never deleted - and cloned fresh.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount")
    public suspend fun syncAll(
        distro: String,
        extDir: String,
        submodules: List<PinnedSubmodule> = DEFAULT_SUBMODULES,
        journal: SetupJournalContext? = null,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        onProgress: (currentItem: String, index: Int, total: Int) -> Unit = { _, _, _ -> },
        log: (String) -> Unit = {},
    ): Result<Unit> {
        cli.execute(distro, listOf("mkdir", "-p", extDir))

        for ((index, sub) in submodules.withIndex()) {
            onProgress(sub.name, index + 1, submodules.size)
            val subDir = "$extDir/${sub.name}"
            var state = cloneState(distro, subDir, sub.url, sub.commit)

            if (state == CloneState.NOT_A_CLONE || state == CloneState.ORIGIN_MISMATCH) {
                val reasonTag = if (state == CloneState.ORIGIN_MISMATCH) "origin-mismatch" else "not-a-clone"
                val aside = "$subDir.$reasonTag-${System.currentTimeMillis()}"
                val logMsg = if (state == CloneState.ORIGIN_MISMATCH) {
                    "'$subDir' origin does not match ${sub.url}"
                } else {
                    "'$subDir' is not its own git clone"
                }
                log("[Step 4/5] $logMsg; moving it to $aside and cloning fresh.")
                val moved = cli.execute(distro, listOf("mv", subDir, aside))
                if (moved.exitCode != 0) {
                    return Result.failure(
                        IllegalStateException("Could not move aside $subDir: ${moved.error.ifBlank { moved.output }}"),
                    )
                }
                state = CloneState.MISSING
            }
            val ctx = GitRun(distro, sub, journal, onRunStarted, log)

            if (state == CloneState.MISSING) {
                log("[Step 4/5] Cloning submodule '${sub.name}' from ${sub.url}...")
                gitLong(ctx, "clone", listOf("clone", "--recursive", sub.url, subDir), extDir)?.let {
                    return Result.failure(IllegalStateException("Failed to clone ${sub.name}: $it"))
                }
            }
            if (state == CloneState.AT_PIN) {
                log("[Step 4/5] '${sub.name}' is a clone at its pinned commit ${sub.commit.take(8)}.")
            } else {
                var checkoutRes = cli.execute(distro, listOf("git", "-C", subDir, "checkout", "-f", sub.commit))
                if (checkoutRes.exitCode != 0) {
                    log("[Step 4/5] Pinned commit not present locally for '${sub.name}'; fetching...")
                    gitLong(ctx, "fetch", listOf("-C", subDir, "fetch", "--all"), subDir)
                    checkoutRes = cli.execute(distro, listOf("git", "-C", subDir, "checkout", "-f", sub.commit))
                }
                if (checkoutRes.exitCode != 0) {
                    val err = "Failed to check out ${sub.name} at ${sub.commit}: " +
                        checkoutRes.error.ifBlank { checkoutRes.output }
                    return Result.failure(IllegalStateException(err))
                }
                log("[Step 4/5] Checked out '${sub.name}' at pinned commit ${sub.commit.take(8)}.")
            }
            // Always, also for a clone already at its pin (a sync cut off here leaves the pin checked out but
            // the nested submodules incomplete): bring them to the versions the pin records. A no-op when they
            // already are. Long (erofs-tools pulls 11 repositories), so it runs as a durable run.
            gitLong(
                ctx,
                "submodules",
                listOf("-C", subDir, "submodule", "update", "--init", "--recursive", "--force"),
                subDir,
            )
                ?.let {
                    return Result.failure(IllegalStateException("Failed to update the submodules of ${sub.name}: $it"))
                }
            repository?.updateSubmodule(sub.name) { it.copy(isSynced = true, commit = sub.commit) }
            if (state != CloneState.AT_PIN) {
                // The sources changed: tools built from the previous ones are stale and must be compiled again.
                for (toolId in ToolCapabilities.toolsForRecipeGroup(sub.name)) {
                    repository?.updateTool(toolId) { it.copy(isCompiled = false, isVerified = false) }
                }
            }
        }

        repository?.markStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name, true)
        return Result.success(Unit)
    }

    private class GitRun(
        val distro: String,
        val sub: PinnedSubmodule,
        val journal: SetupJournalContext?,
        val onRunStarted: (SetupStepStage, String) -> Unit,
        val log: (String) -> Unit,
    )

    /**
     * Runs a long git step (clone, fetch, submodule update). With the build service it is a durable, journaled
     * run that streams its output, never one HTTP request that times out on a slow network. Returns null on
     * success, otherwise why it failed.
     */
    @Suppress("ReturnCount") // local result, no journal, journaled verdict
    private suspend fun gitLong(ctx: GitRun, step: String, args: List<String>, workingDir: String): String? {
        val port = transport
        if (port == null) {
            val res = cli.execute(ctx.distro, listOf("git") + args, timeoutSeconds = CLONE_TIMEOUT_SECONDS)
            return if (res.exitCode == 0) null else res.error.ifBlank { res.output }.ifBlank { "exit ${res.exitCode}" }
        }
        val launcher = journaledLauncher(ctx.journal)
            ?: return "a durable '$step' of ${ctx.sub.name} requires a journaled attempt; " +
                "refusing an unjournaled server run"
        val runResult = launcher.run(
            stage = SetupStepStage.REPO_SYNCHRONIZATION,
            // The clone keeps its original action id so recovery of older journals still matches it.
            actionId = if (step == "clone") "submodule:${ctx.sub.name}" else "submodule:${ctx.sub.name}:$step",
            request = ToolExecutionRequest(
                toolId = "git",
                arguments = args,
                workingDirectory = workingDir,
                timeoutMs = CLONE_TIMEOUT_SECONDS * 1000L,
                purpose = io.ltirom.tooling.core.remote.RunPurpose.SETUP,
            ),
            recipeRevision = ctx.sub.commit,
            onRunStarted = { stage, runId ->
                ctx.onRunStarted(stage, runId)
                ctx.log("[Step 4/5] Attached to $step run $runId for '${ctx.sub.name}'.")
            },
            log = ctx.log,
        )
        return when (runResult) {
            is JournaledRunResult.Finished -> if (runResult.exitCode == 0) null else "exit code ${runResult.exitCode}"
            is JournaledRunResult.JournalRejected -> runResult.reason
            is JournaledRunResult.Interrupted -> runResult.reason
        }
    }

    /** Measures [subDir]: absent, its own clone at [pin], its own clone elsewhere, or not its own clone. */
    @Suppress("ReturnCount") // one exit per measured state
    private fun cloneState(distro: String, subDir: String, url: String, pin: String): CloneState {
        if (cli.execute(distro, listOf("test", "-d", subDir)).exitCode != 0) return CloneState.MISSING
        val topLevel = cli.execute(distro, listOf("git", "-C", subDir, "rev-parse", "--show-toplevel"))
        if (topLevel.exitCode != 0 || topLevel.output.trim().trimEnd('/') != subDir.trimEnd('/')) {
            return CloneState.NOT_A_CLONE
        }
        val originRes = cli.execute(distro, listOf("git", "-C", subDir, "remote", "get-url", "origin"))
        if (originRes.exitCode == 0) {
            val origin = GitCache.normalizeRepoUrl(originRes.output.trim())
            val expected = GitCache.normalizeRepoUrl(url)
            if (origin.isNotBlank() && expected.isNotBlank() && origin != expected) {
                return CloneState.ORIGIN_MISMATCH
            }
        }
        val head = cli.execute(distro, listOf("git", "-C", subDir, "rev-parse", "HEAD")).output.trim()
        return if (head == pin) CloneState.AT_PIN else CloneState.OTHER_COMMIT
    }

    private fun journaledLauncher(journal: SetupJournalContext?): JournaledRunLauncher? {
        val port = transport
        val repo = repository
        return if (port != null && repo != null && journal != null) JournaledRunLauncher(port, repo, journal) else null
    }

    /**
     * Checks which submodules are currently missing on disk.
     */
    public suspend fun findMissingSubmodules(
        distro: String,
        extDir: String,
        submodules: List<PinnedSubmodule> = DEFAULT_SUBMODULES,
    ): List<PinnedSubmodule> = submodules.filter { sub ->
        cli.execute(distro, listOf("test", "-d", "$extDir/${sub.name}")).exitCode != 0
    }
}
