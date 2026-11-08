/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

/** What a path is, for rules that apply to one kind only (`--` file, `-d` folder, `-l` link in file_contexts). */
public enum class EntryKind { File, Directory, Link }

/**
 * The SELinux rules a ROM labels its files with (`plat_file_contexts`, `vendor_file_contexts`, …), looked up as
 * Android's restorecon does: a rule is `<regex> [<type>] <context>` matched against the whole device path; rules
 * without regex characters (exact paths) are tried first, then the last matching rule wins. A `<<none>>` context
 * means "leave it unlabelled", which is no answer here.
 */
public class FileContextRules private constructor(private val specs: List<Spec>) {
    private class Spec(val regex: Regex, val kind: EntryKind?, val context: String)

    public val size: Int get() = specs.size

    /** The context [devicePath] (`/system/bin/sh`) gets as a [kind], or null when no rule names one. */
    public fun label(devicePath: String, kind: EntryKind): String? = specs.lastOrNull { spec ->
        (spec.kind == null || spec.kind == kind) && spec.regex.matches(devicePath)
    }?.context?.takeIf { it != NONE }

    public companion object {
        public val EMPTY: FileContextRules = FileContextRules(emptyList())
        private const val NONE = "<<none>>"
        private val FIELDS = Regex("""\s+""")

        /**
         * The rules of [texts] in the order Android loads them (plat, system_ext, product, vendor, odm). A line whose
         * regex Kotlin cannot compile is skipped: one odd rule must not stop the rest from labelling.
         */
        public fun parse(texts: List<String>): FileContextRules {
            val all = texts.flatMap { text ->
                text.lineSequence().mapNotNull { raw ->
                    val line = raw.trim()
                    if (line.isEmpty() || line.startsWith("#")) return@mapNotNull null
                    val f = line.split(FIELDS)
                    val (pattern, kind, context) = when (f.size) {
                        2 -> Triple(f[0], null, f[1])
                        3 -> Triple(f[0], kindOf(f[1]) ?: return@mapNotNull null, f[2])
                        else -> return@mapNotNull null
                    }
                    val regex = runCatching { Regex(pattern) }.getOrNull() ?: return@mapNotNull null
                    Spec(regex, kind, context) to hasMetaChars(pattern)
                }
            }
            // As libselinux sorts them: regex rules in file order, then exact paths (so those are found first).
            val (regexes, exact) = all.partition { it.second }
            return FileContextRules((regexes + exact).map { it.first })
        }

        /** `--` file, `-d` folder, `-l` link; other types (devices, sockets, pipes) never occur in an image tree. */
        private fun kindOf(type: String): EntryKind? = when (type) {
            "--" -> EntryKind.File
            "-d" -> EntryKind.Directory
            "-l" -> EntryKind.Link
            else -> null
        }

        /** libselinux's test: any of `.^$?*+|[({` not escaped by a backslash makes it a regex, not an exact path. */
        internal fun hasMetaChars(pattern: String): Boolean {
            var i = 0
            while (i < pattern.length) {
                val c = pattern[i]
                if (c == '\\') {
                    i += 2
                    continue
                }
                if (c in ".^$?*+|[({") return true
                i++
            }
            return false
        }
    }
}
