/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.toolkit

import org.ide.lti.core.domain.pipeline.toolkit.ChunkExtractor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChunkExtractorTest {

    @Test
    fun identifiesSparseChunkFilenames() {
        assertTrue(ChunkExtractor.isSparseChunk("system.img_sparsechunk.0"))
        assertTrue(ChunkExtractor.isSparseChunk("system_sparsechunk.1"))
        assertTrue(ChunkExtractor.isSparseChunk("vendor.chunk0"))
        assertFalse(ChunkExtractor.isSparseChunk("boot.img"))
        assertFalse(ChunkExtractor.isSparseChunk("recovery.img.lz4"))
    }

    @Test
    fun groupsAndSortsSparseChunksByPartition() {
        val files = listOf(
            "/ws/firmware/system.img_sparsechunk.2",
            "/ws/firmware/vendor.img_sparsechunk.0",
            "/ws/firmware/system.img_sparsechunk.0",
            "/ws/firmware/system.img_sparsechunk.1",
            "/ws/firmware/vendor.img_sparsechunk.1",
            "/ws/firmware/boot.img",
        )
        val groups = ChunkExtractor.groupSparseChunks(files)
        assertEquals(2, groups.size)
        assertEquals(
            listOf(
                "/ws/firmware/system.img_sparsechunk.0",
                "/ws/firmware/system.img_sparsechunk.1",
                "/ws/firmware/system.img_sparsechunk.2",
            ),
            groups["system"],
        )
        assertEquals(
            listOf(
                "/ws/firmware/vendor.img_sparsechunk.0",
                "/ws/firmware/vendor.img_sparsechunk.1",
            ),
            groups["vendor"],
        )
    }

    @Test
    fun generatesJoinChunksCommandUsingSimg2img() {
        val chunks = listOf(
            "/ws/firmware/system.img_sparsechunk.0",
            "/ws/firmware/system.img_sparsechunk.1",
        )
        val cmd = ChunkExtractor.joinChunksCommand(
            chunkPaths = chunks,
            outImagePath = "/ws/firmware/extracted/system.img",
            workingDir = "/ws",
        )
        assertEquals("simg2img", cmd.toolId)
        assertEquals(
            listOf(
                "/ws/firmware/system.img_sparsechunk.0",
                "/ws/firmware/system.img_sparsechunk.1",
                "/ws/firmware/extracted/system.img",
            ),
            cmd.args,
        )
        assertEquals("/ws", cmd.workingDir)
    }

    @Test
    fun constructsNtpiUnsupportedRefusal() {
        val refusal = ChunkExtractor.ntpiRefusal("firmware_package.ntpi")
        assertEquals("Nothing Phone packed images (NTPI) are not currently supported.", refusal.cause)
        assertEquals("firmware_package.ntpi", refusal.item)
        assertEquals("Extract partition images with the vendor utility before importing.", refusal.nextStep)
    }
}
