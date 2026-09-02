package org.ide.lti.core.cli

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeShellExecutor : ShellExecutor {
    val recordedCommands = mutableListOf<Pair<String, String>>()
    var eventsToEmit: List<ShellOutputEvent> = listOf(
        ShellOutputEvent.Line("fake stdout line", isError = false),
        ShellOutputEvent.Finished(0),
    )

    override fun run(command: String, workingDirectory: String): Flow<ShellOutputEvent> = flow {
        recordedCommands.add(command to workingDirectory)
        for (event in eventsToEmit) {
            emit(event)
        }
    }
}

class TerminalCommandDispatcherTest {

    @Test
    fun testEchoCommand() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("echo hello world").toList()
        assertEquals(1, events.size)
        assertEquals(TerminalOutputEvent.Output("hello world"), events[0])
        assertEquals(0, fakeShell.recordedCommands.size)
    }

    @Test
    fun testVersionCommand() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("version").toList()
        assertEquals(1, events.size)
        assertEquals(TerminalOutputEvent.Output("LtiRom Studio terminal - clikt 5.0.1"), events[0])
    }

    @Test
    fun testThemeCommand() = runTest {
        var toggled = false
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
            onToggleTheme = { toggled = true },
        )

        val events = dispatcher.dispatch("theme").toList()
        assertTrue(toggled)
        assertEquals(1, events.size)
        assertEquals(TerminalOutputEvent.Output("Theme toggled"), events[0])
    }

    @Test
    fun testHelpCommand() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("help").toList()
        val hasHelpText = events.any { event ->
            event is TerminalOutputEvent.Output &&
                (event.text.contains("Commands:") || event.text.contains("help"))
        }
        assertTrue(hasHelpText)
    }

    @Test
    fun testClearCommand() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("clear").toList()
        assertEquals(1, events.size)
        assertEquals(TerminalOutputEvent.Clear, events[0])
    }

    @Test
    fun testUnrecognizedCommandRoutesToFakeShell() = runTest {
        val fakeShell = FakeShellExecutor()
        val userHome = System.getProperty("user.home")
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = userHome,
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("custom-tool --flag arg").toList()
        assertEquals(1, fakeShell.recordedCommands.size)
        assertEquals("custom-tool --flag arg" to dispatcher.cwd.value, fakeShell.recordedCommands[0])
        assertEquals(listOf(TerminalOutputEvent.Output("fake stdout line")), events)
    }

    @Test
    fun testCdValidDirectoryUpdatesCwd() = runTest {
        val fakeShell = FakeShellExecutor()
        val tempDir = kotlin.io.path.createTempDirectory("test_cd_").toFile()
        tempDir.deleteOnExit()

        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("cd \"${tempDir.absolutePath}\"").toList()
        assertEquals(0, events.size)
        assertEquals(tempDir.canonicalPath, dispatcher.cwd.value)
    }

    @Test
    fun testCdInvalidDirectoryEmitsErrorAndDoesNotUpdateCwd() = runTest {
        val fakeShell = FakeShellExecutor()
        val userHome = System.getProperty("user.home")
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = userHome,
            shellExecutor = fakeShell,
        )

        val initialCwd = dispatcher.cwd.value
        val invalidPath = "this_directory_should_never_exist_12345"
        val events = dispatcher.dispatch("cd $invalidPath").toList()

        assertEquals(1, events.size)
        assertTrue(events[0] is TerminalOutputEvent.Error)
        assertEquals(initialCwd, dispatcher.cwd.value)
    }

    @Test
    fun testCdBareReturnsToUserHome() = runTest {
        val fakeShell = FakeShellExecutor()
        val tempDir = kotlin.io.path.createTempDirectory("test_cd_home_").toFile()
        tempDir.deleteOnExit()

        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = tempDir.absolutePath,
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("cd").toList()
        assertEquals(0, events.size)
        assertEquals(File(System.getProperty("user.home")).canonicalPath, dispatcher.cwd.value)
    }
}
