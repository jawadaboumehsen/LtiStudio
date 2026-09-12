package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupStepStage

/**
 * Dedicated engine for compiling and verifying native toolchain binaries.
 *
 * Adheres to:
 * - Single Responsibility Principle: Exclusively manages CMake, Make, and binary distribution
 *   for native C/C++ and auxiliary tool binaries.
 * - Incremental Execution & Durable Runs: Checks [ToolchainSetupRepository] and `$binDir` executables
 *   to skip redundant compilations. When [RemoteTransportPort] is provided, executes CMake and Make
 *   as durable runs with idempotency keys (`setup:build:<tool>:<action>`), surviving GUI restarts.
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
    }

    private suspend fun executeBuildCommand(
        distro: String,
        workingDir: String,
        toolId: String,
        arguments: List<String>,
        timeoutSeconds: Long,
        idempotencyKey: String,
        log: (String) -> Unit
    ): Boolean {
        if (transport != null) {
            val handle = transport.startRun(
                StartRunRequest(
                    request = ToolExecutionRequest(
                        toolId = toolId,
                        arguments = arguments,
                        workingDirectory = workingDir,
                        timeoutMs = timeoutSeconds * 1000L
                    ),
                    idempotencyKey = idempotencyKey
                )
            )
            log("[Step 5/5] Attached to build run ${handle.runId} ($idempotencyKey).")
            var finishedExitCode: Int? = null
            transport.attachRun(handle.runId).collect { seqEv ->
                when (val ev = seqEv.event) {
                    is StreamEvent.OutputChunk -> log("[$toolId] ${ev.text.trimEnd()}")
                    is StreamEvent.ExecutionFinished -> finishedExitCode = ev.exitCode
                    else -> {}
                }
            }
            val status = transport.getRun(handle.runId)
            val exitCode = finishedExitCode ?: status?.exitCode
                ?: if (status?.status == RunStatusValue.COMPLETED) 0 else 1
            return exitCode == 0
        } else {
            val res = cli.execute(distro, listOf(toolId) + arguments, timeoutSeconds = timeoutSeconds)
            log("[$toolId] ${res.output.takeLast(300)}")
            return res.exitCode == 0
        }
    }

    /**
     * Compiles and distributes all toolchain binaries into [binDir].
     *
     * Skips android-tools or erofs-utils compilation if binaries are already compiled and present.
     */
    public suspend fun buildAndDistributeAll(
        distro: String,
        extDir: String,
        binDir: String,
        log: (String) -> Unit = {},
    ): Result<Unit> {
        cli.execute(distro, listOf("mkdir", "-p", binDir))
        val persisted = repository?.currentState

        // 1. android-tools
        val hasAdb = cli.execute(distro, listOf("test", "-x", "$binDir/adb")).exitCode == 0
        val isAdbRecorded = persisted?.tools?.get("adb")?.isCompiled == true
        if (isAdbRecorded && hasAdb) {
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
                idempotencyKey = "setup:build:android-tools:cmake",
                log = log
            )
            if (!cmakeOk) {
                return Result.failure(IllegalStateException("android-tools cmake configuration failed"))
            }

            val makeOk = executeBuildCommand(
                distro = distro,
                workingDir = buildDir,
                toolId = "make",
                arguments = listOf("-C", buildDir, "-j4"),
                timeoutSeconds = 600,
                idempotencyKey = "setup:build:android-tools:make",
                log = log
            )
            if (!makeOk) {
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

            val androidToolIds = listOf("adb", "fastboot", "mke2fs", "avbtool", "mkbootimg", "lpmake", "lpunpack", "lpdump")
            for (toolId in androidToolIds) {
                val exists = cli.execute(distro, listOf("test", "-x", "$binDir/$toolId")).exitCode == 0
                if (exists) {
                    repository?.updateTool(toolId) { tool ->
                        tool.copy(
                            isCompiled = true,
                            isVerified = true,
                            binaryPath = "$binDir/$toolId",
                            lastVerifiedTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        // 2. erofs-utils
        val hasErofs = cli.execute(distro, listOf("test", "-x", "$binDir/mkfs.erofs")).exitCode == 0
        val isErofsRecorded = persisted?.tools?.get("mkfs.erofs")?.isCompiled == true
        if (isErofsRecorded && hasErofs) {
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
                idempotencyKey = "setup:build:erofs-utils:cmake",
                log = log
            )
            if (!cmakeOk) {
                return Result.failure(IllegalStateException("erofs-utils cmake configuration failed"))
            }

            val makeOk = executeBuildCommand(
                distro = distro,
                workingDir = outDir,
                toolId = "make",
                arguments = listOf("-C", outDir, "-j4"),
                timeoutSeconds = 300,
                idempotencyKey = "setup:build:erofs-utils:make",
                log = log
            )
            if (!makeOk) {
                return Result.failure(IllegalStateException("erofs-utils make build failed"))
            }

            if (cli.execute(distro, listOf("test", "-d", "$outDir/erofs-tools")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$outDir/erofs-tools/.", "$binDir/"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/fuse.erofs")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "fuse.erofs", "$binDir/erofsfuse"))
            }
            cli.execute(distro, listOf("chmod", "+x", "$binDir/mkfs.erofs", "$binDir/dump.erofs"))

            val erofsToolIds = listOf("mkfs.erofs", "dump.erofs", "fsck.erofs")
            for (toolId in erofsToolIds) {
                val exists = cli.execute(distro, listOf("test", "-x", "$binDir/$toolId")).exitCode == 0
                if (exists) {
                    repository?.updateTool(toolId) { tool ->
                        tool.copy(
                            isCompiled = true,
                            isVerified = true,
                            binaryPath = "$binDir/$toolId",
                            lastVerifiedTimestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }

        // 3. img2sdat
        val hasImg2sdat = cli.execute(distro, listOf("test", "-e", "$binDir/img2sdat.py")).exitCode == 0 ||
                cli.execute(distro, listOf("test", "-e", "$binDir/img2sdat")).exitCode == 0
        if (!hasImg2sdat) {
            if (cli.execute(distro, listOf("test", "-f", "$extDir/img2sdat/img2sdat.py")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/img2sdat/img2sdat.py", "$binDir/img2sdat"))
                cli.execute(distro, listOf("chmod", "+x", "$binDir/img2sdat"))
            }
        }
        val img2sdatExists = cli.execute(distro, listOf("test", "-x", "$binDir/img2sdat")).exitCode == 0
        if (img2sdatExists) {
            repository?.updateTool("img2sdat") { tool ->
                tool.copy(isCompiled = true, isVerified = true, binaryPath = "$binDir/img2sdat", lastVerifiedTimestamp = System.currentTimeMillis())
            }
        }

        // 4. Auxiliary pre-staged binaries and jar wrappers
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

        val missing = CORE_BINARIES.filter { bin ->
            cli.execute(distro, listOf("test", "-x", "$binDir/$bin")).exitCode != 0
        }

        return if (missing.isEmpty()) {
            repository?.markStageCompleted(SetupStepStage.TOOLCHAIN_COMPILATION.name, true)
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Missing core binaries in $binDir: ${missing.joinToString(", ")}"))
        }
    }
}
