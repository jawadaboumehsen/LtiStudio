/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui

import org.ide.lti.core.model.filesystem.FileNode
import org.ide.lti.core.model.filesystem.FileType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassFileTreeTest {

    private val tree = FileNode(
        path = "/root",
        name = "root",
        type = FileType.DIRECTORY,
        children = listOf(
            FileNode(
                path = "/root/src",
                name = "src",
                type = FileType.DIRECTORY,
                children = listOf(
                    FileNode(
                        path = "/root/src/commonMain",
                        name = "commonMain",
                        type = FileType.DIRECTORY,
                        children = listOf(
                            FileNode(
                                path = "/root/src/commonMain/kotlin",
                                name = "kotlin",
                                type = FileType.DIRECTORY,
                                children = listOf(
                                    FileNode(
                                        path = "/root/src/commonMain/kotlin/App.kt",
                                        name = "App.kt",
                                        type = FileType.FILE,
                                    ),
                                ),
                            ),
                            FileNode(
                                path = "/root/src/commonMain/resources",
                                name = "resources",
                                type = FileType.DIRECTORY,
                            ),
                        ),
                    ),
                    FileNode(
                        path = "/root/src/build.gradle.kts",
                        name = "build.gradle.kts",
                        type = FileType.FILE,
                    ),
                ),
            ),
            FileNode(
                path = "/root/README.md",
                name = "README.md",
                type = FileType.FILE,
            ),
        ),
    )

    @Test
    fun testEmptyChildrenReturnsEmptyList() {
        val emptyNode = FileNode(path = "/empty", name = "empty", type = FileType.DIRECTORY, children = null)
        val flattened = flattenFileTree(emptyNode, emptySet())
        assertTrue(flattened.isEmpty())
    }

    @Test
    fun testAllCollapsedShowsOnlyRootChildrenAtLevelZero() {
        val flattened = flattenFileTree(tree, emptySet())
        assertEquals(2, flattened.size)
        assertEquals("/root/src", flattened[0].node.path)
        assertEquals(0, flattened[0].level)
        assertEquals("/root/README.md", flattened[1].node.path)
        assertEquals(0, flattened[1].level)
    }

    @Test
    fun testLevelOneExpansion() {
        val flattened = flattenFileTree(tree, setOf("/root/src"))
        assertEquals(4, flattened.size)
        assertEquals("/root/src", flattened[0].node.path)
        assertEquals(0, flattened[0].level)
        assertEquals("/root/src/commonMain", flattened[1].node.path)
        assertEquals(1, flattened[1].level)
        assertEquals("/root/src/build.gradle.kts", flattened[2].node.path)
        assertEquals(1, flattened[2].level)
        assertEquals("/root/README.md", flattened[3].node.path)
        assertEquals(0, flattened[3].level)
    }

    @Test
    fun testThreeLevelDeepExpansionAndCollapseSequence() {
        // Step 1: Expand 3 levels deep:
        // /root/src -> /root/src/commonMain -> /root/src/commonMain/kotlin
        val expandedAllThree = setOf(
            "/root/src",
            "/root/src/commonMain",
            "/root/src/commonMain/kotlin",
        )
        val flattened3 = flattenFileTree(tree, expandedAllThree)

        assertEquals(7, flattened3.size)
        assertEquals("/root/src", flattened3[0].node.path)
        assertEquals(0, flattened3[0].level)
        assertEquals("/root/src/commonMain", flattened3[1].node.path)
        assertEquals(1, flattened3[1].level)
        assertEquals("/root/src/commonMain/kotlin", flattened3[2].node.path)
        assertEquals(2, flattened3[2].level)
        assertEquals("/root/src/commonMain/kotlin/App.kt", flattened3[3].node.path)
        assertEquals(3, flattened3[3].level)
        assertEquals("/root/src/commonMain/resources", flattened3[4].node.path)
        assertEquals(2, flattened3[4].level)
        assertEquals("/root/src/build.gradle.kts", flattened3[5].node.path)
        assertEquals(1, flattened3[5].level)
        assertEquals("/root/README.md", flattened3[6].node.path)
        assertEquals(0, flattened3[6].level)

        // Step 2: Collapse middle directory (/root/src/commonMain) while inner node is still in set
        val collapseMiddle = setOf(
            "/root/src",
            "/root/src/commonMain/kotlin",
        )
        val flattenedCollapsedMiddle = flattenFileTree(tree, collapseMiddle)
        assertEquals(4, flattenedCollapsedMiddle.size)
        assertEquals("/root/src", flattenedCollapsedMiddle[0].node.path)
        assertEquals(0, flattenedCollapsedMiddle[0].level)
        assertEquals("/root/src/commonMain", flattenedCollapsedMiddle[1].node.path)
        assertEquals(1, flattenedCollapsedMiddle[1].level)
        assertEquals("/root/src/build.gradle.kts", flattenedCollapsedMiddle[2].node.path)
        assertEquals(1, flattenedCollapsedMiddle[2].level)
        assertEquals("/root/README.md", flattenedCollapsedMiddle[3].node.path)
        assertEquals(0, flattenedCollapsedMiddle[3].level)

        // Step 3: Re-expand middle directory and confirm 3-level depth is properly restored
        val reExpandedMiddle = flattenFileTree(tree, expandedAllThree)
        assertEquals(flattened3, reExpandedMiddle)

        // Step 4: Collapse top directory (/root/src)
        val collapseTop = setOf(
            "/root/src/commonMain",
            "/root/src/commonMain/kotlin",
        )
        val flattenedCollapsedTop = flattenFileTree(tree, collapseTop)
        assertEquals(2, flattenedCollapsedTop.size)
        assertEquals("/root/src", flattenedCollapsedTop[0].node.path)
        assertEquals(0, flattenedCollapsedTop[0].level)
        assertEquals("/root/README.md", flattenedCollapsedTop[1].node.path)
        assertEquals(0, flattenedCollapsedTop[1].level)
    }
}
