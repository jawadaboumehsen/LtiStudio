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

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Enforces repository-wide security invariants:
 * - Production code must NEVER generate, execute, or recommend NOPASSWD or sudoers modifications.
 * - Production code must NEVER use sudo -S, root elevation without authorization, or password fields.
 */
class SecuritySudoersGuardTest {
    @Test
    fun `no production source file contains NOPASSWD, writes to sudoers, or prompts for password`() {
        val rootDir = File(".").canonicalFile
        val ltiRomGuiDir = if (File(rootDir, "LtiRomGui").exists()) File(rootDir, "LtiRomGui") else rootDir

        val productionSourceDirs =
            listOf(
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
}
