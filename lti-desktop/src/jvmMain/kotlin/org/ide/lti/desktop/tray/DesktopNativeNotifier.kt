/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.tray

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier

internal enum class DesktopOs {
    Windows,
    MacOS,
    Linux,
    Other,
    ;

    companion object {
        val current: DesktopOs by lazy {
            val osName = System.getProperty("os.name").orEmpty().lowercase()
            when {
                osName.contains("win") -> Windows
                osName.contains("mac") || osName.contains("darwin") -> MacOS
                osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> Linux
                else -> Other
            }
        }
    }
}

/**
 * Native OS notifier for desktop environments (Windows, macOS, Linux).
 *
 * Operates independently of Java AWT Event Dispatch Thread constraints,
 * delivering notifications via native platform commands.
 */
class DesktopNativeNotifier(
    private val appTitle: String = "LtiRom Studio",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : SystemNotifier {

    override val isSupported: Boolean
        get() = when (DesktopOs.current) {
            DesktopOs.Windows, DesktopOs.MacOS, DesktopOs.Linux -> true
            DesktopOs.Other -> false
        }

    override fun notify(notification: SystemNotification) {
        if (!isSupported) return

        scope.launch {
            try {
                when (DesktopOs.current) {
                    DesktopOs.Windows -> sendWindowsToast(notification)
                    DesktopOs.Linux -> sendLinuxNotification(notification)
                    DesktopOs.MacOS -> sendMacNotification(notification)
                    DesktopOs.Other -> Unit
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Ignore notification delivery failures gracefully
            }
        }
    }

    private fun sendWindowsToast(notification: SystemNotification) {
        val escapedTitle = escapePowerShell(notification.title.ifBlank { appTitle })
        val escapedMessage = escapePowerShell(notification.message)
        val script = """
            [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime] | Out-Null
            ${'$'}template = [Windows.UI.Notifications.ToastNotificationManager]::GetTemplateContent([Windows.UI.Notifications.ToastTemplateType]::ToastText02)
            ${'$'}nodes = ${'$'}template.GetElementsByTagName('text')
            ${'$'}nodes.Item(0).AppendChild(${'$'}template.CreateTextNode('$escapedTitle')) | Out-Null
            ${'$'}nodes.Item(1).AppendChild(${'$'}template.CreateTextNode('$escapedMessage')) | Out-Null
            ${'$'}toast = [Windows.UI.Notifications.ToastNotification]::new(${'$'}template)
            [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier('$appTitle').Show(${'$'}toast)
        """.trimIndent()

        ProcessBuilder(
            "powershell",
            "-NoProfile",
            "-NonInteractive",
            "-WindowStyle",
            "Hidden",
            "-Command",
            script,
        ).start()
    }

    private fun sendLinuxNotification(notification: SystemNotification) {
        ProcessBuilder(
            "notify-send",
            notification.title.ifBlank { appTitle },
            notification.message,
        ).start()
    }

    private fun sendMacNotification(notification: SystemNotification) {
        val title = escapeAppleScript(notification.title.ifBlank { appTitle })
        val message = escapeAppleScript(notification.message)
        val script = "display notification \"$message\" with title \"$title\""
        ProcessBuilder("osascript", "-e", script).start()
    }

    private fun escapePowerShell(text: String): String = text.replace("'", "''").replace("`", "``").replace("\$", "`\$")

    private fun escapeAppleScript(text: String): String = text.replace("\\", "\\\\").replace("\"", "\\\"")
}
