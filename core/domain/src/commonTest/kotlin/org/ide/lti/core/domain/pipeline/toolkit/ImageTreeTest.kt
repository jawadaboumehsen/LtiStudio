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
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ImageTreeTest {

    @Test
    fun entryValidationEnforcesValidAttributes() {
        assertFailsWith<IllegalArgumentException> {
            ImageTreeEntry(path = "", size = 100L, mode = 0b111101101, uid = 0, gid = 0, label = "u:object_r:rootfs:s0")
        }
        assertFailsWith<IllegalArgumentException> {
            ImageTreeEntry(
                path = "/bin/sh",
                size = -1L,
                mode = 0b111101101,
                uid = 0,
                gid = 0,
                label = "u:object_r:rootfs:s0",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ImageTreeEntry(
                path = "/bin/sh",
                size = 100L,
                mode = 0b111101101,
                uid = -1,
                gid = 0,
                label = "u:object_r:rootfs:s0",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ImageTreeEntry(
                path = "/bin/sh",
                size = 100L,
                mode = 0b111101101,
                uid = 0,
                gid = -1,
                label = "u:object_r:rootfs:s0",
            )
        }
    }

    @Test
    fun imageTreeRoundTripSerialization() {
        val entry1 = ImageTreeEntry(
            path = "init.rc",
            size = 1200L,
            mode = 420, // 0644 octal
            uid = 0,
            gid = 0,
            label = "u:object_r:rootfs:s0",
        )
        val entry2 = ImageTreeEntry(
            path = "system/bin/sh",
            size = 45000L,
            mode = 493, // 0755 octal
            uid = 0,
            gid = 2000,
            label = "u:object_r:shell_exec:s0",
        )
        val tree = ImageTree(entries = listOf(entry1, entry2))

        val encoded = ImageTree.encode(tree)
        val decoded = ImageTree.decode(encoded)

        assertEquals(tree, decoded)
        assertEquals(entry1, decoded.find("init.rc"))
        assertEquals(entry2, decoded.find("system/bin/sh"))
    }

    @Test
    fun compareIdenticalTreesReturnsOk() {
        val tree = ImageTree(
            entries = listOf(
                ImageTreeEntry("file1", 100L, 420, 0, 0, "label1"),
                ImageTreeEntry("file2", 200L, 493, 0, 2000, "label2"),
            ),
        )
        val verify = ImageVerify.compare(expected = tree, actual = tree)
        assertTrue(verify.ok)
        assertTrue(verify.missing.isEmpty())
        assertTrue(verify.extra.isEmpty())
        assertTrue(verify.changed.isEmpty())
    }

    @Test
    fun compareDetectsMissingExtraAndChangedEntries() {
        val expected = ImageTree(
            entries = listOf(
                ImageTreeEntry("unchanged", 100L, 420, 0, 0, "label_ok"),
                ImageTreeEntry("missing_file", 50L, 420, 0, 0, "label_m"),
                ImageTreeEntry("modified_size", 200L, 420, 0, 0, "label_s"),
                ImageTreeEntry("modified_mode", 300L, 420, 0, 0, "label_mode"),
                ImageTreeEntry("modified_uid", 400L, 420, 0, 0, "label_u"),
                ImageTreeEntry("modified_gid", 500L, 420, 0, 0, "label_g"),
                ImageTreeEntry("modified_label", 600L, 420, 0, 0, "label_old"),
            ),
        )

        val actual = ImageTree(
            entries = listOf(
                ImageTreeEntry("unchanged", 100L, 420, 0, 0, "label_ok"),
                ImageTreeEntry("extra_file", 777L, 493, 1000, 1000, "label_extra"),
                ImageTreeEntry("modified_size", 250L, 420, 0, 0, "label_s"),
                ImageTreeEntry("modified_mode", 300L, 493, 0, 0, "label_mode"),
                ImageTreeEntry("modified_uid", 400L, 420, 1000, 0, "label_u"),
                ImageTreeEntry("modified_gid", 500L, 420, 0, 2000, "label_g"),
                ImageTreeEntry("modified_label", 600L, 420, 0, 0, "label_new"),
            ),
        )

        val verify = ImageVerify.compare(expected = expected, actual = actual)
        assertFalse(verify.ok)
        assertEquals(listOf("missing_file"), verify.missing)
        assertEquals(listOf("extra_file"), verify.extra)
        assertEquals(5, verify.changed.size)

        val changedPaths = verify.changed.map { it.path }
        assertEquals(
            listOf("modified_gid", "modified_label", "modified_mode", "modified_size", "modified_uid"),
            changedPaths,
        )

        val sizeChange = verify.changed.first { it.path == "modified_size" }
        assertTrue("size" in sizeChange.reason)

        val labelChange = verify.changed.first { it.path == "modified_label" }
        assertTrue("label" in labelChange.reason)
    }

    @Test
    fun imageVerifySerialization() {
        val verify = ImageVerify(
            ok = false,
            missing = listOf("a"),
            extra = listOf("b"),
            changed = listOf(ImageTreeChange("c", "size(10 vs 20)")),
        )
        val encoded = ImageVerify.encode(verify)
        val decoded = ImageVerify.decode(encoded)
        assertEquals(verify, decoded)
    }
}
