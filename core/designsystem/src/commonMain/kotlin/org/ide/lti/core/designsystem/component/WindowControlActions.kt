/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

import androidx.compose.runtime.compositionLocalOf

/**
 * Window control actions interface for desktop platform integration.
 */
data class WindowControlActions(
    val onMinimize: () -> Unit = {},
    val onMaximize: () -> Unit = {},
    val onClose: () -> Unit = {},
    val onDragWindow: () -> Unit = {},
    val isMaximized: () -> Boolean = { false }
)

val LocalWindowControlActions = compositionLocalOf { WindowControlActions() }
