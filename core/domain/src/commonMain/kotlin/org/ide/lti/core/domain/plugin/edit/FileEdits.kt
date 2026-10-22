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

import org.ide.lti.core.domain.plugin.FileMutationCodes

/**
 * The one engine for the text and property edits of a work-tree file, used by the build
 * (`FileMutationAdapter`) and by the SDK's mod check alike. It works on the file's text; reading and writing the
 * file is the caller's.
 */
object FileEdits {

    /** Why [key] cannot be a property key, or null when it can. */
    fun propertyKeyProblem(key: String): String? {
        val invalid = key.isEmpty() || key.any { it in INVALID_KEY_CHARS }
        return "Invalid property key '$key': must not be empty or contain '=', newline, or NUL".takeIf { invalid }
    }

    /**
     * [existing] with `key=value` set: the first non-comment line of that key is replaced, else the line is added at
     * the end. Lines are rewritten with `\n`.
     */
    fun propertyPatch(existing: String, key: String, value: String): EditResult {
        val problem = propertyKeyProblem(key)
        if (problem != null) return EditResult.Failed(FileMutationCodes.PROPERTY_KEY_INVALID, problem)
        val lines = existing.replace("\r\n", "\n").split("\n").let { if (it.lastOrNull() == "") it.dropLast(1) else it }
        val at = lines.indexOfFirst { isKeyLine(it, key) }
        val patched = if (at == -1) lines + "$key=$value" else lines.toMutableList().also { it[at] = "$key=$value" }
        return EditResult.Changed(patched.joinToString("") { "$it\n" }, key)
    }

    private val INVALID_KEY_CHARS = setOf('=', '\n', '\r', '\u0000')

    private fun isKeyLine(line: String, key: String): Boolean {
        val eq = line.indexOf('=')
        return !line.trimStart().startsWith("#") && eq != -1 && line.substring(0, eq).trim() == key
    }

    /**
     * [existing] with the one occurrence of [context] replaced by [replacement]. A non-empty [expectedSha256] pins
     * the file first against [actualSha256] (the file's own hash); an empty one anchors the edit by [context] alone.
     * [member] names the file in the messages.
     */
    fun textPatch(
        existing: String,
        context: String,
        replacement: String,
        expectedSha256: String,
        actualSha256: String,
        member: String,
    ): EditResult {
        val pinned = expectedSha256.isNotEmpty() && !actualSha256.equals(expectedSha256, ignoreCase = true)
        val count = countOccurrences(existing, context)
        return when {
            pinned -> EditResult.Failed(
                FileMutationCodes.PREIMAGE_MISMATCH,
                "Preimage mismatch for '$member': expected $expectedSha256, actual $actualSha256",
            )
            count == 0 -> EditResult.Failed(
                FileMutationCodes.TEXT_CONTEXT_NOT_FOUND,
                "Context anchor '$context' not found in '$member'",
            )
            count > 1 -> EditResult.Failed(
                FileMutationCodes.TEXT_CONTEXT_AMBIGUOUS,
                "Context anchor '$context' is ambiguous (found $count times) in '$member'",
            )
            else -> {
                val at = existing.indexOf(context)
                val patched = existing.substring(0, at) + replacement + existing.substring(at + context.length)
                EditResult.Changed(patched, member)
            }
        }
    }

    private fun countOccurrences(text: String, context: String): Int {
        if (context.isEmpty()) return 0
        var count = 0
        var from = 0
        while (true) {
            val found = text.indexOf(context, from)
            if (found == -1) return count
            count++
            from = found + context.length
        }
    }
}
