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

interface ShellExecutor {
    /**
     * Executes the given [command] in the specified [workingDirectory].
     * Emits [ShellOutputEvent.Line] for each line of stdout and stderr as they arrive,
     * followed by [ShellOutputEvent.Finished] with the process exit code.
     * When the collecting coroutine is cancelled, the underlying process must be killed.
     */
    fun run(command: String, workingDirectory: String): Flow<ShellOutputEvent>
}

sealed interface ShellOutputEvent {
    data class Line(val text: String, val isError: Boolean) : ShellOutputEvent
    data class Finished(val exitCode: Int) : ShellOutputEvent
}
