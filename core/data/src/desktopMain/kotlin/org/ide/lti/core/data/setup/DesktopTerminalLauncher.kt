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

import org.ide.lti.core.domain.ports.TerminalLauncherPort

/**
 * Desktop implementation of [TerminalLauncherPort] for Windows environments.
 * Launches an interactive terminal attached to the specified WSL distribution.
 *
 * Tries Windows Terminal (`wt.exe`) first, falling back to classic console host (`conhost.exe`).
 * Uses discrete argument lists exclusively to eliminate shell escaping injection risks.
 *
 * The shell starts in the Linux home (`--cd ~`), not in the app's Windows folder under `/mnt/c`, and the
 * tab keeps a title naming the distro, so the window is recognisably WSL whatever the user's prompt is.
 */
public class DesktopTerminalLauncher(
    private val processStarter: (List<String>) -> Unit = { cmd -> ProcessBuilder(cmd).start() },
) : TerminalLauncherPort {

    override fun launchTerminal(distro: String): Result<Unit> = tryLaunchWindowsTerminal(distro)
        .recoverCatching { tryLaunchConhost(distro).getOrThrow() }

    private fun tryLaunchWindowsTerminal(distro: String): Result<Unit> = runCatching {
        processStarter(
            listOf("wt.exe", "--title", "$distro (WSL)", "--suppressApplicationTitle") + wslShell(distro),
        )
    }

    private fun tryLaunchConhost(distro: String): Result<Unit> = runCatching {
        processStarter(listOf("conhost.exe") + wslShell(distro))
    }

    private fun wslShell(distro: String): List<String> = listOf("wsl.exe", "-d", distro, "--cd", "~")
}
