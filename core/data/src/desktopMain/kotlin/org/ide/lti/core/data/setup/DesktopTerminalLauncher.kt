package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.ports.TerminalLauncherPort

/**
 * Desktop implementation of [TerminalLauncherPort] for Windows environments.
 * Launches an interactive terminal attached to the specified WSL distribution.
 *
 * Tries Windows Terminal (`wt.exe`) first, falling back to classic console host (`conhost.exe`).
 * Uses discrete argument lists exclusively to eliminate shell escaping injection risks.
 */
public class DesktopTerminalLauncher(
    private val processStarter: (List<String>) -> Unit = { cmd -> ProcessBuilder(cmd).start() },
) : TerminalLauncherPort {

    override fun launchTerminal(distro: String): Result<Unit> = tryLaunchWindowsTerminal(distro)
        .recoverCatching { tryLaunchConhost(distro).getOrThrow() }

    private fun tryLaunchWindowsTerminal(distro: String): Result<Unit> = runCatching {
        val command = listOf("wt.exe", "wsl.exe", "-d", distro)
        processStarter(command)
    }

    private fun tryLaunchConhost(distro: String): Result<Unit> = runCatching {
        val command = listOf("conhost.exe", "wsl.exe", "-d", distro)
        processStarter(command)
    }
}
