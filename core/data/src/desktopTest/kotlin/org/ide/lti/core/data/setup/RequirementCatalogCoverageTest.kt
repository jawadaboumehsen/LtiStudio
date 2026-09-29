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

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.doctor.ArchiveToolsInspector
import org.ide.lti.core.data.setup.doctor.BuildCacheInspector
import org.ide.lti.core.data.setup.doctor.CompilersInspector
import org.ide.lti.core.data.setup.doctor.DiagnosticContext
import org.ide.lti.core.data.setup.doctor.DualJdkInspector
import org.ide.lti.core.data.setup.doctor.KernelFuseInspector
import org.ide.lti.core.data.setup.doctor.NativeLibrariesInspector
import org.ide.lti.core.data.setup.doctor.PythonRuntimeInspector
import org.ide.lti.core.data.setup.doctor.SecurityToolingInspector
import org.ide.lti.core.data.setup.doctor.SelinuxAttrInspector
import org.ide.lti.core.data.setup.doctor.SystemDiagnosticInspector
import org.ide.lti.core.data.setup.doctor.TransferToolsInspector
import org.ide.lti.core.domain.setup.StepStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequirementCatalogCoverageTest {

    private class MissingCli : WslCliExecutor() {
        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult = CliExecutionResult(1, "", "not installed")
    }

    private val missingContext = DiagnosticContext(
        distro = "Ubuntu-24.04",
        home = "/home/testuser",
        binDir = "/home/testuser/LtiRomTools/bin",
        binariesPresent = false,
        systemCommands = emptySet(),
        availableCommands = emptySet(),
    )

    private val allInspectors: List<SystemDiagnosticInspector> = listOf(
        ArchiveToolsInspector(MissingCli()),
        BuildCacheInspector(MissingCli()),
        CompilersInspector(MissingCli()),
        DualJdkInspector(MissingCli()),
        KernelFuseInspector(MissingCli()),
        NativeLibrariesInspector(MissingCli()),
        PythonRuntimeInspector(MissingCli()),
        SecurityToolingInspector(MissingCli()),
        SelinuxAttrInspector(MissingCli()),
        TransferToolsInspector(MissingCli()),
    )

    @Test
    fun `every package suggested by any inspector maps to a catalog row`() = runTest {
        val catalogPackages = RequirementCatalog.ALL_APT_PACKAGES

        for (inspector in allInspectors) {
            val item = inspector.inspect(missingContext)
            val cmd = item.copyableCommand ?: continue
            val parts = cmd.split(" ").filter { it.isNotBlank() }
            val installIdx = parts.indexOf("install")
            if (installIdx != -1) {
                val packages = parts.drop(installIdx + 1).filter { !it.startsWith("-") }
                for (pkg in packages) {
                    assertTrue(
                        pkg in catalogPackages,
                        "Package '$pkg' suggested by ${inspector::class.simpleName} is missing from " +
                            "RequirementCatalog!",
                    )
                }
            }
        }
    }

    @Test
    fun `every tool any build recipe invokes maps to a catalog row`() {
        val buildTools = listOf("cmake", "make", "clang", "protoc", "pkg-config", "git")
        val catalogIds = RequirementCatalog.REQUIREMENTS.map { it.id }.toSet()

        for (tool in buildTools) {
            val found = RequirementCatalog.REQUIREMENTS.any { req ->
                val check = req.check
                req.id == tool ||
                    req.aptPackages.contains(tool) ||
                    (check is org.ide.lti.core.model.setup.RequirementCheck.Command && check.name == tool)
            }
            assertTrue(found, "Build tool '$tool' is not represented in RequirementCatalog!")
        }
    }

    @Test
    fun `all requirements have valid IDs and non-empty packages`() {
        for (req in RequirementCatalog.REQUIREMENTS) {
            assertTrue(req.id.isNotBlank(), "Requirement id must not be blank")
            assertTrue(req.aptPackages.isNotEmpty(), "Requirement ${req.id} must have aptPackages")
            for (pkg in req.aptPackages) {
                assertTrue(pkg.isNotBlank())
                assertFalse(pkg.contains(" "))
            }
        }
    }

    @Test
    fun `after simulated install of missing packages no inspector reports missing packages`() = runTest {
        // Collect all packages from RequirementCatalog
        val installedPackages = RequirementCatalog.ALL_APT_PACKAGES.toSet()

        class AllInstalledCli : WslCliExecutor() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                val cmdStr = command.joinToString(" ")
                return when {
                    cmdStr.contains("which") ||
                        cmdStr.contains("test -x") ||
                        cmdStr.contains("test -f") ||
                        cmdStr.contains("test -d") ->
                        CliExecutionResult(0, "/usr/bin/tool", "")
                    cmdStr.contains("dpkg-query") -> {
                        val pkgs = command.filter { !it.startsWith("-") && it != "dpkg-query" }
                        val out = pkgs.joinToString("\n") { "$it install ok installed" }
                        CliExecutionResult(0, out, "")
                    }
                    cmdStr.contains("python3 --version") ->
                        CliExecutionResult(0, "Python 3.12.3", "")
                    cmdStr.contains("python3 -c") ->
                        CliExecutionResult(0, "", "")
                    cmdStr.contains("java -version") ->
                        CliExecutionResult(0, "openjdk version \"21.0.2\"", "")
                    else -> CliExecutionResult(0, "ok", "")
                }
            }
        }

        val allPresentContext = DiagnosticContext(
            distro = "Ubuntu-24.04",
            home = "/home/testuser",
            binDir = "/home/testuser/LtiRomTools/bin",
            binariesPresent = true,
            systemCommands = setOf(
                "cmake", "make", "clang", "gcc", "g++", "pkg-config", "protoc", "git",
                "curl", "rsync", "tar", "file", "xxd", "zip", "unzip", "brotli",
                "attr", "getfattr", "ccache", "openssl", "python3", "java", "javac", "fusermount3", "truncate",
            ),
        )

        val inspectors = listOf(
            ArchiveToolsInspector(AllInstalledCli()),
            BuildCacheInspector(AllInstalledCli()),
            CompilersInspector(AllInstalledCli()),
            DualJdkInspector(AllInstalledCli()),
            KernelFuseInspector(AllInstalledCli()),
            NativeLibrariesInspector(AllInstalledCli()),
            PythonRuntimeInspector(AllInstalledCli()),
            SecurityToolingInspector(AllInstalledCli()),
            SelinuxAttrInspector(AllInstalledCli()),
            TransferToolsInspector(AllInstalledCli()),
        )

        for (inspector in inspectors) {
            val item = inspector.inspect(allPresentContext)
            assertEquals(
                StepStatus.SUCCESS,
                item.status,
                "Inspector ${inspector::class.simpleName} reported ${item.status} instead of SUCCESS after full " +
                    "install: ${item.detail}",
            )
        }
    }
}
