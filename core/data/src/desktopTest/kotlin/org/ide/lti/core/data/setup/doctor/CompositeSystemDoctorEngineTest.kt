/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.doctor.RunSystemDiagnosticsUseCase
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeSystemDoctorEngineTest {

    private class MockWslCliExecutor : WslCliExecutor() {
        var simulateAllSuccess: Boolean = true
        var simulateMissingPythonCrypto: Boolean = false
        var simulateLowDisk: Boolean = false
        var simulateMissingCompilers: Boolean = false
        var simulateMissingToolchainBinaries: Boolean = false
        val executedCommands = mutableListOf<String>()
        val executedUsers = mutableListOf<String?>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            val cmdStr = command.joinToString(" ")
            executedCommands.add(cmdStr)
            executedUsers.add(user)

            if (cmdStr.contains("apt-get install")) {
                if (cmdStr.contains("cmake") || cmdStr.contains("build-essential")) {
                    simulateMissingCompilers = false
                }
            }
            if (cmdStr.contains("pip3 install") && cmdStr.contains("cryptography")) {
                simulateMissingPythonCrypto = false
            }

            if (cmdStr.contains("find")) {
                val tools = mutableListOf(
                    "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                    "zip", "unzip", "brotli", "curl", "git", "rsync", "tar", "file",
                    "xxd", "truncate", "attr", "getfattr", "openssl", "python3", "pip3",
                    "java", "javac", "fusermount3", "fuse", "loop-control",
                    "java-17-openjdk-amd64", "java-21-openjdk-amd64",
                )
                if (!simulateMissingToolchainBinaries) {
                    tools.addAll(ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS)
                }
                if (simulateMissingCompilers) {
                    tools.remove("cmake")
                }
                return CliExecutionResult(0, tools.joinToString("\n"), "")
            }

            if (cmdStr.contains("find")) {
                val tools = mutableListOf(
                    "cmake", "make", "clang", "gcc", "clang++", "g++", "ccache",
                    "zip", "unzip", "brotli", "curl", "git", "rsync", "tar", "file",
                    "xxd", "truncate", "attr", "getfattr", "openssl", "python3", "pip3",
                    "java", "javac", "fusermount3", "fuse", "loop-control",
                    "java-17-openjdk-amd64", "java-21-openjdk-amd64",
                )
                if (!simulateMissingToolchainBinaries) {
                    tools.addAll(ToolchainIntegrityInspector.DEFAULT_CORE_TOOLS)
                }
                if (simulateMissingCompilers) {
                    tools.remove("cmake")
                }
                return CliExecutionResult(0, tools.joinToString("\n"), "")
            }

            if (cmdStr.contains("echo -n \$HOME") || cmdStr.contains("printenv HOME") || cmdStr.contains("printenv")) {
                return CliExecutionResult(0, "/home/lti", "")
            }

            if (cmdStr.contains("dpkg-query")) {
                val pkgs = NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES
                    .joinToString("\n") { "$it install ok installed" }
                return CliExecutionResult(0, pkgs, "")
            }

            if (cmdStr.contains("import cryptography, pyasn1")) {
                return if (simulateMissingPythonCrypto) {
                    CliExecutionResult(1, "", "ModuleNotFoundError: No module named 'cryptography'")
                } else {
                    CliExecutionResult(0, "", "")
                }
            }

            if (cmdStr.contains("python3 --version")) {
                return CliExecutionResult(0, "Python 3.12.3", "")
            }

            if (cmdStr.contains("java -version")) {
                return CliExecutionResult(0, "openjdk version \"21.0.11\" 2024-04-16", "")
            }

            if (cmdStr.contains("df -BG")) {
                return if (simulateLowDisk) {
                    CliExecutionResult(
                        0,
                        "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/sdc 250G 235G 15G 94% /home/lti\n",
                        "",
                    )
                } else {
                    CliExecutionResult(
                        0,
                        "Filesystem 1G-blocks Used Available Use% Mounted on\n/dev/sdc 250G 50G 190G 21% /home/lti\n",
                        "",
                    )
                }
            }

            if (cmdStr.contains("which cmake") ||
                cmdStr.contains("which make") ||
                cmdStr.contains("which clang") ||
                cmdStr.contains("which clang++")
            ) {
                return if (simulateMissingCompilers && cmdStr.contains("cmake")) {
                    CliExecutionResult(1, "", "not found")
                } else {
                    CliExecutionResult(0, "/usr/bin/tool", "")
                }
            }

            if (cmdStr.contains("cp -a") && cmdStr.contains("external/bin")) {
                simulateMissingToolchainBinaries = false
            }

            if (cmdStr.contains("test -e") && cmdStr.contains("apktool.jar")) {
                return if (simulateMissingToolchainBinaries) {
                    CliExecutionResult(1, "", "")
                } else {
                    CliExecutionResult(0, "", "")
                }
            }

            if (cmdStr.contains("test -e") ||
                cmdStr.contains("test -d") ||
                cmdStr.contains("test -x") ||
                cmdStr.contains("test -r")
            ) {
                return CliExecutionResult(0, "", "")
            }

            return CliExecutionResult(0, "/usr/bin/tool", "")
        }
    }

    @Test
    fun `runDiagnostics evaluates all 14 inspectors concurrently and returns success when environment is healthy`() =
        runTest {
            val testDispatcher = UnconfinedTestDispatcher(testScheduler)
            val mockCli = MockWslCliExecutor()
            val detector = WslEnvironmentDetector(cli = mockCli)

            val engine = CompositeSystemDoctorEngine(
                cli = mockCli,
                detector = detector,
                dispatcher = testDispatcher,
            )

            val results = engine.runDiagnostics("Ubuntu", binariesPresent = true)

            assertEquals(13, results.size, "Expected 13 diagnostic check items")
            val ids = results.map { it.id }.toSet()
            val expectedIds = setOf(
                "host_compilers", "host_libraries", "python_crypto", "jdk_dual",
                "selinux_attr", "archive_tools", "transfer_tools",
                "dev_fuse", "loop_mount", "disk_headroom", "openssl_tools", "toolchain_binaries",
                "build_cache",
            )
            assertEquals(expectedIds, ids)

            // All checks should be SUCCESS or WARNING (for optional tools)
            assertTrue(results.all { it.status == StepStatus.SUCCESS || it.status == StepStatus.WARNING })
        }

    @Test
    fun `PythonRuntimeInspector flags WARNING or FAILED when cryptography module is missing`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor().apply { simulateMissingPythonCrypto = true }
        val detector = WslEnvironmentDetector(cli = mockCli)

        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val results = engine.runDiagnostics("Ubuntu", binariesPresent = false)
        val pythonCheck = results.find { it.id == "python_crypto" }

        assertNotNull(pythonCheck)
        assertEquals(StepStatus.FAILED, pythonCheck.status)
        assertTrue(pythonCheck.detail.contains("cryptography/pyasn1 packages are missing"))
        assertNotNull(pythonCheck.copyableCommand)
    }

    @Test
    fun `StorageHeadroomInspector flags FAILED when free disk is below 25 GB`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor().apply { simulateLowDisk = true }
        val detector = WslEnvironmentDetector(cli = mockCli)

        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val results = engine.runDiagnostics("Ubuntu", binariesPresent = false)
        val diskCheck = results.find { it.id == "disk_headroom" }

        assertNotNull(diskCheck)
        assertEquals(StepStatus.FAILED, diskCheck.status)
        assertTrue(diskCheck.detail.contains("15 GB free"))
    }

    @Test
    fun `CompilersInspector flags FAILED when cmake is missing and no prebuilt binaries exist`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor().apply { simulateMissingCompilers = true }
        val detector = WslEnvironmentDetector(cli = mockCli)

        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val results = engine.runDiagnostics("Ubuntu", binariesPresent = false)
        val compilerCheck = results.find { it.id == "host_compilers" }

        assertNotNull(compilerCheck)
        assertEquals(StepStatus.FAILED, compilerCheck.status)
        assertTrue(compilerCheck.detail.contains("Missing: cmake"))
    }

    @Test
    fun `RunSystemDiagnosticsUseCase executes SystemDoctorPort seamlessly`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = mockCli)
        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )
        val useCase = RunSystemDiagnosticsUseCase(engine)

        val results = useCase("Ubuntu", binariesPresent = true)
        assertEquals(13, results.size)
    }

    @Test
    fun `autoRemediate requires user authorization when system packages are missing`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor().apply {
            simulateMissingCompilers = true
            simulateMissingPythonCrypto = true
        }
        val detector = WslEnvironmentDetector(cli = mockCli)
        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val progressLogs = mutableListOf<String>()
        val success = engine.autoRemediate("Ubuntu") { progressLogs.add(it) }

        assertFalse(success)
        assertTrue(
            progressLogs.any {
                it.contains("authorization in your WSL terminal") ||
                    it.contains("Remediation finished with remaining issues")
            },
        )
        assertTrue(mockCli.executedCommands.none { it.contains("sudo") })
    }

    @Test
    fun `LoopMountInspector succeeds when loop device control is present`() = runTest {
        val testCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmd = command.joinToString(" ")
                return when {
                    cmd.contains("test -e /dev/loop-control") -> CliExecutionResult(0, "", "")
                    else -> CliExecutionResult(1, "", "")
                }
            }
        }
        val inspector = LoopMountInspector(cli = testCli)
        val result = inspector.inspect(DiagnosticContext("Ubuntu", "/home/lti", "/home/lti/LtiRomTools/bin", true))
        assertEquals(StepStatus.SUCCESS, result.status)
        assertTrue(result.detail.contains("/dev/loop-control"))
    }

    @Test
    fun `autoRemediate synchronizes missing toolchain binaries when toolchain_binaries check warns`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor().apply {
            simulateMissingToolchainBinaries = true
        }
        val detector = WslEnvironmentDetector(cli = mockCli)
        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val progressLogs = mutableListOf<String>()
        val success = engine.autoRemediate("Ubuntu") { progressLogs.add(it) }

        assertTrue(success)
        assertTrue(mockCli.executedCommands.any { it.contains("cp -a") && it.contains("external/bin") })
        assertTrue(progressLogs.any { it.contains("Toolchain binaries synchronized") })
    }

    @Test
    fun `ArchiveToolsInspector flags FAILED when brotli is missing`() = runTest {
        val testCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmd = command.joinToString(" ")
                return if (cmd.contains("which brotli")) {
                    CliExecutionResult(1, "", "brotli: not found")
                } else {
                    CliExecutionResult(0, "/usr/bin/tool", "")
                }
            }
        }
        val inspector = ArchiveToolsInspector(cli = testCli)
        val result = inspector.inspect(DiagnosticContext("Ubuntu", "/home/lti", "/home/lti/LtiRomTools/bin", true))
        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(result.detail.contains("Missing: brotli"))
        assertEquals("sudo apt-get update && sudo apt-get install -y brotli", result.copyableCommand)
    }

    @Test
    fun `TransferToolsInspector flags FAILED when file, xxd, or truncate are missing`() = runTest {
        val testCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmd = command.joinToString(" ")
                return if (cmd.contains("which xxd") || cmd.contains("which truncate")) {
                    CliExecutionResult(1, "", "not found")
                } else {
                    CliExecutionResult(0, "/usr/bin/tool", "")
                }
            }
        }
        val inspector = TransferToolsInspector(cli = testCli)
        val result = inspector.inspect(DiagnosticContext("Ubuntu", "/home/lti", "/home/lti/LtiRomTools/bin", true))
        assertEquals(StepStatus.FAILED, result.status)
        assertTrue(result.detail.contains("xxd"))
        assertTrue(result.detail.contains("truncate"))
        assertTrue(result.copyableCommand?.contains("xxd") == true)
    }

    @Test
    fun `WslPackageRemediator batches brotli, attr, rsync, file, xxd`() = runTest {
        val installedAptCommands = mutableListOf<String>()
        val testCli = object : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmd = command.joinToString(" ")
                if (cmd.contains("apt-get install")) {
                    installedAptCommands.add(cmd)
                }
                return CliExecutionResult(0, "ok", "")
            }
        }
        val failedItems = listOf(
            org.ide.lti.core.domain.setup.DiagnosticCheckItem(
                id = "archive_tools",
                title = "Archive Tools",
                status = StepStatus.FAILED,
            ),
            org.ide.lti.core.domain.setup.DiagnosticCheckItem(
                id = "transfer_tools",
                title = "Transfer Tools",
                status = StepStatus.FAILED,
            ),
            org.ide.lti.core.domain.setup.DiagnosticCheckItem(
                id = "selinux_attr",
                title = "SELinux Attr",
                status = StepStatus.FAILED,
            ),
        )
        val plan = WslPackageRemediator.resolve(failedItems)
        assertTrue(plan.requiresElevation)
        assertTrue(plan.aptPackages.contains("brotli"))
        assertTrue(plan.aptPackages.contains("attr"))
        assertTrue(plan.aptPackages.contains("rsync"))
        assertTrue(plan.aptPackages.contains("file"))
        assertTrue(plan.aptPackages.contains("xxd"))
    }

    @Test
    fun `runDiagnostics on a healthy host issues at most 10 requests in total`() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        val mockCli = MockWslCliExecutor()
        val detector = WslEnvironmentDetector(cli = mockCli)
        val engine = CompositeSystemDoctorEngine(
            cli = mockCli,
            detector = detector,
            dispatcher = testDispatcher,
        )

        val results = engine.runDiagnostics("Ubuntu", binariesPresent = true)
        assertEquals(13, results.size)
        assertTrue(
            mockCli.executedCommands.size <= 10,
            "Expected <= 10 commands, but was ${mockCli.executedCommands.size}: ${mockCli.executedCommands}",
        )
    }
}
