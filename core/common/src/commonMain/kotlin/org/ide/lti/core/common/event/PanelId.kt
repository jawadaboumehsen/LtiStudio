/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.event

import kotlinx.serialization.Serializable

/**
 * Identifies the four main panels in the IDR workspace.
 */
@Serializable
enum class PanelId {
    LEFT, // Navigation tools (Explorer, Search, Structure)
    MAIN, // Central editor zone
    RIGHT, // Contextual tools (Inspector, AI Assistant)
    BOTTOM, // Process outputs (Terminal, Logs, Tasks)
}
