/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.diff

import org.ide.lti.core.domain.diff.ConfigDiffEngine
import org.ide.lti.core.domain.diff.DiffKind
import org.ide.lti.core.domain.diff.PropertyRiskLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PropertyDiffViewerTest {

    @Test
    fun testDiffResultMetricsMatchEntries() {
        val original = """
            ro.build.version.release=14
            ro.secure=1
            ro.debuggable=0
        """.trimIndent()

        val modified = """
            ro.build.version.release=15
            ro.secure=0
            persist.vendor.test=true
        """.trimIndent()

        val result = ConfigDiffEngine.computeDiff(original, modified)

        assertEquals(1, result.addedCount)
        assertEquals(1, result.removedCount)
        assertEquals(2, result.modifiedCount)

        val secureEntry = result.entries.first { it.key == "ro.secure" }
        assertEquals(DiffKind.MODIFIED, secureEntry.kind)
        assertEquals(PropertyRiskLevel.WARNING, secureEntry.riskLevel)
        assertEquals("1", secureEntry.oldValue)
        assertEquals("0", secureEntry.newValue)
    }

    @Test
    fun testRollbackCallbackInvoked() {
        var rolledBack = false
        val snapshot = ConfigDiffEngine.createSnapshot(
            id = "snap-1",
            name = "Baseline",
            content = "ro.build=1",
        )

        val rollbackAction: () -> Unit = {
            val content = ConfigDiffEngine.rollbackTo(snapshot)
            if (content.isNotEmpty()) rolledBack = true
        }

        rollbackAction()
        assertTrue(rolledBack)
    }
}
