/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.terminal

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.model.terminal.TerminalOutputEvent

interface TerminalCommandDispatcher {
    /**
     * Currently tracked working directory.
     */
    val cwd: StateFlow<String>

    /**
     * Dispatches a raw input line.
     * Special-cases `cd`, built-in commands (help, clear, echo, version, theme),
     * and forwards any other command to the underlying shell executor.
     */
    fun dispatch(line: String): Flow<TerminalOutputEvent>
}
