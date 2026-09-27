package org.ide.lti.core.data.setup

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DesktopTerminalLauncherTest {

    @Test
    fun `launches wt exe primarily with discrete arguments`() {
        val launched = mutableListOf<List<String>>()
        val launcher = DesktopTerminalLauncher { cmd ->
            launched.add(cmd)
        }

        val result = launcher.launchTerminal("Ubuntu-24.04")

        assertTrue(result.isSuccess)
        assertEquals(1, launched.size)
        assertEquals(listOf("wt.exe", "wsl.exe", "-d", "Ubuntu-24.04"), launched.first())
    }

    @Test
    fun `falls back to conhost exe when wt exe fails`() {
        val launched = mutableListOf<List<String>>()
        val launcher = DesktopTerminalLauncher { cmd ->
            if (cmd.first() == "wt.exe") {
                throw IOException("wt.exe not found on PATH")
            }
            launched.add(cmd)
        }

        val result = launcher.launchTerminal("Ubuntu-24.04")

        assertTrue(result.isSuccess)
        assertEquals(1, launched.size)
        assertEquals(listOf("conhost.exe", "wsl.exe", "-d", "Ubuntu-24.04"), launched.first())
    }

    @Test
    fun `returns failure when both wt and conhost fail`() {
        val launcher = DesktopTerminalLauncher { _ ->
            throw IOException("Terminal execution failed")
        }

        val result = launcher.launchTerminal("Ubuntu-24.04")

        assertTrue(result.isFailure)
    }
}
