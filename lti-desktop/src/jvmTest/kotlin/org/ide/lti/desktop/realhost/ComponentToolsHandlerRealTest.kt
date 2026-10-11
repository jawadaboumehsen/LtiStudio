package org.ide.lti.desktop.realhost

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.execute.ExecutionContext
import org.ide.lti.core.data.setup.execute.handlers.ComponentToolsHandler
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Real-host integration test verifying ComponentToolsHandler behavior with real ToolManager (T035):
 * - All Ready -> returns null
 * - Failing fixture -> returns Failed naming the failing group
 * - Cancel -> returns Cancelled
 */
class ComponentToolsHandlerRealTest {
    private fun makeContext(
        host: RealToolHost,
        action: SetupPlanAction.SwitchToolchain,
        kind: SetupPlanKind = SetupPlanKind.FULL_SETUP,
    ): ExecutionContext {
        val plan =
            SetupPlan(
                planId = "test-plan",
                revisionHash = "rev123",
                environmentKey = host.tempHome.name,
                kind = kind,
                orderedActions = listOf(action),
            )
        val scope =
            OperationScope(
                environmentKey = host.tempHome.name,
                operationId = "test-op",
                stateFlow = MutableStateFlow(ToolchainSetupState()),
                activeOpId = { "test-op" },
            )
        return ExecutionContext(
            distro = host.tempHome.name,
            home = host.hostPath(""),
            binDir = host.hostPath("bin"),
            extDir = host.hostPath("ext"),
            workDir = host.hostPath("work"),
            plan = plan,
            scope = scope,
        )
    }

    @Test
    fun mapsAllReadyToNull() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("ready-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "ready-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "ready-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                // Pre-install so that it is already ready
                host.manager.prepareMissing()

                val handler = ComponentToolsHandler(host.manager)
                val input =
                    ResolvedInput.Git(
                        group = group.id,
                        repoUrl = repoPath,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = commit,
                        resolvedAt = 1234567890L,
                    )
                val action =
                    SetupPlanAction.SwitchToolchain(
                        inputs = mapOf(group.id to input),
                        selectionRevision = 1L,
                    )
                val context = makeContext(host, action)

                val outcome = handler.execute(action, context)
                assertNull(outcome, "Ready tools should produce null SetupOutcome")
            }
        }
    }

    @Test
    fun failingFixtureMapsToFailedNamingTheGroup() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("failing-tool-repo")
            // Setup fixture whose probe fails
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "failing-tool",
                    probeExitCode = 1,
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "failing-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val handler = ComponentToolsHandler(host.manager)
                val input =
                    ResolvedInput.Git(
                        group = group.id,
                        repoUrl = repoPath,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = commit,
                        resolvedAt = 1234567890L,
                    )
                val action =
                    SetupPlanAction.SwitchToolchain(
                        inputs = mapOf(group.id to input),
                        selectionRevision = 1L,
                    )
                val context = makeContext(host, action)

                val outcome = handler.execute(action, context)
                assertIs<SetupOutcome.Failed>(outcome)
                assertTrue(
                    outcome.reason.contains("failing-tool"),
                    "Failure outcome must name the failing group, got: ${outcome.reason}",
                )
            }
        }
    }

    @Test
    fun cancelMapsToCancelled() = runTest(timeout = 180.seconds) {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("cancel-tool-repo")
            val setupRes =
                Fixtures.setupWorkingCMakeToolFixture(
                    transport = host.transport,
                    repoPath = repoPath,
                    toolName = "cancel-tool",
                    tag = "v1.0.0",
                )
            assertTrue(setupRes.isSuccess, "Must setup fixture repo")
            val commit = setupRes.getOrThrow()

            val group =
                Fixtures.createTestToolGroup(
                    id = "cancel-tool",
                    repoUrl = repoPath,
                    ref = ToolRef.Tag("v1.0.0"),
                    commit = commit,
                )

            ToolGroupCatalog.withIsolatedTestGroup(group) {
                val handler = ComponentToolsHandler(host.manager)
                val input =
                    ResolvedInput.Git(
                        group = group.id,
                        repoUrl = repoPath,
                        ref = ToolRef.Tag("v1.0.0"),
                        commit = commit,
                        resolvedAt = 1234567890L,
                    )
                val action =
                    SetupPlanAction.SwitchToolchain(
                        inputs = mapOf(group.id to input),
                        selectionRevision = 1L,
                    )
                val context = makeContext(host, action)

                var outcome: SetupOutcome? = null
                val job =
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        outcome = handler.execute(action, context)
                    }
                job.cancelAndJoin()
                assertEquals(SetupOutcome.Cancelled, outcome)
            }
        }
    }
}
