/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.partition

import org.ide.lti.core.domain.partition.explorer.PartitionNode
import org.ide.lti.core.domain.partition.explorer.PartitionNodeType
import org.ide.lti.core.domain.partition.explorer.PartitionTreeParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PartitionExplorerViewTest {

    @Test
    fun testPartitionMetadataExtractionAndExtractionCallback() {
        val lines = listOf(
            "system/framework/framework.jar 0644 u:object_r:system_file:s0 34000000",
            "system/bin/surfaceflinger 0755 u:object_r:surfaceflinger_exec:s0 2100000",
        )

        val metadata = PartitionTreeParser.parseManifest("system", "EROFS", lines)
        assertEquals("system", metadata.partitionName)
        assertEquals(2, metadata.totalFiles)

        var extractedNode: PartitionNode? = null
        val onExtract: (PartitionNode) -> Unit = { extractedNode = it }

        val target = metadata.rootNode.children.first { it.name == "framework.jar" }
        onExtract(target)

        assertNotNull(extractedNode)
        assertEquals("framework.jar", extractedNode?.name)
        assertEquals("0644", extractedNode?.permissions)
    }

    @Test
    fun testSymlinkTargetDisplay() {
        val node = PartitionNode(
            path = "system/bin/toolbox",
            name = "toolbox",
            type = PartitionNodeType.SYMLINK,
            targetLink = "/system/bin/toybox",
        )
        assertEquals("/system/bin/toybox", node.targetLink)
        assertEquals(PartitionNodeType.SYMLINK, node.type)
    }
}
