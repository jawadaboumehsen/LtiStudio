/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.common

import org.ide.lti.core.domain.ports.AppEnvironment
import org.ide.lti.core.domain.ports.AppEnvironmentPort
import java.awt.Desktop
import java.io.File

/** The JVM's own facts; the packaged app's version is the one jpackage records. */
internal class DesktopAppEnvironment : AppEnvironmentPort {
    override fun environment(): AppEnvironment {
        val data = resolveAppDataDir("").toFile()
        return AppEnvironment(
            version = System.getProperty("jpackage.app-version")?.takeIf { it.isNotBlank() },
            runtime = "${System.getProperty("java.vendor")} ${System.getProperty("java.runtime.version")}",
            operatingSystem = "${System.getProperty("os.name")} ${System.getProperty("os.version")} " +
                "(${System.getProperty("os.arch")})",
            dataDirectory = data.path,
            logsDirectory = File(data, "logs").path,
        )
    }

    override fun openFolder(path: String): Boolean {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) return false
        return runCatching { Desktop.getDesktop().open(File(path).apply { mkdirs() }) }.isSuccess
    }
}
