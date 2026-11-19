/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import org.ide.lti.core.domain.pipeline.stages.AppliedModulePlan
import org.ide.lti.core.domain.pipeline.stages.AppliedOperationSummary
import org.ide.lti.core.domain.workbench.DeviceCheckResult
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunRecordTest {
    @Test
    fun aRecordGivesBackItsPlanRemovedAppsAndKeys() {
        val plan = AppliedModulePlan(
            lockfileEntries = listOf(
                PluginLockfileEntry(publisher = "lti", id = "lti.pif", version = "1.2.0", contentDigest = "c1"),
            ),
            operationIds = listOf("op1"),
            operations = listOf(AppliedOperationSummary("op1", "TextPatch", "system/build.prop")),
            resolvedPlanDigests = listOf("d1"),
        )
        val planJson = ConfigurationSnapshot.canonicalJson.encodeToString(AppliedModulePlan.serializer(), plan)
        val text = "record\tyes\nmodules-plan.json\t$planJson\n" +
            "debloat-result.json\t{\"removals\":[],\"kept\":[],\"logicalBytesFreed\":1048576}\n" +
            "cert\tAB:CD\navb\t0123\n"
        val record = RunRecord.parse(text)
        assertTrue(record.present)
        assertEquals(plan, record.plan)
        assertEquals(1_048_576L, record.debloat?.logicalBytesFreed)
        assertEquals("AB:CD", record.certificate)
        assertEquals("0123", record.avbKey)
        assertTrue("lti.pif 1.2.0" in record.changes(snapshot = null))
    }

    @Test
    fun aRunWithoutARecordAndAStageThatWasNotPartOfItReadAsAbsent() {
        assertFalse(RunRecord.parse("").present)
        // Only the keys: a run through Assemble keeps no mod plan and no removed apps.
        val keysOnly = RunRecord.parse("record\tyes\ncert\tAB\n")
        assertTrue(keysOnly.present)
        assertNull(keysOnly.plan)
        assertNull(keysOnly.debloat)
        assertNull(keysOnly.avbKey)
    }

    @Test
    fun theHistoryReadsEachRunsPhoneCheckAndSkipsAnUnreadableOne() {
        val check = DeviceCheckResult("r1", "2026-11-19T10:00:00Z", "ABC", "Phone", emptyList())
        val json = ConfigurationSnapshot.canonicalJson.encodeToString(DeviceCheckResult.serializer(), check)
        val checks = RunRecord.parseDeviceChecks("r1\t$json\nr2\t{broken\n\n")
        assertEquals(mapOf("r1" to check), checks)
    }
}
