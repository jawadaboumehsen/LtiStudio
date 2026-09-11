/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.utils

import io.github.vinceglb.filekit.core.FileKit
import io.github.vinceglb.filekit.core.PickerMode

object DialogUtils {
    /**
     * Shows a native system folder picker dialog asynchronously
     * without blocking the Compose UI thread, and returns the selected directory path,
     * or null if cancelled or if an error occurs.
     */
    suspend fun selectDirectory(title: String = "Select Workspace Directory"): String? {
        return try {
            FileKit.pickDirectory(title = title)?.path
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Shows a native system file picker dialog asynchronously
     * without blocking the Compose UI thread, and returns the selected [io.github.vinceglb.filekit.core.PlatformFile],
     * or null if cancelled or if an error occurs.
     */
    suspend fun selectFile(title: String = "Select Firmware Archive"): io.github.vinceglb.filekit.core.PlatformFile? {
        return try {
            FileKit.pickFile(mode = PickerMode.Single, title = title)
        } catch (_: Throwable) {
            null
        }
    }
}
