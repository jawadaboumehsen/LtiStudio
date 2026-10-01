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

import com.github.ajalt.mordant.input.InputEvent
import com.github.ajalt.mordant.input.MouseTracking
import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.rendering.Size
import com.github.ajalt.mordant.terminal.PrintRequest
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalInfo
import com.github.ajalt.mordant.terminal.TerminalInterface
import com.github.ajalt.mordant.terminal.TerminalRecorder
import kotlin.time.TimeMark

/**
 * Terminal console that redirects Clikt/Mordant output to an in-memory buffer
 * and optional callback instead of standard out/err.
 */
class TerminalConsole(
    val recorder: TerminalRecorder = TerminalRecorder(),
    private val onPrint: ((text: String, isError: Boolean) -> Unit)? = null,
) : TerminalInterface {

    val terminal: Terminal = Terminal(terminalInterface = this)

    override fun completePrintRequest(pr: PrintRequest) {
        recorder.completePrintRequest(pr)
        onPrint?.invoke(pr.text, pr.stderr)
    }

    override fun info(
        ansiLevel: AnsiLevel?,
        hyperlinks: Boolean?,
        outputInteractive: Boolean?,
        inputInteractive: Boolean?,
    ): TerminalInfo = recorder.info(ansiLevel, hyperlinks, outputInteractive, inputInteractive)

    override fun readLineOrNull(hideInput: Boolean): String? = recorder.readLineOrNull(hideInput)

    override fun getTerminalSize(): Size = recorder.getTerminalSize()

    override fun readInputEvent(timeout: TimeMark, mouseTracking: MouseTracking): InputEvent? =
        recorder.readInputEvent(timeout, mouseTracking)

    override fun enterRawMode(mouseTracking: MouseTracking): AutoCloseable =
        recorder.enterRawMode(mouseTracking)

    override fun shouldAutoUpdateSize(): Boolean = recorder.shouldAutoUpdateSize()

    fun output(): String = recorder.output()

    fun stdout(): String = recorder.stdout()

    fun stderr(): String = recorder.stderr()

    fun clear(): Unit = recorder.clearOutput()
}
