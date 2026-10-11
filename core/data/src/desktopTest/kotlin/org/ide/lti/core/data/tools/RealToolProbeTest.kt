/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Opt-in (`LTI_LIVE_WSL=1`): runs the shared validator, probes included, against the tool versions really
 * installed under `~/LtiRomTools/bin/<group>/current` in the distro (`LTI_TEST_DISTRO`, default Ubuntu).
 * A group that is not installed is skipped; an installed tool whose probe rejects it has a bad signature.
 */
class RealToolProbeTest {
    private val distro = System.getenv("LTI_TEST_DISTRO") ?: "Ubuntu"

    private fun mountPath(file: File): String =
        file.absolutePath.replace(File.separatorChar, '/').replace(Regex("^([A-Za-z]):")) {
            "/mnt/" + it.groupValues[1].lowercase()
        }

    /** Runs [script] in the distro; the exit code and the combined output. */
    private fun wsl(script: String): Pair<Int, String> {
        val file = File.createTempFile("probe", ".sh").apply {
            deleteOnExit()
            writeText(script.replace("\r", "") + "\n")
        }
        val process = ProcessBuilder("wsl.exe", "-d", distro, "--", "sh", mountPath(file))
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.readBytes().toString(Charsets.UTF_8)
        check(process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) { "timed out" }
        return process.exitValue() to output.replace(NUL, "")
    }

    private fun rejected(group: ToolGroup, home: String): List<String> {
        val dir = "$home/LtiRomTools/bin/${group.id.value}/current"
        if (wsl("[ -d \"$dir\" ]").first != 0) return emptyList()
        val perTool = group.outputs.filter {
            wsl(KotlinStoreLayout.versionCheck(dir, listOf(it), emptyList(), probe = true)).first != 0
        }.map { "${group.id.value}/${it.toolId}" }
        val whole = KotlinStoreLayout.versionCheck(dir, group.outputs, group.allSupportFiles(), probe = true)
        return perTool + listOfNotNull("${group.id.value} (group)".takeIf { wsl(whole).first != 0 })
    }

    @Test
    fun `every installed catalog tool passes its own probe`() {
        org.junit.Assume.assumeTrue("set LTI_LIVE_WSL=1", System.getenv("LTI_LIVE_WSL") == "1")
        val home = wsl("echo \$HOME").second.trim()

        val failures = ToolGroupCatalog.allGroups.flatMap { rejected(it, home) }

        assertTrue(failures.isEmpty(), "probes rejected installed tools: $failures")
    }

    private companion object {
        const val TIMEOUT_SECONDS = 120L
        const val NUL = "\u0000"
    }
}
