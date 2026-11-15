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

import kotlin.test.Test
import kotlin.test.assertEquals

class ModLabTest {
    @Test
    fun rowsSurviveTheTextResultWithTabsAndLineBreaksInTheReason() {
        val rows = listOf(
            LabRow("lti:adb", "prop-1", LabStatus.CHECKED),
            LabRow("lti:hma", "edit-2", LabStatus.REFUSED, "TEXT_CONTEXT_NOT_FOUND: no\tmatch\nin file"),
            LabRow("lti:hma", "smali-3", LabStatus.UNCHECKED, "edits system/framework/services.jar"),
            LabRow("lti:pif", "copy-4", LabStatus.NOT_APPLICABLE, "only for Android 14"),
        )
        val back = LabRow.decode(LabRow.encode(rows))
        assertEquals(rows.map { it.copy(reason = it.reason.replace(Regex("[\t\n]"), " ")) }, back)
        assertEquals(emptyList(), LabRow.decode("ok\nnot a row"))
    }

    @Test
    fun aRowsBeforeAndAfterSurvivesTheTextResultWithItsLineBreaksAndTabs() {
        val detail = "=== smali/X.smali\n@@ line 1 @@\n- a\n+ \tb"
        val back = LabRow.decode(LabRow.encode(listOf(LabRow("m", "op", LabStatus.CHECKED, "ok", detail))))
        assertEquals("=== smali/X.smali\n@@ line 1 @@\n- a\n+     b", back.single().detail)
        // A row written before details existed still reads.
        assertEquals("", LabRow.decode("m\top\tCHECKED\tok").single().detail)
    }
}
