/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import org.ide.lti.core.domain.plugin.LabRow

/**
 * One file of the work tree the enabled mods change, in the mod lab's "By package" view: every mod operation on
 * it (in plan order), and for an APK or JAR the methods its edits change and the files inside it they touch.
 */
internal data class LabPackage(
    val target: String,
    val rows: List<LabRow>,
    val methods: List<String>,
    val files: List<String>,
)

/**
 * The lab's rows by the file they change ([targetOf] a row: `partition/path`, null when its operation is not in the
 * plans), packages touched by several mods first, then by path.
 */
internal fun labByPackage(rows: List<LabRow>, targetOf: (LabRow) -> String?): List<LabPackage> =
    rows.groupBy { targetOf(it) ?: UNKNOWN }.map { (target, group) ->
        LabPackage(
            target = target,
            rows = group,
            methods = group.flatMap { it.methods }.distinct(),
            files = group.flatMap { it.files }.distinct(),
        )
    }.sortedWith(compareByDescending<LabPackage> { p -> p.rows.map { it.mod }.distinct().size }.thenBy { it.target })

/** The target of a row whose operation the plans no longer hold (a mod changed since the check). */
internal const val UNKNOWN = "(not in the current plans)"
