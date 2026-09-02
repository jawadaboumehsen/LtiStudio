package org.ide.lti.feature.workspace.panels.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import org.ide.lti.core.cli.ShellExecutor
import org.ide.lti.core.cli.ShellOutputEvent
import org.ide.lti.core.cli.TerminalLine
import org.ide.lti.core.data.settings.AppSettingsState
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeWorkspaceRepository : WorkspaceRepository {
    override fun getWorkspaces(): Flow<List<Workspace>> = emptyFlow()
    override suspend fun getWorkspace(id: String): Workspace? = null
    override suspend fun saveWorkspace(workspace: Workspace) {}
    override suspend fun deleteWorkspace(id: String) {}
    override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
    override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    override fun getRecentProjects(): Flow<List<RecentProject>> = emptyFlow()
    override suspend fun addRecentProject(project: RecentProject) {}
    override suspend fun removeRecentProject(workspaceId: String) {}
    override suspend fun clearRecentProjects() {}
}

class FakeShellExecutor(
    private val delayExecution: Boolean = false,
) : ShellExecutor {
    override fun run(command: String, workingDirectory: String): Flow<ShellOutputEvent> = kotlinx.coroutines.flow.flow {
        if (delayExecution) {
            kotlinx.coroutines.awaitCancellation()
        } else {
            emit(org.ide.lti.core.cli.ShellOutputEvent.Finished(0))
        }
    }
}

class TerminalViewModelTest {

    private fun createViewModel(delayExecution: Boolean = false): TerminalViewModel {
        val repo = FakeWorkspaceRepository()
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val workspaceManager = WorkspaceManager(repo, scope)
        val appSettingsState = AppSettingsState()
        val fakeShell = FakeShellExecutor(delayExecution = delayExecution)
        return TerminalViewModel(workspaceManager, appSettingsState, fakeShell)
    }

    @Test
    fun testInitialState() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value
        assertTrue(state.lines.isEmpty())
        assertEquals("", state.currentInput)
        assertFalse(state.isRunning)
        assertTrue(state.cwd.isNotBlank())
    }

    @Test
    fun testInputChange() {
        val viewModel = createViewModel()
        viewModel.onInputChange("echo test")
        assertEquals("echo test", viewModel.uiState.value.currentInput)
    }

    @Test
    fun testHistoryNavigation() {
        val viewModel = createViewModel()

        viewModel.onInputChange("first")
        viewModel.onSubmit()
        while (viewModel.uiState.value.isRunning) {
            Thread.sleep(10)
        }

        viewModel.onInputChange("second")
        viewModel.onSubmit()
        while (viewModel.uiState.value.isRunning) {
            Thread.sleep(10)
        }

        assertEquals("", viewModel.uiState.value.currentInput)

        // Navigate up -> should get "second"
        viewModel.onHistoryUp()
        assertEquals("second", viewModel.uiState.value.currentInput)

        // Navigate up again -> should get "first"
        viewModel.onHistoryUp()
        assertEquals("first", viewModel.uiState.value.currentInput)

        // Navigate down -> should get "second"
        viewModel.onHistoryDown()
        assertEquals("second", viewModel.uiState.value.currentInput)

        // Navigate down -> back to empty
        viewModel.onHistoryDown()
        assertEquals("", viewModel.uiState.value.currentInput)
    }

    @Test
    fun testCancelRunning() {
        val viewModel = createViewModel(delayExecution = true)

        viewModel.onInputChange("long running task")
        viewModel.onSubmit()

        viewModel.onCancelRunning()
        assertFalse(viewModel.uiState.value.isRunning)
        assertTrue(viewModel.uiState.value.lines.any { it is TerminalLine.Error && it.text == "^C" })
    }
}
