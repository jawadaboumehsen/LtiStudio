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
 * - the method is picked by [MethodMatch] (name, parameters, return type, the strings it loads), never by position;
 * - each hunk's context and removed lines must occur once, consecutively, in that method, compared as trimmed whole
 *   lines; blank lines and `.line` debug lines are ignored on both sides;
 * - `@{name}` in a context or removed line matches any register (`v3`, `p1`) and an added line's `@{name}` is
 *   written as the register it matched, so inserted code follows the firmware's register allocation.
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

    private fun applyHunk(body: List<String>, hunk: List<HunkLine>, signature: String): HunkResult {
        val old = hunk.filter { it.marker != '+' && it.text.isNotBlank() }
        if (old.isEmpty()) {
            return HunkResult.Failed(
                EditResult.Failed(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, "A change in $signature has no anchor"),
            )
        }
        val significant = body.indices.filter { body[it].isNotBlank() && !body[it].trim().startsWith(".line ") }
        val patterns = old.map { pattern(it.text) }
        val hits = (0..significant.size - old.size).mapNotNull { p ->
            val captures = mutableMapOf<String, String>()
            p.takeIf { patterns.indices.all { k -> patterns[k].matchLine(body[significant[p + k]].trim(), captures) } }
                ?.let { it to captures }
        }
        val anchor = old.first().text.trim()
        val (p, captures) = when (hits.size) {
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
                line.marker == '+' -> replacement += fill(line.text, captures)
                    ?: return HunkResult.Failed(
                        EditResult.Failed(
                            ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                            "'${line.text.trim()}' uses a register no anchor line captured",
                        ),
                    )
                line.text.isBlank() -> Unit
                // Context keeps the firmware's own line; removed lines are dropped.
                line.marker == ' ' -> replacement += body[significant[p + k++]]
                else -> k++
            }
        }
        val first = significant[p]
        val last = significant[p + old.size - 1]
        return HunkResult.Applied(body.subList(0, first) + replacement + body.subList(last + 1, body.size))
    }

    /** A line pattern: literal text, with `@{name}` matching one register (the same one wherever the name recurs). */
    private class LinePattern(private val literal: String, private val regex: Regex?, private val names: List<String>) {
        fun matchLine(line: String, captures: MutableMap<String, String>): Boolean {
            if (regex == null) return line == literal
            val m = regex.matchEntire(line) ?: return false
            val found = names.mapIndexed { i, name -> name to m.groupValues[i + 1] }
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
        val regex = CAPTURE.split(trimmed).joinToString(REGISTER) { Regex.escape(it) }
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
            match.strings.all { s -> body.any { it.startsWith("const-string") && it.endsWith(", \"$s\"") } }
    }

    private fun describe(match: MethodMatch): String = buildList {
        match.name?.let { add("name $it") }
        match.parameters?.let { add("parameters ($it)") }
        match.returns?.let { add("return $it") }
        if (match.strings.isNotEmpty()) add("strings ${match.strings}")
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

    private val CAPTURE = Regex("""@\{(\w+)\}""")
    private const val REGISTER = "([vp]\\d+)"
}
