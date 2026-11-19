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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** [VintfCheck] judging what its script printed for PQ84P01 (vendor SKU sun, kernel 6.6.92, sepolicy 202404). */
class VintfCheckTest {
    private fun output(
        mappings: String = "202404.cil\n34.0.cil",
        kernel: String = "ok 6.6.92-android15-8-g3637f4904cf5-ab13944661-4k",
        result: String = "@@@ result sun|- 0\n",
    ) = "@@@ mapping\n$mappings\n@@@ vendor-sepolicy\n202404\n@@@ kernel $kernel\n$result"

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
    fun aCompatibleSystemHasNoRefusalAndNoNote() {
        assertEquals(emptyList(), VintfCheck.refusals(output(), accepted = false))
        assertEquals(emptyList(), VintfCheck.notes(output()))
    }

    @Test
    fun anIncompatibilityIsRefusedWithCheckvintfsReasonUntilAccepted() {
        val refusals = VintfCheck.refusals(output(result = incompatible), accepted = false)
        assertEquals(1, refusals.size, "$refusals")
        val refusal = refusals[0]
        assertTrue(refusal.startsWith("VINTF_INCOMPATIBLE: the system does not meet"), refusal)
        assertTrue("(vendor SKU sun): Runtime info and framework compatibility matrix" in refusal, refusal)
        assertTrue("Missing config CONFIG_ADVISE_SYSCALLS" in refusal, refusal)
        assertTrue("files are incompatible" !in refusal && ": Success" !in refusal, refusal)
        assertEquals(emptyList(), VintfCheck.refusals(output(result = incompatible), accepted = true))
    }

    @Test
    fun aMissingSelinuxMappingIsRefusedEvenWhenIncompatibilityIsAccepted() {
        val refusals = VintfCheck.refusals(output(mappings = "33.0.cil\n34.0.cil"), accepted = true)
        assertEquals(1, refusals.size, "$refusals")
        assertTrue(refusals[0].startsWith("VINTF_SEPOLICY: vendor's sepolicy is 202404"), refusals[0])
        assertTrue("33.0, 34.0" in refusals[0], refusals[0])
    }

    @Test
    fun whatCouldNotBeJudgedIsANoteNotARefusal() {
        val noTool = "@@@ mapping\n202404.cil\n@@@ vendor-sepolicy\n202404\n@@@ tool-missing\n"
        assertEquals(emptyList(), VintfCheck.refusals(noTool, accepted = false))
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
        assertEquals(emptyList(), VintfCheck.refusals(crashed, accepted = false))
        assertEquals(
            listOf("VINTF_NOT_CHECKED: checkvintf could not finish (exit 70): No device manifest."),
            VintfCheck.notes(crashed),
        )
    }
}
