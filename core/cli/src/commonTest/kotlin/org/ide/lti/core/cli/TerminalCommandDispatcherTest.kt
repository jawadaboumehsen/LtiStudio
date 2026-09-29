/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.terminal.ShellExecutor
import org.ide.lti.core.domain.terminal.ShellOutputEvent
import org.ide.lti.core.model.terminal.TerminalOutputEvent
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

    @Test
    fun testThemeSetAndList() = runTest {
        val fakeShell = FakeShellExecutor()
        var selectedTheme: String? = null
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
            onSetTheme = { theme ->
                selectedTheme = theme
                true
            },
        )

        val setEvents = dispatcher.dispatch("theme set --name dark").toList()
        assertEquals("dark", selectedTheme)
        assertEquals(1, setEvents.size)
        assertEquals(TerminalOutputEvent.Output("Theme set to: dark"), setEvents[0])

        val listEvents = dispatcher.dispatch("theme list").toList()
        assertTrue(listEvents.any { it is TerminalOutputEvent.Output && it.text.contains("dark") })
        assertTrue(listEvents.any { it is TerminalOutputEvent.Output && it.text.contains("blue") })
    }

    @Test
    fun testSettingsListAndSet() = runTest {
        val fakeShell = FakeShellExecutor()
        val mockSettings = mutableMapOf<String, Any>("theme" to "dark", "effectsEnabled" to true)
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
            getSettings = { mockSettings },
            onUpdateSetting = { k, v ->
                mockSettings[k] = v
                true
            },
        )

        val listEvents = dispatcher.dispatch("settings list").toList()
        assertTrue(listEvents.any { it is TerminalOutputEvent.Output && it.text.contains("theme = dark") })

        val setEvents = dispatcher.dispatch("settings set -k theme -v blue").toList()
        assertEquals(1, setEvents.size)
        assertEquals(TerminalOutputEvent.Output("Setting 'theme' updated to 'blue'"), setEvents[0])
        assertEquals("blue", mockSettings["theme"])
    }

    @Test
    fun testWorkspaceInfoAndList() = runTest {
        val fakeShell = FakeShellExecutor()
        val tempDir = kotlin.io.path.createTempDirectory("test_ws_").toFile()
        tempDir.deleteOnExit()
        val subFile = File(tempDir, "sample.txt")
        subFile.writeText("hello")
        subFile.deleteOnExit()

        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = tempDir.absolutePath,
            shellExecutor = fakeShell,
        )

        val infoEvents = dispatcher.dispatch("workspace info").toList()
        assertTrue(infoEvents.any { it is TerminalOutputEvent.Output && it.text.contains("Workspace Location:") })

        val listEvents = dispatcher.dispatch("workspace list").toList()
        assertTrue(listEvents.any { it is TerminalOutputEvent.Output && it.text.contains("sample.txt") })
    }

    @Test
    fun testSystemMemoryAndInfo() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val memEvents = dispatcher.dispatch("system memory").toList()
        assertTrue(memEvents.any { it is TerminalOutputEvent.Output && it.text.contains("JVM Memory:") })

        val sysEvents = dispatcher.dispatch("system info").toList()
        assertTrue(sysEvents.any { it is TerminalOutputEvent.Output && it.text.contains("OS:") })
    }

    @Test
    fun testEchoFlags() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val errEvents = dispatcher.dispatch("echo -e error output").toList()
        assertEquals(1, errEvents.size)
        assertEquals(TerminalOutputEvent.Error("error output"), errEvents[0])
    }

    @Test
    fun testVersionShort() = runTest {
        val fakeShell = FakeShellExecutor()
        val dispatcher = TerminalCommandDispatcherImpl(
            initialCwd = System.getProperty("user.home"),
            shellExecutor = fakeShell,
        )

        val events = dispatcher.dispatch("version -s").toList()
        assertEquals(1, events.size)
        assertEquals(TerminalOutputEvent.Output("0.0.1-beta"), events[0])
    }

    @Test
    fun testDefaultCommandCompleter() {
        val completer = DefaultCommandCompleter()

        // Root completions
        val rootCompletions = completer.complete("")
        assertTrue(rootCompletions.contains("theme"))
        assertTrue(rootCompletions.contains("settings"))
        assertTrue(rootCompletions.contains("workspace"))
        assertTrue(rootCompletions.contains("system"))

        // Prefix matching
        val tCompletions = completer.complete("th")
        assertEquals(listOf("theme"), tCompletions)

        // Subcommand completions
        val themeSubcommands = completer.complete("theme ")
        assertTrue(themeSubcommands.contains("set"))
        assertTrue(themeSubcommands.contains("list"))
        assertTrue(themeSubcommands.contains("toggle"))

        // Option completions
        val themeSetOptions = completer.complete("theme set ")
        assertTrue(themeSetOptions.contains("--name"))
        assertTrue(themeSetOptions.contains("-n"))

        // Option values
        val themeValues = completer.complete("theme set --name ")
        assertTrue(themeValues.contains("dark"))
        assertTrue(themeValues.contains("blue"))
        assertTrue(themeValues.contains("light"))
    }
}
