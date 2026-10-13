/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

/**
 * How far an install is, from what it really reported: the phase it is in, and the percentage a tool printed
 * (git's "Receiving objects: 45%", cmake/make's "[ 45%]", ninja's "[12/345]"). Nothing is invented: a phase whose
 * tool prints no percentage stays at the start of its range, and the bar never moves backwards.
 */
public object OperationProgress {
    private const val FETCH_END = 20
    private const val BUILD_END = 85
    private const val VERIFY_END = 92
    private const val PUBLISH_END = 98
    private const val DONE = 100
    private const val HUNDRED = 100.0

    private val percentInBrackets = Regex("""\[\s*(\d{1,3})%]""")
    private val gitPercent = Regex("""(?:Receiving objects|Resolving deltas|Checking out files):\s+(\d{1,3})%""")
    private val ninjaStep = Regex("""\[(\d+)/(\d+)]""")

    /** The share of the bar a [phase] owns, as start..end percent. */
    public fun range(phase: OperationPhase): IntRange = when (phase) {
        OperationPhase.Fetching -> 0..FETCH_END
        OperationPhase.Building -> FETCH_END..BUILD_END
        OperationPhase.Verifying -> BUILD_END..VERIFY_END
        OperationPhase.Publishing -> VERIFY_END..PUBLISH_END
        OperationPhase.Switching -> PUBLISH_END..DONE
    }

    /** The 0..1 fraction of its own work a tool printed in [text] (the last one wins), or null if it printed none. */
    public fun fractionIn(text: String): Double? =
        text.lineSequence().toList().asReversed().firstNotNullOfOrNull { lineFraction(it) }

    private fun lineFraction(line: String): Double? {
        val percent = (percentInBrackets.find(line) ?: gitPercent.find(line))?.groupValues?.get(1)?.toIntOrNull()
        val step = ninjaStep.find(line)?.groupValues
        val done = step?.get(1)?.toLongOrNull()
        val total = step?.get(2)?.toLongOrNull()
        return when {
            percent != null -> percent.coerceIn(0, DONE) / HUNDRED
            done != null && total != null && total > 0 -> (done.toDouble() / total).coerceIn(0.0, 1.0)
            else -> null
        }
    }

    /** [op] after moving to [phase]: the bar jumps to the start of that phase's range if it is not already past it. */
    public fun enter(op: ToolOperation, phase: OperationPhase): ToolOperation = op.copy(
        phase = phase,
        percent = op.percent?.let { maxOf(it, range(phase).first) },
    )

    /** [op] after [text] of output: moved forward by a percentage the tool printed, within the phase it is in. */
    public fun onOutput(op: ToolOperation, text: String): ToolOperation {
        val current = op.percent
        val fraction = if (op.phase == OperationPhase.Fetching || op.phase == OperationPhase.Building) {
            fractionIn(text)
        } else {
            null
        }
        if (current == null || fraction == null) return op
        val range = range(op.phase)
        val reached = range.first + ((range.last - range.first) * fraction).toInt()
        return if (reached > current) op.copy(percent = reached) else op
    }
}
