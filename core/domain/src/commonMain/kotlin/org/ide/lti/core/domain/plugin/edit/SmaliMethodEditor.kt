/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin.edit

import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.model.plugin.MethodMatch

/**
 * Applies a method edit's hunks inside one method of a smali class, so an edit keeps working when the rest of the
 * class (or the dex it is in) changes between firmwares:
 * - the method is picked by [MethodMatch] (name, parameters, return type, the strings it loads, the references
 *   its instructions use, an opcode run), never by position;
 * - each hunk's context and removed lines must occur once, consecutively, in that method, compared as trimmed whole
 *   lines; blank lines and `.line` debug lines are ignored on both sides. A `...` line between them skips any
 *   number of lines (the first match after it is taken), so anchors need not be neighbours;
 * - `@{name}` in a context or removed line matches any register (`v3`, `p1`) and an added line's `@{name}` is
 *   written as the register it matched, so inserted code follows the firmware's register allocation. `@{*}`
 *   matches any text (a register list, an opcode suffix) and is not captured.
 */
@Suppress("ReturnCount")
object SmaliMethodEditor {

    private data class Method(val start: Int, val end: Int, val signature: String)

    private data class HunkLine(val marker: Char, val text: String)

    fun apply(classSmali: String, match: MethodMatch, unifiedDiff: String, cls: String): EditResult {
        val lines = classSmali.replace("\r\n", "\n").split("\n").toMutableList()
        val candidates = methods(lines).filter { matches(it, match, lines) }
        val method = when (candidates.size) {
            0 -> return EditResult.Failed(ComponentPatchCodes.ANCHOR_NOT_FOUND, "No method matches ${describe(match)}")
            1 -> candidates.single()
            else -> return EditResult.Failed(
                ComponentPatchCodes.ANCHOR_AMBIGUOUS,
                "${candidates.size} methods match ${describe(match)}: ${candidates.joinToString { it.signature }}",
            )
        }
        var body = lines.subList(method.start, method.end + 1).toList()
        for (hunk in hunks(unifiedDiff)) {
            body = when (val applied = applyHunk(body, hunk, method.signature)) {
                is HunkResult.Failed -> return applied.failure
                is HunkResult.Applied -> applied.body
            }
        }
        val result = lines.subList(0, method.start) + body + lines.subList(method.end + 1, lines.size)
        return EditResult.Changed(result.joinToString("\n"), "$cls->${method.signature}")
    }

    private sealed interface HunkResult {
        data class Applied(val body: List<String>) : HunkResult
        data class Failed(val failure: EditResult.Failed) : HunkResult
    }

    /** Where a hunk's old lines matched: a body index per line ([GAP] for a `...` line), and the registers. */
    private class Hit(val at: List<Int>, val captures: Map<String, String>)

    @Suppress("CyclomaticComplexMethod")
    private fun applyHunk(body: List<String>, hunk: List<HunkLine>, signature: String): HunkResult {
        val old = hunk.filter { it.marker != '+' && it.text.isNotBlank() }
        if (old.isEmpty() || old.first().text.trim() == GAP_LINE || old.last().text.trim() == GAP_LINE) {
            return HunkResult.Failed(
                EditResult.Failed(
                    ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                    "A change in $signature has no anchor (or starts or ends with '...')",
                ),
            )
        }
        val significant = body.indices.filter { body[it].isNotBlank() && !body[it].trim().startsWith(".line ") }
        val patterns = old.map { line -> line.text.trim().takeUnless { it == GAP_LINE }?.let(::pattern) }
        val hits = significant.indices.mapNotNull { p -> matchAt(body, significant, patterns, p) }
        val anchor = old.first().text.trim()
        val hit = when (hits.size) {
            0 -> return HunkResult.Failed(
                EditResult.Failed(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, "'$anchor' not found in $signature"),
            )
            1 -> hits.single()
            else -> return HunkResult.Failed(
                EditResult.Failed(
                    ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                    "'$anchor' occurs ${hits.size}× in $signature",
                ),
            )
        }
        val replacement = mutableListOf<String>()
        var k = 0
        for (line in hunk) {
            when {
                line.marker == '+' -> replacement += fill(line.text, hit.captures)
                    ?: return HunkResult.Failed(
                        EditResult.Failed(
                            ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                            "'${line.text.trim()}' uses a register no anchor line captured",
                        ),
                    )
                line.text.isBlank() -> Unit
                // A kept gap keeps the firmware's lines between its neighbours; a removed one drops them.
                hit.at[k] == GAP -> {
                    if (line.marker == ' ') replacement += body.subList(hit.at[k - 1] + 1, hit.at[k + 1])
                    k++
                }
                // Context keeps the firmware's own line; removed lines are dropped.
                line.marker == ' ' -> replacement += body[hit.at[k++]]
                else -> k++
            }
        }
        val kept = body.subList(0, hit.at.first()) + replacement
        return HunkResult.Applied(kept + body.subList(hit.at.last() + 1, body.size))
    }

    /**
     * The hunk's [patterns] matched from the [p]th significant line: each follows the one before it, or comes
     * anywhere later after a gap (null), the first such line being taken.
     */
    private fun matchAt(body: List<String>, significant: List<Int>, patterns: List<LinePattern?>, p: Int): Hit? {
        val captures = mutableMapOf<String, String>()
        val at = mutableListOf<Int>()
        var next = p
        var afterGap = false
        for (pattern in patterns) {
            if (pattern == null) {
                at += GAP
                afterGap = true
                continue
            }
            val candidates = if (afterGap) next until significant.size else next..next
            val found = candidates.firstOrNull {
                it < significant.size && pattern.matchLine(body[significant[it]].trim(), captures)
            } ?: return null
            at += significant[found]
            next = found + 1
            afterGap = false
        }
        return Hit(at, captures)
    }

    /** A line pattern: literal text, with `@{name}` matching one register (the same one wherever the name recurs). */
    private class LinePattern(private val literal: String, private val regex: Regex?, private val names: List<String>) {
        fun matchLine(line: String, captures: MutableMap<String, String>): Boolean {
            if (regex == null) return line == literal
            val m = regex.matchEntire(line) ?: return false
            val found = names.mapIndexed { i, name -> name to m.groupValues[i + 1] }.filter { it.first != ANY }
            if (found.groupBy({ it.first }, { it.second }).any { it.value.distinct().size > 1 }) return false
            if (found.any { (name, reg) -> captures[name]?.let { it != reg } == true }) return false
            captures.putAll(found)
            return true
        }
    }

    private fun pattern(text: String): LinePattern {
        val trimmed = text.trim()
        val names = CAPTURE.findAll(trimmed).map { it.groupValues[1] }.toList()
        if (names.isEmpty()) return LinePattern(trimmed, null, emptyList())
        val parts = CAPTURE.split(trimmed).map { Regex.escape(it) }
        val regex = parts.first() + names.indices.joinToString("") { i ->
            (if (names[i] == ANY) "(.*?)" else REGISTER) + parts[i + 1]
        }
        return LinePattern(trimmed, Regex(regex), names)
    }

    private fun fill(text: String, captures: Map<String, String>): String? {
        var missing = false
        val filled = CAPTURE.replace(text) { m -> captures[m.groupValues[1]] ?: "".also { missing = true } }
        return filled.takeUnless { missing }
    }

    private fun methods(lines: List<String>): List<Method> {
        val found = mutableListOf<Method>()
        var start = -1
        lines.forEachIndexed { i, raw ->
            val line = raw.trim()
            when {
                line.startsWith(".method ") -> start = i
                line == ".end method" && start >= 0 -> {
                    found += Method(start, i, lines[start].trim().substringAfterLast(' '))
                    start = -1
                }
            }
        }
        return found
    }

    private fun matches(method: Method, match: MethodMatch, lines: List<String>): Boolean {
        val sig = method.signature
        val name = sig.substringBefore('(')
        val parameters = sig.substringAfter('(').substringBefore(')')
        val returns = sig.substringAfter(')')
        val body by lazy { lines.subList(method.start, method.end + 1).map(String::trim) }
        return (match.name == null || match.name == name) &&
            (match.parameters == null || match.parameters == parameters) &&
            (match.returns == null || match.returns == returns) &&
            match.strings.all { s -> body.any { it.startsWith("const-string") && it.endsWith(", \"$s\"") } } &&
            match.references.all { r -> instructions(body).any { r in reference(it) } } &&
            hasRun(instructions(body).map { it.substringBefore(' ') }, match.opcodes)
    }

    private fun instructions(body: List<String>) = body.filter { it.isNotEmpty() && it[0] !in ".:#" }

    /** An instruction's reference (`Lx;->m()V`, `Lx;->f:I`, `Lx;`): its last operand, unless that is a string. */
    private fun reference(instruction: String): String =
        instruction.substringAfterLast(", ", "").takeUnless { it.startsWith('"') }.orEmpty()

    private fun hasRun(opcodes: List<String>, run: List<String>): Boolean = run.isEmpty() ||
        (0..opcodes.size - run.size).any { p -> run.indices.all { run[it] == ANY || run[it] == opcodes[p + it] } }

    private fun describe(match: MethodMatch): String = buildList {
        match.name?.let { add("name $it") }
        match.parameters?.let { add("parameters ($it)") }
        match.returns?.let { add("return $it") }
        if (match.strings.isNotEmpty()) add("strings ${match.strings}")
        if (match.references.isNotEmpty()) add("references ${match.references}")
        if (match.opcodes.isNotEmpty()) add("opcodes ${match.opcodes}")
    }.joinToString(", ")

    /** The hunks of a unified diff with their line markers (git headers and `@@` lines separate them). */
    private fun hunks(diff: String): List<List<HunkLine>> {
        val hunks = mutableListOf<MutableList<HunkLine>>()
        for (line in diff.replace("\r\n", "\n").split("\n")) {
            when {
                line.startsWith("@@") -> hunks += mutableListOf<HunkLine>()
                hunks.isEmpty() || line.isEmpty() -> Unit
                line[0] in "+- " -> hunks.last() += HunkLine(line[0], line.substring(1))
            }
        }
        return hunks
    }

    private val CAPTURE = Regex("""@\{(\w+|\*)\}""")
    private const val REGISTER = "([vp]\\d+)"
    private const val ANY = "*"
    private const val GAP_LINE = "..."
    private const val GAP = -1
}
