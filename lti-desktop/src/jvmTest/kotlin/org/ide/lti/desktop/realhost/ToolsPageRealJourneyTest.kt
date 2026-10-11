package org.ide.lti.desktop.realhost

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.ide.lti.core.data.tools.RemoteCommandRunner
import org.ide.lti.core.data.tools.ToolSettingsFile
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.SetupUiState
import org.ide.lti.feature.setup.steps.ToolsStepContent
import org.ide.lti.feature.setup.tools.ToolRowStatusKind
import org.ide.lti.feature.setup.tools.ToolsPageState
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real journey Compose UI test of the Tools page (T066).
 *
 * Runs over real [ToolsPageState], real [org.ide.lti.core.domain.tools.ToolManager], and real host store.
 * Verified under CI with xvfb-run or local real host when LTI_REAL_HOST is set.
 */
@OptIn(ExperimentalTestApi::class)
class ToolsPageRealJourneyTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String {
        val h = io.ltirom.tooling.client.WslPathTranslator().toWslPath(host.tempHome.path)
        return if (sub.isBlank()) h else "$h/$sub"
    }

    @Test
    fun realToolsPageJourneyEndToEnd() = runDesktopComposeUiTest {
        if (!RealToolHost.isAvailable()) {
            println(
                "Skipping ToolsPageRealJourneyTest: RealToolHost is not available (set LTI_REAL_HOST=linux or LTI_REAL_HOST=wsl).",
            )
            return@runDesktopComposeUiTest
        }

        RealToolHost.create().use { host ->
            val repoPath = hostPath(host, "journey-tool-repo")
            val commit1 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "journey-tool",
                    probeExitCode = 0,
                    tag = "v1.0.0",
                ).getOrThrow()

            val commit2 =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "journey-tool",
                    probeExitCode = 0,
                    tag = "v2.0.0",
                ).getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "journey-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit1,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val sFile = ToolSettingsFile(RemoteCommandRunner(host.transport), hostPath(host, "LtiRomTools"))

                // Pre-install v1.0.0 as earlier version
                val initRes =
                    runBlocking {
                        host.manager.installAndUse(
                            group = group.id,
                            source = ResolvedSource.Git(repoPath, ToolRef.Tag("v1.0.0"), commit1),
                        )
                    }
                assertTrue(initRes is OperationResult.Installed)

                val scope = CoroutineScope(Dispatchers.Default)
                val state = ToolsPageState(host.manager, scope)

                runBlocking {
                    state.refresh()
                }

                setContent {
                    LtiTheme(reducedMotion = true) {
                        ToolsStepContent(
                            state = SetupUiState(),
                            toolsPageState = state,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                try {
                    // 1. Select the fixture group
                    state.selectGroup(group.id)
                    awaitIdle()

                    assertEquals(group.id, state.selectedGroup.value)
                    assertTrue(state.isInspectorOpen.value)

                    // 2. Install & use v2.0.0: builds for real, row shows Installing, then Ready
                    val installSource = ResolvedSource.Git(repoPath, ToolRef.Tag("v2.0.0"), commit2)
                    state.installAndUse(installSource)

                    // Wait for installation to complete and group to become Ready
                    runBlocking {
                        withTimeoutOrNull(60_000L) {
                            while (true) {
                                val row = state.rows.value.find { it.groupId == group.id.value }
                                if (row?.statusKind == ToolRowStatusKind.Ready && row.inUseLabel.startsWith("v2.0.0")) {
                                    break
                                }
                                delay(200L)
                            }
                        }
                    }
                    awaitIdle()

                    val rowAfterInstall = state.rows.value.find { it.groupId == group.id.value }
                    assertEquals(ToolRowStatusKind.Ready, rowAfterInstall?.statusKind)
                    assertTrue(rowAfterInstall?.inUseLabel?.startsWith("v2.0.0") == true)

                    // 3. Use the earlier version v1.0.0: must switch in under 2 seconds
                    val switchElapsedMs =
                        measureTimeMillis {
                            state.use("v1.0.0")
                            runBlocking {
                                withTimeoutOrNull(5_000L) {
                                    while (true) {
                                        val row = state.rows.value.find { it.groupId == group.id.value }
                                        if (row?.inUseLabel?.startsWith("v1.0.0") == true) break
                                        delay(100L)
                                    }
                                }
                            }
                        }
                    awaitIdle()
                    assertTrue(
                        switchElapsedMs < 2000L,
                        "Switching to earlier installed version took $switchElapsedMs ms, must be < 2000 ms",
                    )

                    // 4. Use Studio's version: pick is removed from settings.json
                    val picksBeforeFollow = runBlocking { sFile.readSelections().getOrThrow() }
                    assertTrue(picksBeforeFollow.containsKey(group.id), "Pick must exist before followStudio")

                    state.followStudio()
                    runBlocking {
                        withTimeoutOrNull(5_000L) {
                            while (true) {
                                val picks = sFile.readSelections().getOrThrow()
                                if (!picks.containsKey(group.id)) break
                                delay(100L)
                            }
                        }
                    }
                    val picksAfterFollow = runBlocking { sFile.readSelections().getOrThrow() }
                    assertFalse(picksAfterFollow.containsKey(group.id), "Pick must be removed after followStudio")

                    // 5. Remove is disabled for the version in use, with reason shown
                    assertFalse(state.canRemoveVersion("v1.0.0"))
                    val disabledReason = state.removeDisabledReason("v1.0.0")
                    assertNotNull(disabledReason)
                    assertTrue(disabledReason.contains("in use", ignoreCase = true))

                    // 6. Keyboard navigation: Down/Up, Enter and Esc
                    val rowNode = onNode(hasText(group.id.value) and hasClickAction())
                    rowNode.performClick()
                    rowNode.performKeyInput {
                        pressKey(Key.DirectionDown)
                    }
                    awaitIdle()

                    rowNode.performKeyInput {
                        pressKey(Key.DirectionUp)
                    }
                    awaitIdle()
                    assertEquals(group.id, state.selectedGroup.value)

                    // Enter opens inspector, Esc closes inspector
                    rowNode.performKeyInput {
                        pressKey(Key.Enter)
                    }
                    awaitIdle()
                    assertTrue(state.isInspectorOpen.value)

                    rowNode.performKeyInput {
                        pressKey(Key.Escape)
                    }
                    awaitIdle()
                    assertFalse(state.isInspectorOpen.value)
                } finally {
                    scope.cancel()
                }
            }
        }
    }
}
