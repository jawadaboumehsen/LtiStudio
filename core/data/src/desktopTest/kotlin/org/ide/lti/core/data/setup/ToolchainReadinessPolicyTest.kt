/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolsState
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ToolchainReadinessPolicyTest {
    private fun mockVersion(groupId: String, intact: Boolean = true): InstalledVersion = InstalledVersion(
        group = ToolGroupId(groupId),
        id = "v-1",
        label = "v1.0",
        source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
        recipe = RecipeSummary("cmake", 1),
        outputs = emptyList(),
        builtAt = "2026-10-01",
        intact = intact,
    )

    private fun testGroup(
        groupId: String,
        toolId: String,
        requiredFor: List<String>,
        requiredForProduct: Boolean = true,
    ): ToolGroup = ToolGroup(
        id = ToolGroupId(groupId),
        source = ToolSource.Git("https://example.com/repo.git", ToolRef.Tag("v1.0"), "1" * 40),
        recipe = RecipeConfig.CMake(),
        layout = listOf("CMakeLists.txt"),
        outputs =
        listOf(
            ToolOutput(
                toolId = toolId,
                file = toolId,
                kind = ToolOutputKind.NATIVE,
                probe = ToolProbe(listOf("--version")),
                requiredFor = requiredFor,
                requiredForProduct = requiredForProduct,
            ),
        ),
        buildPackages = emptyList(),
    )

    @Test
    fun `action is blocked only when a tool in its requiredFor is not ready per ToolManager state`() {
        val groupExtract = testGroup("group-extract", "tool-extract", listOf("extract"), requiredForProduct = true)
        val groupFlash = testGroup("group-flash", "tool-flash", listOf("flash"), requiredForProduct = true)

        ToolGroupCatalog.withIsolatedTestGroups(listOf(groupExtract, groupFlash)) {
            // group-extract is ready, group-flash is unready
            val state =
                ToolsState(
                    inventory =
                    ToolInventory(
                        mapOf(
                            groupExtract.id to
                                GroupState(
                                    group = groupExtract.id,
                                    current = mockVersion("group-extract", intact = true),
                                    installed = listOf(mockVersion("group-extract")),
                                ),
                            groupFlash.id to
                                GroupState(
                                    group = groupFlash.id,
                                    current = null,
                                    installed = emptyList(),
                                ),
                        ),
                    ),
                )

            // Extract is ready because tool-extract is ready
            val extractResult = ToolchainReadinessPolicy.checkOperationReadiness("extract", state)
            assertEquals(ToolchainReadinessPolicy.OperationReadiness.Ready, extractResult)

            // Flash is blocked because tool-flash is unready
            val flashResult = ToolchainReadinessPolicy.checkOperationReadiness("flash", state)
            assertIs<ToolchainReadinessPolicy.OperationReadiness.Blocked>(flashResult)
            assertEquals(listOf("tool-flash"), flashResult.missingTools)

            // When tool-extract becomes damaged, extraction is blocked
            val damagedState =
                ToolsState(
                    inventory =
                    ToolInventory(
                        mapOf(
                            groupExtract.id to
                                GroupState(
                                    group = groupExtract.id,
                                    current = mockVersion("group-extract", intact = false),
                                    installed = listOf(mockVersion("group-extract", intact = false)),
                                ),
                        ),
                    ),
                )
            val damagedExtractResult = ToolchainReadinessPolicy.checkOperationReadiness("extract", damagedState)
            assertIs<ToolchainReadinessPolicy.OperationReadiness.Blocked>(damagedExtractResult)
            assertEquals(listOf("tool-extract"), damagedExtractResult.missingTools)
        }
    }

    @Test
    fun `outputs with requiredForProduct false never block extraction`() {
        val nonProductTool =
            testGroup(
                groupId = "group-secondary",
                toolId = "tool-secondary",
                requiredFor = listOf("extract"),
                requiredForProduct = false,
            )

        ToolGroupCatalog.withIsolatedTestGroups(listOf(nonProductTool)) {
            // Secondary tool is missing / unready
            val state =
                ToolsState(
                    inventory =
                    ToolInventory(
                        mapOf(
                            nonProductTool.id to
                                GroupState(
                                    group = nonProductTool.id,
                                    current = null,
                                    installed = emptyList(),
                                ),
                        ),
                    ),
                )

            val extractResult = ToolchainReadinessPolicy.checkOperationReadiness("extract", state)
            assertEquals(ToolchainReadinessPolicy.OperationReadiness.Ready, extractResult)
        }
    }
}

private operator fun String.times(n: Int): String = repeat(n)
