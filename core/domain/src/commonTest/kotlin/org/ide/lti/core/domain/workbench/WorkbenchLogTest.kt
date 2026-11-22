/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class WorkbenchLogTest {
    @Test
    fun identicalLinesStillHaveDistinctKeysAtCapacityAndAfterClear() {
        val log = WorkbenchLog(now = { 0 })
        log.add(LogLevel.INF, List(WorkbenchLog.MAX) { "same" }.joinToString("\n"))
        val first = log.lines.value
        assertEquals(WorkbenchLog.MAX, first.map(LogLine::id).toSet().size)

        log.add(LogLevel.INF, "same")
        val rotated = log.lines.value
        assertEquals(first[1].id, rotated.first().id)
        assertNotEquals(first.last().id, rotated.last().id)

        log.clear()
        log.add(LogLevel.INF, "same")
        assertNotEquals(rotated.last().id, log.lines.value.single().id)
    }
}
