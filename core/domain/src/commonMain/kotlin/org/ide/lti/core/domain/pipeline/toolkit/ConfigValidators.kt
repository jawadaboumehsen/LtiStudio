/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

/**
 * Domain syntax validators for Android configuration files (FR-031, T067).
 * Validates fstab, build.prop, fs_config, and file_contexts syntax.
 */
public object ConfigValidators {
    private val ID_RANGE = 0L..0xFFFFFFFFL
    private val MODE_RANGE = 0..0b111_111_111_111
    private val WHITESPACE = "\\s+".toRegex()
    private const val OCTAL = 8
    private const val FS_CONFIG_FIELDS = 5
    private val CAPABILITIES = Regex("capabilities=(0x[0-9a-fA-F]{1,16}|[0-9]{1,20})")
    private val SELINUX_CONTEXT = Regex("[^:\\s]+:[^:\\s]+:[^:\\s]+:\\S+")
    private const val NO_CONTEXT = "<<none>>"
    private const val MAX_CONTEXT_FIELDS = 3

    /** The non-empty, non-comment lines of [content] with their 1-based line numbers. */
    private fun entries(content: String): List<Pair<Int, String>> = content.lines()
        .mapIndexed { idx, line -> idx + 1 to line.trim() }
        .filter { (_, line) -> line.isNotEmpty() && !line.startsWith("#") }

    public fun validateFstab(content: String): List<String> = entries(content).mapNotNull { (n, line) ->
        val tokens = line.split(WHITESPACE)
        "Line $n: fstab entries require at least 5 fields (<src> <mnt> <type> <flags> <fs_mgr>), found ${tokens.size}"
            .takeIf { tokens.size < 5 }
    }

    public fun validateBuildProp(content: String): List<String> = entries(content).mapNotNull { (n, line) ->
        val key = line.substringBefore('=', missingDelimiterValue = "").trim()
        when {
            line.indexOf('=') <= 0 -> "Line $n: Property entry must contain 'key=value'"
            key.any { it == ' ' || it == '\t' } -> "Line $n: Property key cannot contain whitespace: '$key'"
            else -> null
        }
    }

    public fun validateFsConfig(content: String): List<String> = entries(content).flatMap { (n, line) ->
        val tokens = line.split(WHITESPACE)
        if (tokens.size < 4) {
            listOf("Line $n: fs_config requires at least 4 fields (<path> <uid> <gid> <mode>), found ${tokens.size}")
        } else {
            listOfNotNull(
                // An owner is an unsigned 32-bit id; a mode holds permission and setuid/setgid/sticky bits only.
                "Line $n: Invalid UID number '${tokens[1]}'".takeIf { tokens[1].toLongOrNull() !in ID_RANGE },
                "Line $n: Invalid GID number '${tokens[2]}'".takeIf { tokens[2].toLongOrNull() !in ID_RANGE },
                "Line $n: Invalid octal mode '${tokens[3]}'".takeIf { tokens[3].toIntOrNull(OCTAL) !in MODE_RANGE },
                tokens.getOrNull(FS_CONFIG_FIELDS - 1)?.takeIf { !CAPABILITIES.matches(it) }
                    ?.let { "Line $n: Invalid capabilities '$it'" },
                "Line $n: fs_config has at most $FS_CONFIG_FIELDS fields, found ${tokens.size}"
                    .takeIf { tokens.size > FS_CONFIG_FIELDS },
            )
        }
    }

    public fun validateFileContexts(content: String): List<String> = entries(content).mapNotNull { (n, line) ->
        val tokens = line.split(WHITESPACE)
        when {
            tokens.size !in 2..MAX_CONTEXT_FIELDS ->
                "Line $n: file_contexts requires <regex_path> [file_type] <selinux_context>"

            runCatching { Regex(tokens.first()) }.isFailure -> "Line $n: Invalid path regex '${tokens.first()}'"

            tokens.last() != NO_CONTEXT && !SELINUX_CONTEXT.matches(tokens.last()) ->
                "Line $n: Invalid SELinux context format '${tokens.last()}' (expected user:role:type:level)"

            else -> null
        }
    }
}
