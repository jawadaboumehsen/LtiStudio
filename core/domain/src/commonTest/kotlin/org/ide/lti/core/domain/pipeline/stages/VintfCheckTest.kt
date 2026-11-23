/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [VintfCheck] judging what its script printed for PQ84P01 (vendor SKU sun, kernel 6.6.92, sepolicy 202404). */
class VintfCheckTest {
    private fun output(
        mappings: String = "202404.cil\n34.0.cil",
        kernel: String = "ok 6.6.92-android15-8-g3637f4904cf5-ab13944661-4k",
        result: String = "@@@ result sun|- 0\n",
    ) = "@@@ mapping\n$mappings\n@@@ vendor-sepolicy\n202404\n@@@ kernel $kernel\n$result@@@ done 1\n"

    /** checkvintf's real answer when the boot kernel lacks a config the level-202404 6.6 requirements ask for. */
    private val incompatible = listOf(
        "@@@ result sun|- 65",
        "ERROR: files are incompatible: Runtime info and framework compatibility matrix are incompatible: " +
            "No compatible kernel requirement found (kernel FCM version = 202404).",
        "For kernel requirements at matrix level 202404, Kernel config errors:",
        "Missing config CONFIG_ADVISE_SYSCALLS",
        ": Success",
    ).joinToString("\n", postfix = "\n")

    @Test
    fun android10ReportsCompatibleAndIncompatibleVendorSkuIndependently() {
        val report = VintfCheck.assess(
            """
            @@@ android 10
            @@@ first-api 29
            @@@ mapping
            29.0.cil
            @@@ vendor-sepolicy
            29.0
            @@@ kernel ok 4.14.180
            @@@ result carrier|- 0
            @@@ result retail|- 65
            ERROR: Required HAL android.hardware.example@1.0::IExample/default is missing.
            @@@ done 2
            """.trimIndent(),
        )
        assertEquals("10", report.androidRelease)
        assertEquals(29, report.firstApiLevel)
        assertEquals(VintfStatus.INCOMPATIBLE, report.status)
        assertEquals(
            listOf(
                VintfSkuResult("carrier", null, VintfStatus.COMPATIBLE),
                VintfSkuResult("retail", null, VintfStatus.INCOMPATIBLE),
            ),
            report.skuResults,
        )
    }

    @Test
    fun android14ReportsMissingEvidenceEvenWhenAnotherOdmSkuPasses() {
        val report = VintfCheck.assess(
            """
            @@@ android 14
            @@@ first-api 34
            @@@ mapping
            34.0.cil
            @@@ vendor-sepolicy
            34.0
            @@@ kernel ok 5.15.148
            @@@ missing apex|Could not read VINTF fragments from APEX system/system/apex/com.android.test.apex.
            @@@ result global|wifi 0
            @@@ result global|cellular 70
            getDeviceHalManifest: -22 VINTF parse error: Illformed file: /odm/etc/vintf/manifest_cellular.xml: Not a valid XML
            getDeviceHalManifest: -22 VINTF parse error: Illformed file: /odm/etc/vintf/manifest_cellular.xml: Not a valid XML
            ERROR: No such device: No device manifest file from device or from update package
            @@@ done 2
            """.trimIndent(),
        )
        assertEquals("14", report.androidRelease)
        assertEquals(34, report.firstApiLevel)
        assertEquals(VintfStatus.INCOMPLETE, report.status)
        assertEquals(
            listOf(
                VintfSkuResult("global", "wifi", VintfStatus.COMPATIBLE),
                VintfSkuResult("global", "cellular", VintfStatus.INCOMPLETE),
            ),
            report.skuResults,
        )
        assertEquals(
            listOf(VintfEvidenceKind.APEX, VintfEvidenceKind.FIRMWARE_METADATA),
            report.evidence.map { it.kind },
        )
        val missing = report.missingEvidence[1]
        val manifest = "/odm/etc/vintf/manifest_cellular.xml"
        assertTrue(missing.contains(manifest))
        assertEquals(missing.indexOf(manifest), missing.lastIndexOf(manifest))
    }

    @Test
    fun aCompatibleSystemHasNoRefusalAndNoNote() {
        assertEquals(emptyList(), VintfCheck.refusals(output()))
        assertEquals(emptyList(), VintfCheck.notes(output()))
        assertEquals(VintfStatus.COMPATIBLE, VintfCheck.assess(output()).status)
    }

    @Test
    fun anIncompatibilityIsRefusedWithCheckvintfsReasonUntilAccepted() {
        val text = output(result = incompatible)
        assertEquals(VintfStatus.INCOMPATIBLE, VintfCheck.assess(text).status)
        val refusals = VintfCheck.refusals(text)
        assertEquals(1, refusals.size, "$refusals")
        val refusal = refusals[0]
        val (id, message) = VintfCheck.splitMismatchMessage(refusal.substringAfter(": "))
        assertTrue(message.startsWith("the system does not meet"), refusal)
        assertTrue("(vendor SKU sun): Runtime info and framework compatibility matrix" in refusal, refusal)
        assertTrue("Missing config CONFIG_ADVISE_SYSCALLS" in refusal, refusal)
        assertTrue("files are incompatible" !in refusal && ": Success" !in refusal, refusal)
        assertEquals(emptyList(), VintfCheck.refusals(text, setOf(requireNotNull(id))))
    }

    @Test
    fun aMissingSelinuxMappingIsRefusedEvenWhenIncompatibilityIsAccepted() {
        val refusals = VintfCheck.refusals(output(mappings = "33.0.cil\n34.0.cil"), setOf("arbitrary-acknowledgement"))
        assertEquals(1, refusals.size, "$refusals")
        assertTrue(refusals[0].startsWith("VINTF_SEPOLICY: vendor's sepolicy is 202404"), refusals[0])
        assertTrue("33.0, 34.0" in refusals[0], refusals[0])
    }

    @Test
    fun whatCouldNotBeJudgedIsANoteNotARefusal() {
        val noTool = "@@@ mapping\n202404.cil\n@@@ vendor-sepolicy\n202404\n@@@ tool-missing\n"
        assertEquals(emptyList(), VintfCheck.refusals(noTool))
        assertEquals(VintfStatus.NOT_RUN, VintfCheck.assess(noTool).status)
        assertEquals(VintfStatus.NOT_RUN, VintfCheck.assess(null).status)
        assertTrue(VintfCheck.notes(noTool).single().startsWith("VINTF_NOT_CHECKED: checkvintf is not installed"))

        val noConfig = output(kernel = "no embedded configuration (CONFIG_IKCONFIG) in the boot kernel")
        assertEquals(
            listOf(
                "VINTF_NOT_CHECKED: the kernel requirements were not checked: " +
                    "no embedded configuration (CONFIG_IKCONFIG) in the boot kernel",
            ),
            VintfCheck.notes(noConfig),
        )

        val crashed = output(result = "@@@ result -|- 70\nERROR: No device manifest.\n")
        assertEquals(emptyList(), VintfCheck.refusals(crashed))
        assertEquals(
            listOf("VINTF_NOT_CHECKED: checkvintf could not finish (exit 70): No device manifest."),
            VintfCheck.notes(crashed),
        )
    }

    @Test
    fun successfulSkuCheckWithUnreadableApexIsIncomplete() {
        val missing = "Could not read VINTF fragments from APEX vendor/apex/broken.apex. Check its archive and payload."
        val assessment = VintfCheck.assess(output() + "@@@ missing apex|$missing\n")
        assertEquals(VintfStatus.INCOMPLETE, assessment.status)
        assertEquals(listOf(VintfEvidence(missing, VintfEvidenceKind.APEX)), assessment.evidence)
    }

    @Test
    fun anInterruptedScanCannotPassOnOnlyOneSuccessfulSku() {
        val partial = VintfCheck.assess(output().substringBefore("@@@ done"))
        assertEquals(VintfStatus.INCOMPLETE, partial.status)
        assertTrue(partial.missingEvidence.any { "complete set" in it })
    }

    @Test
    fun aNonzeroScanExitCannotBeCompatibleEvenAfterTheCompletionMarker() {
        val assessment = VintfCheck.assess(output(), exitCode = 1)
        assertEquals(VintfStatus.INCOMPLETE, assessment.status)
        assertTrue(assessment.missingEvidence.any { "exit 1" in it })
    }

    @Test
    fun inferredSkuMismatchIsReportedWithoutRefusingTheBuild() {
        val text = "@@@ sku-inferred\n" + output(result = incompatible)
        val assessment = VintfCheck.assess(text)

        assertEquals(VintfStatus.INCOMPLETE, assessment.status)
        assertEquals(
            listOf(VintfSkuResult("sun", null, VintfStatus.INCOMPATIBLE, provisional = true)),
            assessment.skuResults,
        )
        assertTrue(assessment.missingEvidence.any { "META/misc_info.txt" in it })
        assertTrue(assessment.missingEvidence.any { "vendor SKU sun" in it && "CONFIG_ADVISE_SYSCALLS" in it })
        assertEquals(emptyList(), VintfCheck.refusals(text))
    }

    @Test
    fun missingOrBadSkuMetadataDoesNotClearAConfirmedDefaultMismatch() {
        val fallback = "@@@ sku-inferred\n" +
            "@@@ missing firmware|Cannot read archive metadata.\n" +
            incompatible.replace("sun|- 65", "sun|- 65 provisional") +
            incompatible.replace("sun|- 65", "-|- 65 confirmed")
        val text = output(result = fallback).replace("@@@ done 1", "@@@ done 2")

        val assessment = VintfCheck.assess(text)
        assertEquals(VintfStatus.INCOMPATIBLE, assessment.status)
        assertEquals(listOf(true, false), assessment.skuResults.map { it.provisional })
        assertTrue(VintfCheck.refusals(text).single().startsWith("VINTF_INCOMPATIBLE:"))
    }

    @Test
    fun missingSelectedOdmBlocksForMissingEvidenceAndMakesTheMismatchProvisional() {
        val missing = "The selected /odm partition has no readable VINTF input directory."
        val text = output(result = "@@@ missing partition|$missing\n" + incompatible)

        val assessment = VintfCheck.assess(text)
        assertEquals(VintfStatus.INCOMPLETE, assessment.status)
        assertTrue(assessment.skuResults.single().provisional)
        assertEquals(listOf("VINTF_PARTITION_MISSING: $missing"), VintfCheck.refusals(text, setOf("any")))
    }

    @Test
    fun acknowledgementAppliesOnlyToTheSamePairReasonAndScope() {
        val text = output(result = incompatible)
        val refusal = VintfCheck.refusals(text, scope = "original").single()
        val id = requireNotNull(VintfCheck.splitMismatchMessage(refusal.substringAfter(": ")).first)
        val accepted = setOf(id)

        assertEquals(emptyList(), VintfCheck.refusals(text, accepted, scope = "original"))
        assertEquals(1, VintfCheck.acknowledgedMismatches(text, accepted, scope = "original").size)
        assertEquals(1, VintfCheck.refusals(text, accepted, scope = "different").size)
        assertEquals(1, VintfCheck.refusals(text.replace("sun|-", "other|-"), accepted, scope = "original").size)
        val modifiedConfig = text.replace("CONFIG_ADVISE_SYSCALLS", "CONFIG_OTHER")
        assertEquals(1, VintfCheck.refusals(modifiedConfig, accepted, "original").size)
    }

    @Test
    fun buildGateUsesTheSameScopedAcknowledgementAsPreflightAndIgnoresTheOldGlobalFlag() {
        val base = PipelineFixture().globalOtaContext()
        val legacy = base.snapshot.copy(
            build = base.snapshot.build.copy(acknowledgedWarnings = setOf(BuildWarningKind.VintfIncompatible.name)),
        )
        val original = output(result = incompatible)
        fun refusals(snapshot: ConfigurationSnapshot, report: String) = BuildChecks.vintfSteps(
            base.copy(snapshot = snapshot, runtimeValues = mapOf(BuildChecks.VINTF_KEY to report)),
        ).filterIsInstance<PipelineStep.Check>().mapNotNull { it.verify(emptyMap()) }

        val blocked = refusals(legacy, original).single()
        val id = requireNotNull(VintfCheck.splitMismatchMessage(blocked.substringAfter(": ")).first)
        val accepted = acknowledgeVintfMismatch(id).apply(legacy)
        assertEquals(emptyList(), refusals(accepted, original))
        assertEquals(1, refusals(accepted, original.replace("sun|-", "other|-")).size)

        val changedSource = accepted.copy(acquisition = accepted.acquisition.copy(expectedSha256 = "1".repeat(64)))
        assertEquals(1, refusals(changedSource, original).size)
    }

    @Test
    fun literalDashSkuDiffersFromAnUnsetSku() {
        val text = output(result = "@@@ result %2D|- 0\n")
        assertEquals(
            listOf(VintfSkuResult("-", null, VintfStatus.COMPATIBLE)),
            VintfCheck.assess(text).skuResults,
        )
    }

    @Test
    fun firstApiZeroIsNotExposedAsAValidLevel() {
        assertEquals(null, VintfCheck.assess("@@@ first-api 0\n" + output()).firstApiLevel)
    }

    @Test
    fun assemblyIsRequiredBeforeTheCheckerCanRun() {
        val assessment = VintfCheck.assess("@@@ not-run Assemble must create work/ first.\n")
        assertEquals(VintfStatus.NOT_RUN, assessment.status)
        assertTrue(assessment.missingEvidence.any { "Assemble" in it })
    }

    @Test
    fun vendorPolicyCannotEstablishAnIncompatibilityWithoutSystem() {
        val missing = "The assembled /system partition is missing. Run Assemble."
        val reason = "System and vendor are required to start checkvintf."
        val text = "@@@ mapping\n@@@ vendor-sepolicy\n202404\n@@@ missing assembly|$missing\n@@@ not-run $reason\n"
        val assessment = VintfCheck.assess(text)
        assertEquals(VintfStatus.NOT_RUN, assessment.status)
        assertEquals(listOf(missing, reason), assessment.missingEvidence)
        assertTrue(assessment.evidence.all { it.kind == VintfEvidenceKind.ASSEMBLY })
        assertEquals(emptyList(), VintfCheck.refusals(text))
    }
}
