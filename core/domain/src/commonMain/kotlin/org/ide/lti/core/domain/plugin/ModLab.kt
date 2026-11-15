/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.domain.pipeline.HostRequest

/**
 * Checks every operation of [apply]'s plans, in plan order and each against what the earlier ones left, without
 * changing the work tree; the result is [LabRow.encode]d, one row per operation. With [decode], APK/JAR edits are
 * applied to their decoded packages in memory too (about a minute per package), else marked for the build.
 */
public data class LabModules(val apply: ApplyModules, val decode: Boolean = false) : HostRequest

/** What the check could say about one operation. */
public enum class LabStatus {
    /** Its target, preimage hash, anchor or key match the assembled tree. */
    CHECKED,

    /** The build would refuse it: [LabRow.reason] says why. */
    REFUSED,

    /** An APK/JAR edit: only the build, which decodes the package, checks it. */
    UNCHECKED,

    /** The mod itself leaves it out for this firmware or these settings. */
    NOT_APPLICABLE,
}

/** One operation of one mod (`publisher:id`) in the mod lab. */
public data class LabRow(
    val mod: String,
    val operationId: String,
    val status: LabStatus,
    val reason: String = "",
    /** What the edits of this row's package change, before and after (`LineDiff.hunk` lines); empty for most rows. */
    val detail: String = "",
) {
    public companion object {
        /** One tab-separated line per row: the host step result is text (the detail's line breaks as `\u001F`). */
        public fun encode(rows: List<LabRow>): String = rows.joinToString("\n") { row ->
            listOf(
                row.mod,
                row.operationId,
                row.status.name,
                row.reason.replace(Regex("[\t\n\r]"), " "),
                row.detail.replace("\t", "    ").replace("\r", "").replace('\n', DETAIL_BREAK),
            ).joinToString("\t")
        }

        public fun decode(text: String): List<LabRow> = text.lines().mapNotNull { line ->
            val parts = line.split('\t', limit = 5).takeIf { it.size >= 4 } ?: return@mapNotNull null
            LabStatus.entries.firstOrNull { it.name == parts[2] }?.let { status ->
                LabRow(parts[0], parts[1], status, parts[3], parts.getOrNull(4).orEmpty().replace(DETAIL_BREAK, '\n'))
            }
        }

        private const val DETAIL_BREAK = '\u001F'
    }
}

/** The mod lab's answer: a row per operation, and what stopped the plans before any operation was checked. */
public data class ModLab(val rows: List<LabRow>, val problems: List<String>)
