/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ToolInventory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class InstalledToolsTest {
    private val tools = ToolchainSetupState.defaultToolsMatrix()

    @Test
    fun anInventoryWithoutTheGroupLeavesEveryToolPendingWithNoPath() {
        val after = toolsFromInventory(tools, ToolInventory(emptyMap()), "/home/u")
        assertTrue(after.all { it.status == StepStatus.PENDING && it.path == null })
    }

    @Test
    fun aFoundFileSucceedsUnlessTheToolFailedEarlierAndAMissingOneKeepsItsStatus() {
        val tool = tools.first()
        val found = toolFromFile(tool.copy(status = StepStatus.RUNNING), "/bin/x", exists = true)
        assertEquals(StepStatus.SUCCESS, found.status)
        assertEquals("/bin/x", found.path)
        assertEquals(StepStatus.FAILED, toolFromFile(tool.copy(status = StepStatus.FAILED), "/bin/x", true).status)
        val missing = toolFromFile(tool.copy(status = StepStatus.RUNNING, path = "/old"), "/bin/x", exists = false)
        assertEquals(StepStatus.RUNNING, missing.status)
        assertNull(missing.path)
    }

    @Test
    fun onlyReadyToolsBecomeEvidenceAndATestedOneCountsAsVerified() {
        val (ready, tested, failed) = tools.take(3)
        val evidence = planEvidence(
            listOf(
                ready.copy(status = StepStatus.SUCCESS),
                tested.copy(status = StepStatus.SUCCESS, lastTested = "Just now"),
                failed.copy(status = StepStatus.FAILED),
            ),
            binDir = "/home/u/LtiRomTools/bin",
            now = 42L,
        )
        assertEquals(setOf(ready.id, tested.id), evidence.keys)
        assertEquals(false, evidence.getValue(ready.id).isVerified)
        assertEquals(true, evidence.getValue(tested.id).isVerified)
        assertEquals(42L, evidence.getValue(tested.id).lastVerifiedTimestamp)
    }
}
