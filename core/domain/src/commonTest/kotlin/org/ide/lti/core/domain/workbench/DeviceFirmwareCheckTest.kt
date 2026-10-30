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

import org.ide.lti.core.domain.pipeline.toolkit.SuperGroup
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayout
import org.ide.lti.core.model.target.DefaultTargetCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeviceFirmwareCheckTest {
    private val device = DefaultTargetCatalog.PQ84P01_DEFAULT

    /** What PQ84P01's stock firmware (build 20260113) carries. */
    private val stock = FirmwareFacts(
        props = mapOf(
            "ro.product.vendor.device" to "PQ84P01",
            "ro.product.vendor.model" to "NP05J",
            "ro.board.platform" to "sun",
            "ro.virtual_ab.enabled" to "true",
            "ro.build.version.release" to "15",
            "ro.build.version.sdk" to "35",
        ),
        partitionSizes = mapOf(
            "boot" to 100663296L,
            "init_boot" to 8388608L,
            "vendor_boot" to 100663296L,
            "dtbo" to 18874368L,
        ),
        superLayout = null,
    )

    @Test
    fun theDtboSizeDiffersFromTheFirmwaresAndCanBeTakenFromIt() {
        val facts = DeviceFirmwareCheck.compare(device, stock)
        val differing = facts.filter { it.differs }.map { it.setting }
        assertEquals(listOf("dtboPartitionBytes"), differing)
        val dtbo = facts.single { it.setting == "dtboPartitionBytes" }
        assertEquals(18874368L, DeviceFirmwareCheck.apply(device, dtbo).dtboPartitionBytes)
        assertTrue(facts.any { it.setting == null && it.label == "Android" && it.firmware == "15 (SDK 35)" })
    }

    @Test
    fun aSuperTableIsComparedWhenTheFirmwareHasOne() {
        val withSuper = stock.copy(
            superLayout = SuperLayout(
                superSize = 17179869184L,
                groups = listOf(SuperGroup("qti_dynamic_partitions", 17175674880L)),
            ),
        )
        val facts = DeviceFirmwareCheck.compare(device, withSuper).filter { it.setting?.startsWith("super") == true }
        assertEquals(listOf("superPartitionBytes", "superGroupName", "superGroupBytes"), facts.map { it.setting })
        assertTrue(facts.none { it.differs })
    }

    @Test
    fun anotherDevicesFirmwareShowsUpAsAWrongCodename() {
        val other = stock.copy(props = stock.props + ("ro.product.vendor.device" to "NX809J"))
        val facts = DeviceFirmwareCheck.compare(device, other).filter { it.differs }.map { it.setting }
        assertEquals(listOf("codename", "assertModels", "dtboPartitionBytes"), facts)
    }
}
