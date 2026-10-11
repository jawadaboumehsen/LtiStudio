package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.data.tools.ToolSettingsFile
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host test verifying tool store switching, settings.json recording,
 * ordering resilience, and removal constraints (T060).
 */
class ToolStoreSwitchRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String {
        val h = io.ltirom.tooling.client.WslPathTranslator().toWslPath(host.tempHome.path)
        return if (sub.isBlank()) h else "$h/$sub"
    }

    @Test
    fun useRecordWritesToolEntryToSettingsJson() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "switch-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "switch-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo: ${setupRes.exceptionOrNull()?.message}")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "switch-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                val req =
                    InstallRequest(
                        opId = "op-switch-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )

                val events = host.store.install(req).toList()
                val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                val fail = (finished?.result as? OperationResult.Failed)
                val failMsg =
                    if (fail != null) {
                        "INSTALL FAILED: ${fail.error} :: ${fail.message} :: events: ${events.joinToString(
                            "\n",
                        )}"
                    } else {
                        ""
                    }
                assertTrue(finished?.result is OperationResult.Installed, failMsg)

                val baseDir = hostPath(host, "LtiRomTools")
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), baseDir)
                val picks = sFile.readSelections().getOrThrow()
                assertEquals(1, picks.size)
                val pick = picks[group.id]
                assertNotNull(pick)
                assertEquals("v1.0.0", pick.first)
                assertTrue(pick.second is ResolvedSource.Git)
            }
        }
    }

    @Test
    fun useOrderingSavesSettingsBeforeLinkSwapFailsWhenBinReadOnly() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "order-tool-repo")
            val setupRes1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "order-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes1.isSuccess, "setupRes1 failed: ${setupRes1.exceptionOrNull()?.message}")
            val commit1 = setupRes1.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "order-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Install v1.0.0 with choice Keep (no pick recorded)
                val req1 =
                    InstallRequest(
                        opId = "op-order-v1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit1,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Keep,
                    )
                val events1 = host.store.install(req1).toList()
                val fin1 = events1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin1, "install v1 must finish")
                assertIs<OperationResult.Installed>(fin1.result, "install v1 must succeed")

                // Create second tag v2.0.0 and install it without selecting
                val setupRes2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        transport = host.transport,
                        repoPath = repoPath,
                        toolName = "order-tool",
                        probeExitCode = 0,
                        tag = "v2.0.0",
                    )
                assertTrue(setupRes2.isSuccess, "setupRes2 failed: ${setupRes2.exceptionOrNull()?.message}")
                val commit2 = setupRes2.getOrThrow()

                val req2 =
                    InstallRequest(
                        opId = "op-order-v2",
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commit2,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                        choice = ChoiceUpdate.Keep,
                    )
                val events2 = host.store.install(req2).toList()
                val fin2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin2, "install v2 must finish")
                assertIs<OperationResult.Installed>(fin2.result, "install v2 must succeed")

                val baseDir = hostPath(host, "LtiRomTools")
                val groupDir = "$baseDir/bin/${group.id.value}"

                // Make group directory read-only so ln -sfn / mv -T fails
                val chmodRes =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "chmod",
                            arguments = listOf("555", groupDir),
                            workingDirectory = baseDir,
                        ),
                    )
                assertEquals(0, chmodRes.exitCode)

                try {
                    // Switch to v2.0.0 with Record -> link swap will fail
                    val switchRes = host.store.use(group.id, "v2.0.0", ChoiceUpdate.Record)
                    assertTrue(switchRes.isFailure, "use() must fail when directory is read-only")

                    // settings.json already holds the new pick!
                    val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), baseDir)
                    val picks = sFile.readSelections().getOrThrow()
                    assertEquals(
                        "v2.0.0",
                        picks[group.id]?.first,
                        "settings.json must hold v2.0.0 before link swap",
                    )

                    // current is still v1.0.0
                    val currentLink =
                        host.transport.execute(
                            ToolExecutionRequest(
                                toolId = "readlink",
                                arguments = listOf("$groupDir/current"),
                                workingDirectory = baseDir,
                            ),
                        ).stdout.trim()
                    assertEquals("v1.0.0", currentLink, "current symlink must remain v1.0.0")
                } finally {
                    // Restore write permissions
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "chmod",
                            arguments = listOf("755", groupDir),
                            workingDirectory = baseDir,
                        ),
                    )
                }

                // Next prepareMissing completes the switch!
                val toolManager = ToolManager(host.store, ToolGroupCatalog)
                val summary = toolManager.prepareMissing()
                val groupRes = summary.groupResults[group.id]
                assertIs<GroupSetupResult.Reused>(groupRes)
                assertEquals("v2.0.0", groupRes.versionId)

                val currentAfter =
                    host.transport.execute(
                        ToolExecutionRequest(
                            toolId = "readlink",
                            arguments = listOf("$groupDir/current"),
                            workingDirectory = baseDir,
                        ),
                    ).stdout.trim()
                assertEquals("v2.0.0", currentAfter, "current symlink must now point to v2.0.0")
            }
        }
    }

    @Test
    fun useClearRemovesEntryAndKeepLeavesFileUntouched() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "clear-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    host.transport,
                    repoPath,
                    "clear-tool",
                    0,
                    "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "setupRes failed: ${setupRes.exceptionOrNull()?.message}")
            val commit = setupRes.getOrThrow()
            val group = Fixtures.createTestToolGroup("clear-tool", repoPath, ToolRef.Tag("v1.0.0"), commit = commit)

            ToolGroupCatalog.withTestGroup(group) {
                val req =
                    InstallRequest(
                        opId = "op-clear-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )
                val events = host.store.install(req).toList()
                val fin = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin, "install must finish")
                assertIs<OperationResult.Installed>(fin.result, "install must succeed")

                val baseDir = hostPath(host, "LtiRomTools")
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), baseDir)
                assertEquals(1, sFile.readSelections().getOrThrow().size)

                // Read bytes of settings.json
                val bytesBefore =
                    host.transport.execute(
                        ToolExecutionRequest(toolId = "cat", arguments = listOf("$baseDir/settings.json")),
                    ).stdout

                // use with Keep leaves file untouched
                host.store.use(group.id, "v1.0.0", ChoiceUpdate.Keep).getOrThrow()
                val bytesAfter =
                    host.transport.execute(
                        ToolExecutionRequest(toolId = "cat", arguments = listOf("$baseDir/settings.json")),
                    ).stdout
                assertEquals(bytesBefore, bytesAfter, "Keep must leave settings.json bytes untouched")

                // use with Clear removes entry
                host.store.use(group.id, "v1.0.0", ChoiceUpdate.Clear).getOrThrow()
                val picksAfterClear = sFile.readSelections().getOrThrow()
                assertTrue(picksAfterClear.isEmpty(), "Clear must remove entry from settings.json")
            }
        }
    }

    @Test
    fun removeRefusesVersionInUseAndPickedVersion() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "remove-tool-repo")
            val setupRes1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    host.transport,
                    repoPath,
                    "remove-tool",
                    0,
                    "v1.0.0",
                )
            assertTrue(setupRes1.isSuccess, "setupRes1 failed: ${setupRes1.exceptionOrNull()?.message}")
            val commit1 = setupRes1.getOrThrow()
            val group =
                Fixtures.createTestToolGroup(
                    "remove-tool",
                    repoPath,
                    ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withTestGroup(group) {
                // Install v1.0.0 and record as pick
                val req1 =
                    InstallRequest(
                        opId = "op-rem-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit1,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )
                val events1 = host.store.install(req1).toList()
                val fin1 = events1.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin1, "install v1 must finish")
                assertIs<OperationResult.Installed>(fin1.result, "install v1 must succeed")

                // Install v2.0.0 without selecting and with Keep
                val setupRes2 =
                    Fixtures.setupWorkingCMakeToolFixture(
                        host.transport,
                        repoPath,
                        "remove-tool",
                        0,
                        "v2.0.0",
                    )
                assertTrue(setupRes2.isSuccess, "setupRes2 failed: ${setupRes2.exceptionOrNull()?.message}")
                val commit2 = setupRes2.getOrThrow()
                val req2 =
                    InstallRequest(
                        opId = "op-rem-2",
                        group = group.id,
                        label = "v2.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v2.0.0"),
                            commit = commit2,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = false,
                        choice = ChoiceUpdate.Keep,
                    )
                val events2 = host.store.install(req2).toList()
                val fin2 = events2.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin2, "install v2 must finish")
                assertIs<OperationResult.Installed>(fin2.result, "install v2 must succeed")

                // Switch current to v2.0.0 with Keep (v1.0.0 is still picked in settings.json!)
                host.store.use(group.id, "v2.0.0", ChoiceUpdate.Keep).getOrThrow()

                val baseDir = hostPath(host, "LtiRomTools")
                val v1Path = "$baseDir/bin/${group.id.value}/v1.0.0"
                val v2Path = "$baseDir/bin/${group.id.value}/v2.0.0"

                // 1. Trying to remove v2.0.0 (version in use) -> fails InUse, folder still exists
                val remCurrRes = host.store.remove(group.id, "v2.0.0")
                assertTrue(remCurrRes.isFailure)
                val currErr = (remCurrRes.exceptionOrNull() as? ToolErrorException)?.toolError
                assertIs<ToolError.InUse>(currErr)
                val v2Exists = host.transport.execute(ToolExecutionRequest("test", listOf("-d", v2Path))).exitCode == 0
                assertTrue(v2Exists, "v2.0.0 folder must still exist")

                // 2. Trying to remove v1.0.0 (version picked in settings.json) -> fails InUse, folder still exists
                val remPickRes = host.store.remove(group.id, "v1.0.0")
                assertTrue(remPickRes.isFailure)
                val pickErr = (remPickRes.exceptionOrNull() as? ToolErrorException)?.toolError
                assertIs<ToolError.InUse>(pickErr)
                val v1Exists = host.transport.execute(ToolExecutionRequest("test", listOf("-d", v1Path))).exitCode == 0
                assertTrue(v1Exists, "v1.0.0 folder must still exist")

                // 3. Clear pick, now v1.0.0 is neither current nor picked -> removal succeeds!
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), baseDir)
                sFile.clear(group.id).getOrThrow()

                val remSuccess = host.store.remove(group.id, "v1.0.0")
                assertTrue(remSuccess.isSuccess, "Unused, unpicked version removal must succeed")
                val v1StillExists =
                    host.transport.execute(ToolExecutionRequest("test", listOf("-d", v1Path))).exitCode == 0
                assertTrue(!v1StillExists, "v1.0.0 folder must be deleted")
            }
        }
    }

    @Test
    fun statusAttachesSelectionAndUnreadableSettingsFailsInternal() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "status-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    host.transport,
                    repoPath,
                    "status-tool",
                    0,
                    "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "setupRes failed: ${setupRes.exceptionOrNull()?.message}")
            val commit = setupRes.getOrThrow()
            val group =
                Fixtures.createTestToolGroup(
                    "status-tool",
                    repoPath,
                    ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withTestGroup(group) {
                val req =
                    InstallRequest(
                        opId = "op-status-1",
                        group = group.id,
                        label = "v1.0.0",
                        source =
                        ResolvedSource.Git(
                            repoUrl = repoPath,
                            ref = ToolRef.Tag("v1.0.0"),
                            commit = commit,
                        ),
                        recipe = group.recipe,
                        outputs = group.outputs,
                        select = true,
                        choice = ChoiceUpdate.Record,
                    )
                val events = host.store.install(req).toList()
                val fin = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
                assertNotNull(fin, "install must finish")
                assertIs<OperationResult.Installed>(fin.result, "install must succeed")

                // status attaches group selection
                val inv1 = host.store.status(ToolGroupCatalog).getOrThrow()
                val gState = inv1.groups[group.id]
                assertNotNull(gState)
                assertEquals("v1.0.0", gState.selection?.id)

                // Make settings.json unreadable (corrupt JSON)
                val baseDir = hostPath(host, "LtiRomTools")
                val corruptScript = "cat << 'EOF' > '$baseDir/settings.json'\n{ corrupt json \nEOF"
                host.transport.execute(ToolExecutionRequest("sh", listOf("-c", corruptScript)))

                // status fails with Internal error
                val inv2 = host.store.status(ToolGroupCatalog)
                assertTrue(inv2.isFailure)
                val statusErr = (inv2.exceptionOrNull() as? ToolErrorException)?.toolError
                assertIs<ToolError.Internal>(statusErr)
                assertTrue(statusErr.message.contains("settings.json can't be read"))

                // use also fails with Internal error and file is untouched
                val useRes = host.store.use(group.id, "v1.0.0", ChoiceUpdate.Record)
                assertTrue(useRes.isFailure)
                val useErr = (useRes.exceptionOrNull() as? ToolErrorException)?.toolError
                assertIs<ToolError.Internal>(useErr)

                val fileContent =
                    host.transport.execute(
                        ToolExecutionRequest("cat", listOf("$baseDir/settings.json")),
                    ).stdout
                assertEquals("{ corrupt json", fileContent.trim(), "settings.json must not be overwritten")
            }
        }
    }
}
