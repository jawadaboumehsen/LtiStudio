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

class PartitionImageTest {
    @Test
    fun roundTripsPartitionImageListViaJson() {
        val images = listOf(
            PartitionImage(
                name = "system",
                fsType = FsType.Ext4,
                sizeBytes = 1048576L,
                sparse = false,
                path = "firmware/extracted/system.img",
            ),
            PartitionImage(
                name = "vendor",
                fsType = FsType.Erofs,
                sizeBytes = 524288L,
                sparse = true,
                path = "firmware/extracted/vendor.img",
            ),
            PartitionImage(
                name = "boot",
                fsType = FsType.Raw,
                sizeBytes = 67108864L,
                sparse = false,
                path = "firmware/extracted/boot.img",
            ),
        )

        val json = PartitionImage.encodeList(images)
        val decoded = PartitionImage.decodeList(json)

        assertEquals(3, decoded.size)
        assertEquals(images, decoded)
        assertEquals("system", decoded[0].name)
        assertEquals(FsType.Ext4, decoded[0].fsType)
        assertFalse(decoded[0].sparse)
        assertTrue(decoded[1].sparse)
    }
}
