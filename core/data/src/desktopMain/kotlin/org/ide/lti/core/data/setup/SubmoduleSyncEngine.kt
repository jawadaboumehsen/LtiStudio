package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage

/**
 * Dedicated engine for synchronizing external git submodules.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages git clone, checkout, and verification
 *   for external tool repositories.
 * - Incremental Execution & Durable Runs: Consults [ToolchainSetupRepository] and local filesystem
 *   to skip redundant clones. When [RemoteTransportPort] is provided, executes clone operations as
 *   durable runs with idempotency keys (`setup:submodule:<name>`), reattaching to in-flight runs on reconnect.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class SubmoduleSyncEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
    private val transport: RemoteTransportPort? = null,
) {
    public data class PinnedSubmodule(
        val name: String,
        val url: String,
        val commit: String,
    )

    public companion object {
        public val DEFAULT_SUBMODULES: List<PinnedSubmodule> = listOf(
            PinnedSubmodule("android-tools", "https://github.com/UN1CA/external_android-tools.git", "2c5e1b7c192f30ec3d50ec5f983618bb87b9895c"),
            PinnedSubmodule("apktool", "https://github.com/iBotPeaches/Apktool.git", "1c1d15b070c2138d958fa8b685d430c49c4bb117"),
            PinnedSubmodule("erofs-utils", "https://github.com/sekaiacg/erofs-utils.git", "24ebe947b889fabcd72f802e4f184b0f807a0e0e"),
            PinnedSubmodule("img2sdat", "https://github.com/UN1CA/external_img2sdat.git", "601317ed0bf502eb8facdd048f2d75dc75ffcd53"),
            PinnedSubmodule("signapk", "https://github.com/UN1CA/external_signapk.git", "cb88f4b27d8a07ca101eade5d40a6893920cb55e")
        )
    }

    /**
     * Synchronizes all required submodules into [extDir].
     *
     * For each submodule:
     * - If marked synced in [ToolchainSetupRepository] AND directory exists in WSL, skipping clone.
     * - If missing, clones recursively (via durable run or CLI), checks out pinned commit, and updates repository.
     */
    public suspend fun syncAll(
        distro: String,
        extDir: String,
        submodules: List<PinnedSubmodule> = DEFAULT_SUBMODULES,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        log: (String) -> Unit = {},
    ): Result<Unit> {
        cli.execute(distro, listOf("mkdir", "-p", extDir))

        val persisted = repository?.currentState

        for (sub in submodules) {
            val subDir = "$extDir/${sub.name}"
            val dirExists = cli.execute(distro, listOf("test", "-d", subDir)).exitCode == 0
            val isRecordedSynced = persisted?.submodules?.get(sub.name)?.isSynced == true

            if (isRecordedSynced && dirExists) {
                log("[Step 4/5] Submodule '${sub.name}' is already synced and persisted. Skipping clone.")
                continue
            }

            if (!dirExists) {
                log("[Step 4/5] Cloning submodule '${sub.name}' from ${sub.url}...")
                if (transport != null) {
                    val idempotencyKey = "setup:submodule:${sub.name}"
                    val runHandle = transport.startRun(
                        StartRunRequest(
                            request = ToolExecutionRequest(
                                toolId = "git",
                                arguments = listOf("clone", "--recursive", sub.url, subDir),
                                workingDirectory = extDir,
                                timeoutMs = 300_000L
                            ),
                            idempotencyKey = idempotencyKey
                        )
                    )
                    onRunStarted(SetupStepStage.REPO_SYNCHRONIZATION, runHandle.runId)
                    log("[Step 4/5] Attached to clone run ${runHandle.runId} for '${sub.name}'.")
                    var finishedExitCode: Int? = null
                    transport.attachRun(runHandle.runId).collect { seqEv ->
                        when (val ev = seqEv.event) {
                            is StreamEvent.OutputChunk -> log("[${sub.name}] ${ev.text.trimEnd()}")
                            is StreamEvent.ExecutionFinished -> finishedExitCode = ev.exitCode
                            else -> {}
                        }
                    }
                    val status = transport.getRun(runHandle.runId)
                    val finalCode = finishedExitCode ?: status?.exitCode
                        ?: if (status?.status == RunStatusValue.COMPLETED) 0 else 1
                    if (finalCode != 0) {
                        val err = "Failed to clone ${sub.name}: exit code $finalCode"
                        return Result.failure(IllegalStateException(err))
                    }
                } else {
                    val cloneRes = cli.execute(
                        distro = distro,
                        command = listOf("git", "clone", "--recursive", sub.url, subDir),
                        timeoutSeconds = 300,
                    )
                    if (cloneRes.exitCode != 0) {
                        val err = "Failed to clone ${sub.name}: ${cloneRes.error.ifBlank { cloneRes.output }}"
                        return Result.failure(IllegalStateException(err))
                    }
                }
                val checkoutRes = cli.execute(distro, listOf("git", "-C", subDir, "checkout", "-f", sub.commit))
                if (checkoutRes.exitCode != 0) {
                    val err = "Failed to check out ${sub.name} at ${sub.commit}: ${checkoutRes.error.ifBlank { checkoutRes.output }}"
                    return Result.failure(IllegalStateException(err))
                }
                log("[Step 4/5] Checked out '${sub.name}' at pinned commit ${sub.commit.take(8)}.")
            } else {
                log("[Step 4/5] Submodule directory '${sub.name}' detected. Verifying pinned commit ${sub.commit.take(8)}...")
                var checkoutRes = cli.execute(distro, listOf("git", "-C", subDir, "checkout", "-f", sub.commit))
                if (checkoutRes.exitCode != 0) {
                    log("[Step 4/5] Pinned commit not present locally for '${sub.name}'; fetching...")
                    cli.execute(distro, listOf("git", "-C", subDir, "fetch", "--all"), timeoutSeconds = 120)
                    checkoutRes = cli.execute(distro, listOf("git", "-C", subDir, "checkout", "-f", sub.commit))
                }
                if (checkoutRes.exitCode != 0) {
                    val err = "Failed to check out ${sub.name} at ${sub.commit} even after fetch: " +
                        checkoutRes.error.ifBlank { checkoutRes.output }
                    return Result.failure(IllegalStateException(err))
                }
            }

            repository?.updateSubmodule(sub.name) { it.copy(isSynced = true, commit = sub.commit) }
        }

        repository?.markStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name, true)
        return Result.success(Unit)
    }

    /**
     * Checks which submodules are currently missing on disk.
     */
    public suspend fun findMissingSubmodules(
        distro: String,
        extDir: String,
        submodules: List<PinnedSubmodule> = DEFAULT_SUBMODULES,
    ): List<PinnedSubmodule> {
        return submodules.filter { sub ->
            cli.execute(distro, listOf("test", "-d", "$extDir/${sub.name}")).exitCode != 0
        }
    }
}
