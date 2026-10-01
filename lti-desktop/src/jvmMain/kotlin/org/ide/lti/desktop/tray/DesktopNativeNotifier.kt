/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.tray

import dev.nucleusframework.core.runtime.Platform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.ide.lti.core.common.notification.SystemNotification
import org.ide.lti.core.common.notification.SystemNotifier

/**
 * Native OS notifier for desktop environments (Windows, macOS, Linux).
 *
 * Operates independently of Java AWT Event Dispatch Thread constraints,
 * ensuring notification delivery functions seamlessly on the Tao native backend.
 */
class DesktopNativeNotifier(
    private val appTitle: String = "LtiRom Studio",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
) : SystemNotifier {

    override val isSupported: Boolean
        get() = when (Platform.Current) {
            Platform.Windows, Platform.MacOS, Platform.Linux -> true
            else -> false
        }

    override fun notify(notification: SystemNotification) {
        if (!isSupported) return

        scope.launch {
            try {
                when (Platform.Current) {
                    Platform.Windows -> sendWindowsToast(notification)
                    Platform.Linux -> sendLinuxNotification(notification)
                    Platform.MacOS -> sendMacNotification(notification)
                    else -> Unit
                }
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

    private fun escapePowerShell(text: String): String =
        text.replace("'", "''").replace("`", "``").replace("\$", "`\$")

    private fun escapeAppleScript(text: String): String =
        text.replace("\\", "\\\\").replace("\"", "\\\"")
}
