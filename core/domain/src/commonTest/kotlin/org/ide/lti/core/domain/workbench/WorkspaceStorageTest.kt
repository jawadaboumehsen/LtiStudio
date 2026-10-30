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
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceStorageTest {
    @Test
    fun foldersComeLargestFirstAndTheRestIsOther() {
        val usage = WorkspaceStorage.parseUsage(
            "firmware/downloaded\t5000\nwork\t11000\nout/images\t3000\nworkbench\t100\n.\t20000\n",
        )
        assertEquals(listOf("work", "firmware/downloaded", "out/images", "workbench", "other"), usage.map { it.path })
        assertEquals(20000L - 11000 - 5000 - 3000 - 100, usage.last().bytes)
        assertTrue(usage.first { it.path == "work" }.cleanable)
        assertTrue(!usage.first { it.path == "firmware/downloaded" }.cleanable)
    }

    @Test
    fun onlyFoldersTheBuildMakesAgainAreFreedAndNeverDuringABuild() {
        assertNull(WorkspaceStorage.cleanRefusal("work", runActive = false))
        assertNotNull(WorkspaceStorage.cleanRefusal("work", runActive = true))
        assertNotNull(WorkspaceStorage.cleanRefusal("restore", runActive = false))
        assertNotNull(WorkspaceStorage.cleanRefusal("firmware/downloaded", runActive = false))
        assertNotNull(WorkspaceStorage.cleanRefusal("../etc", runActive = false))
    }
}
