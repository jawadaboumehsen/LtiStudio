/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package org.ide.lti.desktop.menu.model

import androidx.compose.runtime.Immutable

/**
 * Immutable state snapshot driving the reactivity, enablement, and check states
 * of items rendered inside [org.ide.lti.desktop.menu.ui.StudioMenuBar].
 */
@Immutable
data class StudioMenuState(
    val isWorkspaceActive: Boolean = false,
    val isExplorerVisible: Boolean = true,
    val isAiAssistantVisible: Boolean = true,
    val isTerminalVisible: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
)
