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

/**
 * Applies a member's unified-diff hunks in order, each anchored by its unique context (as `git apply`). A single
 * `@@ -0,0 …` hunk creates the member; `@@ replace @@` swaps it whole; `@@ after: text @@` / `@@ before: text @@`
 * insert next to the one line containing the text.
 */
@Suppress("ReturnCount")
object SmaliTextEditor {

    private data class Hunk(
        val contextAndRemoved: String,
        val contextAndAdded: String,
        val creates: Boolean,
        /** The first line of the hunk in the file it was made from (`@@ -start,…`), when the diff says. */
        val oldStart: Int? = null,
        /** `after: text` / `before: text` or `replace`. */
        val anchor: String? = null,
    )

    @Suppress("CyclomaticComplexMethod")
    fun apply(existing: String?, unifiedDiff: String, member: String): EditResult {
        val hunks = parseHunks(unifiedDiff)
        val creates = hunks.size == 1 && hunks.single().creates
        fun fail(code: String, message: String) = EditResult.Failed(code, message)
        if (existing == null && !creates) {
            return fail(ComponentPatchCodes.SMALI_MEMBER_MISSING, "Smali member '$member' not found in the component")
        }
        if (existing != null && creates) {
            return fail(ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS, "Smali member '$member' to create already exists")
        }
        if (creates || hunks.singleOrNull()?.anchor == REPLACE) {
            return EditResult.Changed(hunks.single().contextAndAdded + "\n", member)
        }
        var content = existing.orEmpty().replace("\r\n", "\n")
        var lineDelta = 0
        for (parsed in hunks) {
            val hunk = when (val anchored = anchorAt(parsed, content)) {
                null -> return fail(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, "Anchor line not found in '$member'")
                AMBIGUOUS_ANCHOR -> return fail(
                    ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                    "Anchor line '${parsed.anchor}' occurs more than once in '$member'",
                )
                else -> anchored
            }
            // As git apply: identical context is told apart by the hunk's line (shifted by earlier hunks).
            val expectedLine = hunk.oldStart?.let { it + lineDelta }
            val idx = when (val found = locate(content, hunk.contextAndRemoved, expectedLine)) {
                NOT_FOUND -> return fail(
                    ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                    "Patch context anchor not found in '$member'",
                )
                AMBIGUOUS -> return fail(
                    ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                    "Patch context anchor is ambiguous in '$member'",
                )
                else -> found
            }
            content = content.substring(0, idx) + hunk.contextAndAdded +
                content.substring(idx + hunk.contextAndRemoved.length)
            lineDelta += hunk.contextAndAdded.lines().size - hunk.contextAndRemoved.lines().size
        }
        return EditResult.Changed(content, member)
    }

    /** An `@@ after: text @@` / `@@ before: text @@` hunk as a context hunk on the one line containing the text. */
    private fun anchorAt(hunk: Hunk, content: String): Hunk? {
        val anchor = hunk.anchor ?: return hunk
        val after = anchor.startsWith("after: ")
        val text = anchor.substringAfter(": ")
        val lines = content.lines().filter { text in it }
        val line = when (lines.size) {
            0 -> return null
            1 -> lines.single()
            else -> return AMBIGUOUS_ANCHOR
        }
        val added = hunk.contextAndAdded
        return hunk.copy(
            contextAndRemoved = line,
            contextAndAdded = if (after) "$line\n$added" else "$added\n$line",
            anchor = null,
        )
    }

    /**
     * Index of [text] in [content]: its only occurrence, or the one starting nearest [expectedLine] when it
     * occurs more than once ([AMBIGUOUS] when two are equally near or no line is known).
     */
    private fun locate(content: String, text: String, expectedLine: Int?): Int {
        if (text.isEmpty()) return NOT_FOUND
        val hits = generateSequence(content.indexOf(text)) { content.indexOf(text, it + 1).takeIf { i -> i >= 0 } }
            .takeWhile { it >= 0 }.toList()
        if (hits.size <= 1) return hits.firstOrNull() ?: NOT_FOUND
        if (expectedLine == null) return AMBIGUOUS
        val byDistance = hits.groupBy { idx -> kotlin.math.abs(lineOf(content, idx) - expectedLine) }
        val nearest = byDistance.getValue(byDistance.keys.min())
        return nearest.singleOrNull() ?: AMBIGUOUS
    }

    private fun lineOf(content: String, index: Int) = 1 + (0 until index).count { content[it] == '\n' }

    /** The hunks of a unified diff (git headers ignored); text with no `@@` header is one hunk. */
    private fun parseHunks(diffText: String): List<Hunk> {
        val lines = diffText.replace("\r\n", "\n").lines()
        val groups = mutableListOf<Pair<String?, MutableList<String>>>()
        for (line in lines) {
            when {
                line.startsWith("@@") -> groups += line to mutableListOf()
                isGitHeader(line) -> Unit
                groups.isEmpty() && (line.startsWith(" ") || line.startsWith("-") || line.startsWith("+")) ->
                    groups += null to mutableListOf(line)
                groups.isNotEmpty() -> groups.last().second += line
            }
        }
        return groups.map { (header, body) -> toHunk(header, body) }
    }

    private fun isGitHeader(line: String) = line.startsWith("diff ") || line.startsWith("index ") ||
        line.startsWith("---") || line.startsWith("+++") || line.startsWith("new file") || line.startsWith("\\")

    private fun toHunk(header: String?, body: List<String>): Hunk {
        // A diff ends with a newline: the last, empty "line" is not a blank context line.
        val hunkLines = if (body.lastOrNull()?.isEmpty() == true) body.dropLast(1) else body
        val removed = mutableListOf<String>()
        val added = mutableListOf<String>()
        for (line in hunkLines) {
            when {
                line.startsWith("-") -> removed += line.substring(1)
                line.startsWith("+") -> added += line.substring(1)
                else -> line.removePrefix(" ").let {
                    removed += it
                    added += it
                }
            }
        }
        return Hunk(
            contextAndRemoved = removed.joinToString("\n"),
            contextAndAdded = added.joinToString("\n"),
            creates = header?.startsWith("@@ -0,0 ") == true,
            oldStart = header?.let { Regex("""^@@ -(\d+)""").find(it)?.groupValues?.get(1)?.toIntOrNull() },
            anchor = header?.let { Regex("""^@@ ((?:after|before): .+|replace) @@$""").find(it)?.groupValues?.get(1) },
        )
    }

    private const val REPLACE = "replace"
    private val AMBIGUOUS_ANCHOR = Hunk("", "", creates = false)
    private const val NOT_FOUND = -1
    private const val AMBIGUOUS = -2
}
