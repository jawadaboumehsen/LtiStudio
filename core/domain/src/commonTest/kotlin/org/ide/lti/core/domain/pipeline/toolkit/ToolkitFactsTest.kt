/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolkitFactsTest {

    private val sampleFacts = ToolkitFacts(
        deviceIdentity = "sunfish",
        slotMode = SlotMode.VIRTUAL_AB,
        isVirtualAbCompressed = true,
        partitions = listOf(
            ToolkitPartitionFact(
                name = "system",
                fsType = "erofs",
                sizeBytes = 2_147_483_648L,
                inSuper = true,
            ),
            ToolkitPartitionFact(
                name = "vendor",
                fsType = "ext4",
                sizeBytes = 536_870_912L,
                inSuper = true,
            ),
            ToolkitPartitionFact(
                name = "boot",
                fsType = "raw",
                sizeBytes = 67_108_864L,
                inSuper = false,
            ),
        ),
    )

    @Test
    fun testPropertiesAndHelpers() {
        assertEquals("sunfish", sampleFacts.deviceIdentity)
        assertEquals(SlotMode.VIRTUAL_AB, sampleFacts.slotMode)
        assertTrue(sampleFacts.isVirtualAbCompressed)
        assertEquals(3, sampleFacts.partitions.size)

        val superPartitions = sampleFacts.partitions.filter { it.inSuper }
        assertEquals(2, superPartitions.size)
        assertEquals(listOf("system", "vendor"), superPartitions.map { it.name })

        val bootPart = sampleFacts.partitions.firstOrNull { it.name == "boot" }
        assertFalse(bootPart?.inSuper ?: true)
    }

    @Test
    fun testBasicInfoJsonCodec() {
        val basicInfo = sampleFacts.toBasicInfo()
        val json = ToolkitFacts.encodeBasicInfo(basicInfo)
        val decoded = ToolkitFacts.decodeBasicInfo(json)

        assertEquals(basicInfo.deviceIdentity, decoded.deviceIdentity)
        assertEquals(basicInfo.slotMode, decoded.slotMode)
        assertEquals(basicInfo.isVirtualAbCompressed, decoded.isVirtualAbCompressed)
    }

    @Test
    fun testPartitionInfoJsonCodec() {
        val partitionInfo = sampleFacts.toPartitionInfo()
        val json = ToolkitFacts.encodePartitionInfo(partitionInfo)
        val decoded = ToolkitFacts.decodePartitionInfo(json)

        assertEquals(3, decoded.partitions.size)
        assertEquals("system", decoded.partitions[0].name)
        assertEquals("erofs", decoded.partitions[0].fsType)
        assertEquals(2_147_483_648L, decoded.partitions[0].sizeBytes)
        assertTrue(decoded.partitions[0].inSuper)
    }

    @Test
    fun testCombineAndFullRoundTrip() {
        val basic = sampleFacts.toBasicInfo()
        val part = sampleFacts.toPartitionInfo()
        val combined = ToolkitFacts.from(basic, part)

        assertEquals(sampleFacts, combined)

        val fullJson = ToolkitFacts.encode(sampleFacts)
        val fullDecoded = ToolkitFacts.decode(fullJson)
        assertEquals(sampleFacts, fullDecoded)
    }
}
