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

import org.ide.lti.core.data.setup.RequirementCatalog
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus
import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Enforces repository-wide security invariants:
 * - Production code must NEVER generate, execute, or recommend NOPASSWD or sudoers modifications.
 * - Package remediation must use strictly unprivileged commands and approved package catalog.
 */
class SecuritySudoersGuardTest {

    @Test
    fun `no production source file contains NOPASSWD or writes to sudoers`() {
        // Resolve project root from current working directory
        val rootDir = File(".").canonicalFile
        val ltiRomGuiDir = if (File(rootDir, "LtiRomGui").exists()) File(rootDir, "LtiRomGui") else rootDir

        val productionSourceDirs = listOf(
            "src/main/kotlin",
            "src/desktopMain/kotlin",
            "src/commonMain/kotlin",
            "src/jvmMain/kotlin",
        )

        val violations = mutableListOf<String>()

        ltiRomGuiDir.walkTopDown()
            .onEnter { it.name != "build" && it.name != ".gradle" }
            .filter { file ->
                file.extension == "kt" &&
                    productionSourceDirs.any { file.invariantSeparatorsPath.contains(it) } &&
                    !file.invariantSeparatorsPath.contains("Test") &&
                    !file.invariantSeparatorsPath.contains("/build/")
            }
            .forEach { file ->
                val lines = file.readLines()
                lines.forEachIndexed { index, line ->
                    val trimmed = line.trim()
                    // Ignore comment lines explaining security invariants
                    if (trimmed.startsWith("//") || trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                        return@forEachIndexed
                    }
                    if (line.contains("NOPASSWD", ignoreCase = false) ||
                        line.contains("sudoers.d", ignoreCase = false) ||
                        line.contains("wsl.exe -u root") ||
                        line.contains("sudo -S")
                    ) {
                        violations.add("${file.invariantSeparatorsPath}:${index + 1} -> $trimmed")
                    }
                }
            }

        assertTrue(
            violations.isEmpty(),
            "Security invariant violated! Found prohibited privilege escalation or sudoers writes in production " +
                "code:\n" +
                violations.joinToString("\n"),
        )
    }

    @Test
    fun `WslPackageRemediator resolve produces only approved packages and zero sudoers actions`() {
        val failingDiagnostics = listOf(
            DiagnosticCheckItem(id = "host_compilers", title = "Compilers", status = StepStatus.FAILED),
            DiagnosticCheckItem(id = "python_crypto", title = "Python Crypto", status = StepStatus.FAILED),
            DiagnosticCheckItem(id = "dev_fuse", title = "Kernel Fuse", status = StepStatus.FAILED),
            DiagnosticCheckItem(id = "loop_mount", title = "Loop Mount", status = StepStatus.WARNING),
        )

        val plan = WslPackageRemediator.resolve(failingDiagnostics)

        assertFalse(plan.configureLoopMountElevation, "Plan must NEVER configure loop mount sudoers elevation")
        assertTrue(plan.pipPackages.isEmpty(), "Plan must NOT use pip packages; native apt packages must be used")

        // All resolved packages must strictly belong to RequirementCatalog.ALL_APT_PACKAGES
        for (pkg in plan.aptPackages) {
            assertTrue(
                RequirementCatalog.isPackageApproved(pkg),
                "Package '$pkg' is not in the approved package catalog!",
            )
            assertFalse(
                pkg.contains(";") || pkg.contains("&") || pkg.contains(" ") || pkg.contains("|"),
                "Package '$pkg' contains invalid shell metacharacters!",
            )
        }
    }
}
