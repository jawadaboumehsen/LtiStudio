/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

typealias TerminalLine = org.ide.lti.core.model.terminal.TerminalLine
typealias TerminalOutputEvent = org.ide.lti.core.model.terminal.TerminalOutputEvent

/**
 * Contextual state and service hooks passed to Clikt commands via Clikt Context.
 */
data class LtiCommandContext(
    val console: TerminalConsole,
    val getCwd: () -> String = { "" },
    val setCwd: (String) -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onSetTheme: (String) -> Boolean = { false },
    val onClear: () -> Unit = {},
    val getSettings: () -> Map<String, Any> = { emptyMap() },
    val onUpdateSetting: (String, String) -> Boolean = { _, _ -> false },
)
