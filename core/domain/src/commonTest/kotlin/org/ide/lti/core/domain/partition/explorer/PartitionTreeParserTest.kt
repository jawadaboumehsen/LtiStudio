/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.partition.explorer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PartitionTreeParserTest {

    @Test
    fun testParseManifestCreatesTree() {
        val lines = listOf(
            "system/bin/sh 0755 u:object_r:shell_exec:s0 250000",
            "system/bin/app_process64 0755 u:object_r:zygote_exec:s0 148000",
            "system/etc/hosts 0644 u:object_r:system_file:s0 120",
            "system/bin/su 0755 u:object_r:su_exec:s0 50000 /system/xbin/su",
        )

        val metadata = PartitionTreeParser.parseManifest("system", "EROFS", lines)

        assertEquals("system", metadata.partitionName)
        assertEquals("EROFS", metadata.filesystem)
        assertEquals(4, metadata.totalFiles)
        assertEquals(250000L + 148000L + 120L + 50000L, metadata.totalSizeBytes)

        val children = metadata.rootNode.children
        assertEquals(4, children.size)

        val shNode = children.first { it.name == "sh" }
        assertEquals("0755", shNode.permissions)
        assertEquals("u:object_r:shell_exec:s0", shNode.selinuxContext)
        assertEquals(PartitionNodeType.REGULAR_FILE, shNode.type)

        val suNode = children.first { it.name == "su" }
        assertEquals(PartitionNodeType.SYMLINK, suNode.type)
        assertEquals("/system/xbin/su", suNode.targetLink)
    }

    @Test
    fun testFilterQuery() {
        val lines = listOf(
            "system/bin/sh 0755 u:object_r:shell_exec:s0 250000",
            "system/bin/app_process64 0755 u:object_r:zygote_exec:s0 148000",
            "system/etc/hosts 0644 u:object_r:system_file:s0 120",
        )

        val metadata = PartitionTreeParser.parseManifest("system", "EROFS", lines)
        val filtered = metadata.rootNode.filter("zygote")

        assertNotNull(filtered)
        assertEquals(1, filtered.children.size)
        assertEquals("app_process64", filtered.children[0].name)

        val noMatch = metadata.rootNode.filter("nonexistent_binary_xyz")
        assertNull(noMatch)
    }
}
