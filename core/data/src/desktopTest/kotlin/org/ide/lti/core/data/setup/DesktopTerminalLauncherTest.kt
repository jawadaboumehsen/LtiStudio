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
        assertEquals(
            listOf(
                "wt.exe", "--title", "Ubuntu-24.04 (WSL)", "--suppressApplicationTitle",
                "wsl.exe", "-d", "Ubuntu-24.04", "--cd", "~",
            ),
            launched.first(),
        )
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
        assertEquals(listOf("conhost.exe", "wsl.exe", "-d", "Ubuntu-24.04", "--cd", "~"), launched.first())
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
