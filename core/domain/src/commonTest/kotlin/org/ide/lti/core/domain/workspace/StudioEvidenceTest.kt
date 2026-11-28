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
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.stages.VintfSkuResult
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.plugin.LabRow
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.plugin.ModLab
import org.ide.lti.core.domain.workbench.DeviceTest
import org.ide.lti.core.domain.workbench.DeviceVerdict
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudioEvidenceTest {
    @Test
    fun modEvidenceDistinguishesCheckedFailedAndMissingOperations() {
        val lab = ModLab(
            listOf(
                LabRow("p:ok", "match", LabStatus.CHECKED),
                LabRow("p:broken", "anchor", LabStatus.REFUSED, "Missing anchor"),
                LabRow("p:unread", "decode", LabStatus.UNCHECKED, "Decoder unavailable"),
            ),
            emptyList(),
        )
        val results = FirmwareUpgrade.assessMods(listOf("p:ok", "p:broken", "p:unread", "p:empty"), lab)
        assertEquals(
            listOf(
                BaselineVerdict.PASSES,
                BaselineVerdict.FAILS,
                BaselineVerdict.NEEDS_EVIDENCE,
                BaselineVerdict.NEEDS_EVIDENCE,
            ),
            results.map { it.verdict },
        )
        assertEquals(listOf("anchor · Missing anchor"), results[1].evidence)
        assertEquals(listOf("decode · Decoder unavailable"), results[2].evidence)
    }

    @Test
    fun selectedSkuIsEvaluatedAgainstItsOwnResults() {
        val vintf = VintfAssessment(
            VintfStatus.INCOMPATIBLE,
            skuResults = listOf(
                VintfSkuResult("retail", "global", VintfStatus.COMPATIBLE),
                VintfSkuResult("carrier", "global", VintfStatus.INCOMPATIBLE),
                VintfSkuResult("retail", "test", VintfStatus.COMPATIBLE, provisional = true),
            ),
        )
        assertEquals(BaselineVerdict.PASSES, FirmwareUpgrade.baselineVintf(vintf, BaselineSku("retail", "global")))
        assertEquals(BaselineVerdict.FAILS, FirmwareUpgrade.baselineVintf(vintf, BaselineSku("carrier", "global")))
        assertEquals(BaselineVerdict.NEEDS_EVIDENCE, FirmwareUpgrade.baselineVintf(vintf, BaselineSku("retail")))
        assertEquals(BaselineVerdict.NEEDS_EVIDENCE, FirmwareUpgrade.baselineVintf(vintf, BaselineSku("absent")))
    }

    @Test
    fun updateReviewSeparatesNewAndExistingFailures() {
        val existing = LabRow("p:mod", "keep", LabStatus.REFUSED, "Missing file")
        val before = ModLab(listOf(existing, LabRow("p:mod", "new", LabStatus.CHECKED)), emptyList())
        val after = ModLab(listOf(existing, LabRow("p:mod", "new", LabStatus.REFUSED, "Changed anchor")), emptyList())
        val review = summarizeUpdate(before, after)
        assertTrue("New failure · p:mod new: Changed anchor" in review)
        assertTrue("Existing failure · p:mod keep: Missing file" in review)
    }

    @Test
    fun deviceDiagnosticsUseExactPackageIdsAndTreatModPathsAsPossibleLinks() {
        val answers = "stamp=run\nbooted=1\ncrash-status=ok\ncrash=org.demo:remote\ncrash-log=FATAL EXCEPTION"
        val plan = AppliedModulePlan(
            emptyList(),
            emptyList(),
            listOf(
                AppliedOperationSummary("match", "Replace", "system/app/org.demo.apk", "p:mod"),
                AppliedOperationSummary("other", "Replace", "system/app/org.demo2.apk", "p:other"),
            ),
            emptyList(),
        )
        val evidence = DeviceTest.diagnostics(answers, RunRecord.EMPTY.copy(plan = plan))
        assertEquals(listOf("FATAL EXCEPTION"), evidence.crashLog)
        assertEquals(listOf("p:mod · match · system/app/org.demo.apk"), evidence.links.single().possibleModEdits)
        assertEquals(emptyList(), evidence.links.single().recordedRemovals)
        val unavailable = DeviceTest.items("stamp=run\nbooted=1\ncrash-status=unavailable", "run")
        assertEquals(DeviceVerdict.FAIL, unavailable.last().verdict)
    }
}
