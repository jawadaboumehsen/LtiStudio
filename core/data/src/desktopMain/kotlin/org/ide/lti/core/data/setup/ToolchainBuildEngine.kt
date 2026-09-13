package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCapabilities

/**
 * Dedicated engine for compiling and verifying native toolchain binaries.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages CMake, Make, and binary distribution
 *   for native C/C++ and auxiliary tool binaries.
 * - Incremental Execution & Durable Runs: Checks [ToolchainSetupRepository] and `$binDir` executables
 *   to skip redundant compilations. When [RemoteTransportPort] is provided, executes CMake and Make
 *   as durable runs through [JournaledRunLauncher] under environment/attempt/child identity (no static
 *   idempotency keys); an unjournaled durable run is refused.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class ToolchainBuildEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
    private val transport: RemoteTransportPort? = null,
) {
    public companion object {
        public val CORE_BINARIES: List<String> = listOf(
            "adb", "fastboot", "mke2fs", "dump.erofs", "mkfs.erofs", "img2sdat"
        )

        private val AUXILIARY_RECIPE_GROUPS: Set<String> = setOf(
            ToolCapabilities.GROUP_APKTOOL,
            ToolCapabilities.GROUP_SIGNAPK,
            ToolCapabilities.GROUP_PAYLOAD_DUMPER_GO,
        )
    }

    /**
     * Records installation evidence for every tool of [recipeGroup] whose binary now exists.
     * This is file discovery only: it never marks a tool execution-verified — that fact comes from
     * the domain probe run by the provisioner (FR-010).
     */
    private suspend fun recordInstalledBinaries(distro: String, binDir: String, recipeGroup: String) {
        for (toolId in ToolCapabilities.toolsForRecipeGroup(recipeGroup)) {
            val exists = cli.execute(distro, listOf("test", "-x", "$binDir/$toolId")).exitCode == 0
            if (exists) {
                repository?.updateTool(toolId) { tool ->
                    tool.copy(isCompiled = true, binaryPath = "$binDir/$toolId")
                }
            }
        }
    }

    @Suppress("ReturnCount") // local CLI path, unjournaled refusal and journaled verdict are three distinct exits
    private suspend fun executeBuildCommand(
        distro: String,
        workingDir: String,
        toolId: String,
        arguments: List<String>,
        timeoutSeconds: Long,
        actionName: String,
        journal: SetupJournalContext? = null,
        log: (String) -> Unit,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
    ): Result<Boolean> {
        if (transport == null) {
            val res = cli.execute(distro, listOf(toolId) + arguments, timeoutSeconds = timeoutSeconds)
            log("[$toolId] ${res.output.takeLast(300)}")
            return Result.success(res.exitCode == 0)
        }
        val repo = repository
        val ctx = journal
        if (repo == null || ctx == null) {
            return Result.failure(
                IllegalStateException(
                    "Durable build step '$actionName' requires a journaled attempt; " +
                        "refusing an unjournaled server run."
                )
            )
        }
        val runResult = JournaledRunLauncher(transport, repo, ctx).run(
            stage = SetupStepStage.TOOLCHAIN_COMPILATION,
            actionId = "build:$actionName",
            request = ToolExecutionRequest(
                toolId = toolId,
                arguments = arguments,
                workingDirectory = workingDir,
                timeoutMs = timeoutSeconds * 1000L
            ),
            onRunStarted = { stage, runId ->
                onRunStarted(stage, runId)
                log("[Step 5/5] Attached to build run $runId ($actionName).")
            },
            log = log,
        )
        return when (runResult) {
            is JournaledRunResult.Finished -> Result.success(runResult.exitCode == 0)
            is JournaledRunResult.JournalRejected -> Result.failure(IllegalStateException(runResult.reason))
            is JournaledRunResult.Interrupted -> Result.failure(IllegalStateException(runResult.reason))
        }
    }

    /**
     * Compiles and distributes all toolchain binaries into [binDir].
     *
     * Skips android-tools or erofs-utils compilation if binaries are already compiled and present.
     */
    @Suppress("CyclomaticComplexMethod", "LongMethod", "ReturnCount", "NestedBlockDepth")
    public suspend fun buildAndDistributeAll(
        distro: String,
        extDir: String,
        binDir: String,
        journal: SetupJournalContext? = null,
        targetRecipeGroups: Set<String>? = null,
        forceRebuild: Boolean = false,
        onRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
        log: (String) -> Unit = {},
    ): Result<Unit> {
        cli.execute(distro, listOf("mkdir", "-p", binDir))
        val persisted = repository?.currentState

        val shouldBuildAndroid = targetRecipeGroups == null ||
            targetRecipeGroups.contains(ToolCapabilities.GROUP_ANDROID_TOOLS)
        val shouldBuildErofs = targetRecipeGroups == null ||
            targetRecipeGroups.contains(ToolCapabilities.GROUP_EROFS_UTILS)
        val shouldBuildImg2sdat = targetRecipeGroups == null ||
            targetRecipeGroups.contains(ToolCapabilities.GROUP_IMG2SDAT)
        // Pre-staged binaries are their own recipe groups; a compile-group repair never re-syncs them.
        val shouldBuildAuxiliary = targetRecipeGroups == null ||
            targetRecipeGroups.any { it in AUXILIARY_RECIPE_GROUPS }

        // 1. android-tools
        if (shouldBuildAndroid) {
            val hasAdb = cli.execute(distro, listOf("test", "-x", "$binDir/adb")).exitCode == 0
            val isAdbRecorded = persisted?.tools?.get("adb")?.isCompiled == true
            if (!forceRebuild && isAdbRecorded && hasAdb) {
                log("[Step 5/5] android-tools binaries already compiled and verified in $binDir. Skipping CMake/Make.")
            } else {
                log("[Step 5/5] Compiling android-tools via CMake and Make...")
                val buildDir = "$extDir/android-tools/build"
                cli.execute(distro, listOf("mkdir", "-p", buildDir))

                val cmakeOk = executeBuildCommand(
                    distro = distro,
                    workingDir = "$extDir/android-tools",
                    toolId = "cmake",
                    arguments = listOf(
                        "-B", "build",
                        "-S", "$extDir/android-tools",
                        "-DCMAKE_BUILD_TYPE=Release",
                        "-DANDROID_TOOLS_USE_BUNDLED_FMT=ON",
                        "-DANDROID_TOOLS_USE_BUNDLED_LIBUSB=ON",
                        "-DANDROID_TOOLS_PATCH_VENDOR=OFF"
                    ),
                    timeoutSeconds = 120,
                    actionName = "android-tools:cmake",
                    journal = journal,
                    log = log,
                    onRunStarted = onRunStarted
                )
                if (!cmakeOk.getOrElse { return Result.failure(it) }) {
                    return Result.failure(IllegalStateException("android-tools cmake configuration failed"))
                }

                val makeOk = executeBuildCommand(
                    distro = distro,
                    workingDir = buildDir,
                    toolId = "make",
                    arguments = listOf("-C", buildDir, "-j4"),
                    timeoutSeconds = 600,
                    actionName = "android-tools:make",
                    journal = journal,
                    log = log,
                    onRunStarted = onRunStarted
                )
                if (!makeOk.getOrElse { return Result.failure(it) }) {
                    return Result.failure(IllegalStateException("android-tools make build failed"))
                }

                // Materialize vendor outputs via discrete sequential commands
                if (cli.execute(distro, listOf("test", "-d", "$buildDir/vendor")).exitCode == 0) {
                    cli.execute(distro, listOf("cp", "-a", "$buildDir/vendor/.", "$binDir/"))
                }
                if (cli.execute(distro, listOf("test", "-f", "$extDir/android-tools/vendor/avb/avbtool.py")).exitCode == 0) {
                    cli.execute(distro, listOf("cp", "-a", "$extDir/android-tools/vendor/avb/avbtool.py", "$binDir/avbtool"))
                }
                if (cli.execute(distro, listOf("test", "-f", "$extDir/android-tools/vendor/mkbootimg/mkbootimg.py")).exitCode == 0) {
                    cli.execute(distro, listOf("cp", "-a", "$extDir/android-tools/vendor/mkbootimg/mkbootimg.py", "$binDir/mkbootimg.py"))
                }
                if (cli.execute(distro, listOf("test", "-f", "$binDir/mke2fs.android")).exitCode == 0) {
                    cli.execute(distro, listOf("ln", "-sf", "mke2fs.android", "$binDir/mke2fs"))
                }
                if (cli.execute(distro, listOf("test", "-f", "$binDir/mkbootimg.py")).exitCode == 0) {
                    cli.execute(distro, listOf("ln", "-sf", "mkbootimg.py", "$binDir/mkbootimg"))
                }
                cli.execute(distro, listOf("chmod", "+x", "$binDir/avbtool", "$binDir/mkbootimg", "$binDir/mke2fs"))

                recordInstalledBinaries(distro, binDir, ToolCapabilities.GROUP_ANDROID_TOOLS)
            }
        }

        // 2. erofs-utils
        if (shouldBuildErofs) {
            val hasErofs = cli.execute(distro, listOf("test", "-x", "$binDir/mkfs.erofs")).exitCode == 0
            val isErofsRecorded = persisted?.tools?.get("mkfs.erofs")?.isCompiled == true
            if (!forceRebuild && isErofsRecorded && hasErofs) {
                log("[Step 5/5] erofs-utils binaries already compiled and verified in $binDir. Skipping CMake/Make.")
            } else {
                log("[Step 5/5] Compiling erofs-utils...")
                val outDir = "$extDir/erofs-utils/out"
                cli.execute(distro, listOf("mkdir", "-p", outDir))

                val cmakeOk = executeBuildCommand(
                    distro = distro,
                    workingDir = "$extDir/erofs-utils",
                    toolId = "cmake",
                    arguments = listOf("-S", "build/cmake", "-B", "out", "-DMAX_BLOCK_SIZE=4096"),
                    timeoutSeconds = 300,
                    actionName = "erofs-utils:cmake",
                    journal = journal,
                    log = log,
                    onRunStarted = onRunStarted
                )
                if (!cmakeOk.getOrElse { return Result.failure(it) }) {
                    return Result.failure(IllegalStateException("erofs-utils cmake configuration failed"))
                }

                val makeOk = executeBuildCommand(
                    distro = distro,
                    workingDir = outDir,
                    toolId = "make",
                    arguments = listOf("-C", outDir, "-j4"),
                    timeoutSeconds = 300,
                    actionName = "erofs-utils:make",
                    journal = journal,
                    log = log,
                    onRunStarted = onRunStarted
                )
                if (!makeOk.getOrElse { return Result.failure(it) }) {
                    return Result.failure(IllegalStateException("erofs-utils make build failed"))
                }

                if (cli.execute(distro, listOf("test", "-d", "$outDir/erofs-tools")).exitCode == 0) {
                    cli.execute(distro, listOf("cp", "-a", "$outDir/erofs-tools/.", "$binDir/"))
                }
                if (cli.execute(distro, listOf("test", "-f", "$binDir/fuse.erofs")).exitCode == 0) {
                    cli.execute(distro, listOf("ln", "-sf", "fuse.erofs", "$binDir/erofsfuse"))
                }
                cli.execute(distro, listOf("chmod", "+x", "$binDir/mkfs.erofs", "$binDir/dump.erofs"))

                recordInstalledBinaries(distro, binDir, ToolCapabilities.GROUP_EROFS_UTILS)
            }
        }

        // 3. img2sdat
        if (shouldBuildImg2sdat) {
            val hasImg2sdat = cli.execute(distro, listOf("test", "-e", "$binDir/img2sdat.py")).exitCode == 0 ||
                cli.execute(distro, listOf("test", "-e", "$binDir/img2sdat")).exitCode == 0
            if (forceRebuild || !hasImg2sdat) {
                if (cli.execute(distro, listOf("test", "-f", "$extDir/img2sdat/img2sdat.py")).exitCode == 0) {
                    cli.execute(distro, listOf("cp", "-a", "$extDir/img2sdat/img2sdat.py", "$binDir/img2sdat"))
                    cli.execute(distro, listOf("chmod", "+x", "$binDir/img2sdat"))
                }
            }
            recordInstalledBinaries(distro, binDir, ToolCapabilities.GROUP_IMG2SDAT)
        }

        // 4. Auxiliary pre-staged binaries and jar wrappers
        if (shouldBuildAuxiliary) {
            log("[Step 5/5] Synchronizing auxiliary tools (apktool, signapk, payload-dumper-go, erofsfuse)...")
            if (cli.execute(distro, listOf("test", "-d", "$extDir/bin")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/bin/.", "$binDir/"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/payload-dumper-go/payload-dumper-go")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/payload-dumper-go/payload-dumper-go", "$binDir/payload-dumper-go"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/signapk/signapk/build/libs/signapk.jar")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/signapk/signapk/build/libs/signapk.jar", "$binDir/signapk.jar"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/fuse.erofs")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "fuse.erofs", "$binDir/erofsfuse"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mkbootimg.py")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mkbootimg.py", "$binDir/mkbootimg"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mke2fs.android")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mke2fs.android", "$binDir/mke2fs"))
            }
            cli.execute(distro, listOf("chmod", "+x", "$binDir/signapk.jar", "$binDir/erofsfuse", "$binDir/payload-dumper-go"))
            for (group in AUXILIARY_RECIPE_GROUPS) {
                if (targetRecipeGroups == null || group in targetRecipeGroups) {
                    recordInstalledBinaries(distro, binDir, group)
                }
            }
        }

        val binariesToCheck = if (targetRecipeGroups != null) {
            val targetedTools = targetRecipeGroups.flatMap { ToolCapabilities.toolsForRecipeGroup(it) }.toSet()
            CORE_BINARIES.filter { it in targetedTools }
        } else {
            CORE_BINARIES
        }

        val missing = binariesToCheck.filter { bin ->
            cli.execute(distro, listOf("test", "-x", "$binDir/$bin")).exitCode != 0
        }

        return if (missing.isEmpty()) {
            if (targetRecipeGroups == null) {
                repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
            }
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Missing core binaries in $binDir: ${missing.joinToString(", ")}"))
        }
    }
}
