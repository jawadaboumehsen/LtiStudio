/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.terminal

/**
 * A rendered line in the terminal console history.
 */
sealed interface TerminalLine {
    data class Command(val text: String, val cwd: String) : TerminalLine
    data class Output(val text: String) : TerminalLine
    data class Error(val text: String) : TerminalLine
}

/**
 * An event emitted during the execution/dispatch of a terminal command.
 */
sealed interface TerminalOutputEvent {
    data class Output(val text: String) : TerminalOutputEvent
    data class Error(val text: String) : TerminalOutputEvent
    data object Clear : TerminalOutputEvent
}
