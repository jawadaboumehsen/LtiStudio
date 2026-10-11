package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Real-host integration test verifying ToolManager target selection:
 * picks in settings.json, pinned fallbacks, rebuild behavior, and settings immutability (T032).
 */
class ToolManagerTargetRealTest {
    @Test
    fun prepareMissingTargetsPinnedWhenNoPickAndReusesIntactCurrent() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("pinned-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "target-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "target-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. Initial prepareMissing: targets pinned version v1.0.0
                val summary1 = host.manager.prepareMissing()
                val res1 = summary1.groupResults[group.id]
                assertIs<GroupSetupResult.Installed>(res1)
                assertEquals("v1.0.0", res1.versionId)

                // The automatic run never writes settings.json
                val settingsFile = File(host.tempHome, "LtiRomTools/settings.json")
                assertFalse(settingsFile.exists(), "Automatic run must never write settings.json")

                // 2. Second prepareMissing: target is intact and current -> Reused
                val resultsDir = File(host.tempHome, "LtiRomTools/results")
                val resultsBefore = resultsDir.listFiles()?.map { it.name }?.toSet().orEmpty()

                val summary2 = host.manager.prepareMissing()
                val res2 = summary2.groupResults[group.id]
                assertIs<GroupSetupResult.Reused>(res2)
                assertEquals("v1.0.0", res2.versionId)

                val resultsAfter = resultsDir.listFiles()?.map { it.name }?.toSet().orEmpty()
                assertEquals(resultsBefore, resultsAfter, "No new results/*.json written when reused")
            }
        }
    }

    @Test
    fun targetIntactButNotCurrentMovesCurrentWithNoBuildAndLeavesSettingsUntouched() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("switch-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "switch-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "switch-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Install target version
                val summary1 = host.manager.prepareMissing()
                assertIs<GroupSetupResult.Installed>(summary1.groupResults[group.id])

                // Break current symlink so it's not pointing to v1.0.0
                val currentPath = host.hostPath("LtiRomTools/bin/${group.id.value}/current")
                val rmRes =
                    host.transport.execute(
                        ToolExecutionRequest(toolId = "rm", arguments = listOf("-f", currentPath)),
                    )
                assertEquals(0, rmRes.exitCode, "rm current symlink must succeed")

                val settingsFile = File(host.tempHome, "LtiRomTools/settings.json")
                val settingsBeforeMod = if (settingsFile.exists()) settingsFile.lastModified() else -1L

                // prepareMissing should move current to intact version without rebuilding
                val summary2 = host.manager.prepareMissing()
                val res2 = summary2.groupResults[group.id]
                assertIs<GroupSetupResult.Reused>(res2)

                val readlinkRes =
                    host.transport.execute(
                        ToolExecutionRequest(toolId = "readlink", arguments = listOf(currentPath)),
                    )
                assertEquals(0, readlinkRes.exitCode, "Current symlink must exist")
                assertTrue(readlinkRes.stdout.isNotBlank(), "Current symlink must point to a target")

                val settingsAfterMod = if (settingsFile.exists()) settingsFile.lastModified() else -1L
                assertEquals(settingsBeforeMod, settingsAfterMod, "settings.json must be untouched")
            }
        }
    }

    @Test
    fun pickInSettingsMakesThatVersionTheTargetAndRebuildsSavedSourceWhenDeleted() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("picked-tool-repo")
            // Pinned is v1.0.0, but repo also has custom tag v2.5.0
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "picked-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val script = "cd '$repoPath' && git tag 'v2.5.0'"
            host.transport.execute(
                io.ltirom.tooling.core.remote.ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", script)),
            )

            val group =
                Fixtures.createTestToolGroup(
                    id = "picked-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"), // Pinned is v1.0.0
                    commit = commit,
                )

            // Write pick to settings.json targeting v2.5.0
            val settingsDir = File(host.tempHome, "LtiRomTools")
            settingsDir.mkdirs()
            val escapedRepo = repoPath.replace("\\", "\\\\")
            val settingsJsonContent =
                """
                    {
                      "schema": 1,
                      "selections": {
                        "picked-tool": {
                          "id": "v2.5.0",
                          "source": {
                            "kind": "git",
                            "repo": "$escapedRepo",
                            "ref": { "type": "tag", "name": "v2.5.0" },
                            "commit": "$commit"
                          }
                        }
                      }
                    }
                """.trimIndent()
            val settingsFile = File(settingsDir, "settings.json")
            settingsFile.writeText(settingsJsonContent)

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. prepareMissing builds the picked version (v2.5.0), not pinned (v1.0.0)
                val summary1 = host.manager.prepareMissing()
                val res1 = summary1.groupResults[group.id]
                assertIs<GroupSetupResult.Installed>(res1)
                assertEquals("v2.5.0", res1.versionId)

                val v25Dir = File(host.tempHome, "LtiRomTools/bin/${group.id.value}/v2.5.0")
                assertTrue(v25Dir.exists(), "v2.5.0 must be built and installed")
                val v10Dir = File(host.tempHome, "LtiRomTools/bin/${group.id.value}/v1.0.0")
                assertFalse(v10Dir.exists(), "Studio pinned v1.0.0 must NOT be built when pick is present")

                // 2. rm -rf the picked version folder
                val rmRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "rm",
                            arguments = listOf("-rf", host.hostPath("LtiRomTools/bin/${group.id.value}/v2.5.0")),
                        ),
                    )
                assertEquals(0, rmRes.exitCode, "rm -rf v2.5.0 must succeed")
                assertFalse(v25Dir.exists())

                // 3. prepareMissing rebuilds saved source under the same ID, never Studio's pinned version
                val summary2 = host.manager.prepareMissing()
                val res2 = summary2.groupResults[group.id]
                assertIs<GroupSetupResult.Installed>(res2)
                assertEquals("v2.5.0", res2.versionId)
                assertTrue(v25Dir.exists(), "v2.5.0 must be rebuilt from saved source")
                assertFalse(v10Dir.exists(), "Pinned version must still not be built")
            }
        }
    }

    @Test
    fun damagedTargetRebuildsTargetSourceUnderSameId() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("damaged-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "damaged-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "damaged-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // 1. Install target version
                val summary1 = host.manager.prepareMissing()
                assertIs<GroupSetupResult.Installed>(summary1.groupResults[group.id])

                // 2. Damage output by truncating it
                val outBinary = File(host.tempHome, "LtiRomTools/bin/${group.id.value}/v1.0.0/damaged-tool")
                outBinary.writeText("") // 0 bytes -> not intact

                // 3. prepareMissing detects damage and rebuilds target under same ID
                val summary2 = host.manager.prepareMissing()
                val res2 = summary2.groupResults[group.id]
                assertIs<GroupSetupResult.Installed>(res2)
                assertEquals("v1.0.0", res2.versionId)
                assertTrue(outBinary.length() > 0, "Binary must be repaired and non-empty")
            }
        }
    }

    @Test
    fun unreadableSettingsJsonFailsWithInternalWithoutBuildOrSwitch() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val settingsDir = File(host.tempHome, "LtiRomTools")
            settingsDir.mkdirs()
            val settingsFile = File(settingsDir, "settings.json")
            settingsFile.writeText("INVALID JSON {{{ CORRUPTED")

            val summary = host.manager.prepareMissing()
            val firstRes = summary.groupResults.values.firstOrNull()
            assertNotNull(firstRes)
            assertIs<GroupSetupResult.Failed>(firstRes)
            val err = firstRes.error
            assertIs<ToolError.Internal>(err)
            assertTrue(err.detail.contains("settings.json can't be read"))
        }
    }
}
