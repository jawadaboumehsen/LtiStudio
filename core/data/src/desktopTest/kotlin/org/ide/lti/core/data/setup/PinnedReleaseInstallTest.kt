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
import org.ide.lti.core.domain.setup.ToolCapabilities
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** gh and payload-dumper-go come from pinned, digest-checked releases, so a clean machine gets them too. */
class PinnedReleaseInstallTest {

    private val binDir = "/home/u/LtiRomTools/bin"
    private val extDir = "/home/u/LtiRomTools/external"
    private val gh = PinnedReleases.ALL.first { it.toolId == "gh" }
    private val pdg = PinnedReleases.ALL.first { it.toolId == "payload-dumper-go" }

    /** A distro where `sha256sum` answers from [digests] (path -> digest); every other command succeeds. */
    private class FakeDistro(val digests: MutableMap<String, String>) : WslCliExecutor() {
        val commands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            commands += command
            require(command.take(2) != listOf("sh", "-c")) { "the service refuses shell scripts" }
            val digest = digests[command.getOrNull(1)]
            return when {
                command.first() != "sha256sum" -> CliExecutionResult(0, "", "")
                digest == null -> CliExecutionResult(1, "", "No such file")
                else -> CliExecutionResult(0, "$digest  ${command[1]}", "")
            }
        }

        fun ran(tool: String) = commands.filter { it.first() == tool }
    }

    private fun extracted(release: PinnedRelease) = "$extDir/tmp/${release.toolId}/extract/${release.archiveMember}"

    @Test
    fun `a clean machine downloads, verifies and installs each pinned release`() = runTest {
        // Nothing installed yet; the downloaded archives hold the pinned binaries.
        val cli = FakeDistro(mutableMapOf(extracted(gh) to gh.sha256, extracted(pdg) to pdg.sha256))
        val logs = mutableListOf<String>()

        ToolchainBuildEngine(cli = cli).buildAndDistributeAll("Ubuntu", extDir, binDir) { logs += it }

        assertEquals(listOf(gh.url, pdg.url), cli.ran("curl").map { it.last() })
        assertEquals(
            listOf("tar", "-xzf", "$extDir/tmp/gh/release.tar.gz", "-C", "$extDir/tmp/gh/extract", gh.archiveMember),
            cli.ran("tar").first(),
        )
        assertEquals(listOf("$binDir/gh", "$binDir/payload-dumper-go"), cli.ran("mv").map { it.last() })
        assertTrue(logs.any { it.contains("Installed gh 2.97.0 (checksum verified)") }, "$logs")
    }

    @Test
    fun `a digest mismatch is never installed`() = runTest {
        val cli = FakeDistro(mutableMapOf(extracted(gh) to "0".repeat(64), extracted(pdg) to pdg.sha256))
        val logs = mutableListOf<String>()

        ToolchainBuildEngine(cli = cli).buildAndDistributeAll("Ubuntu", extDir, binDir) { logs += it }

        assertTrue(cli.ran("install").none { it.contains(extracted(gh)) }, "tampered gh must not be installed")
        assertTrue(logs.any { it.contains("ERROR: Could not install gh 2.97.0") && it.contains("checksum mismatch") })
        assertTrue(logs.any { it.contains("Installed payload-dumper-go 1.3.0") })
    }

    @Test
    fun `an installed binary with the pinned digest is kept without downloading`() = runTest {
        val cli = FakeDistro(mutableMapOf("$binDir/gh" to gh.sha256, "$binDir/payload-dumper-go" to pdg.sha256))
        val logs = mutableListOf<String>()

        ToolchainBuildEngine(cli = cli).buildAndDistributeAll("Ubuntu", extDir, binDir) { logs += it }

        assertTrue(cli.ran("curl").isEmpty())
        assertTrue(logs.any { it.contains("gh 2.97.0 already installed") })
    }

    @Test
    fun `repairing a compiled group downloads nothing, repairing gh downloads only gh`() = runTest {
        val cli = FakeDistro(mutableMapOf(extracted(gh) to gh.sha256, extracted(pdg) to pdg.sha256))
        val engine = ToolchainBuildEngine(cli = cli)

        engine.buildAndDistributeAll(
            "Ubuntu",
            extDir,
            binDir,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_ANDROID_TOOLS),
        )
        assertTrue(cli.ran("curl").isEmpty())

        engine.buildAndDistributeAll("Ubuntu", extDir, binDir, targetRecipeGroups = setOf(ToolCapabilities.GROUP_GH))
        assertEquals(listOf(gh.url), cli.ran("curl").map { it.last() })
    }
}
