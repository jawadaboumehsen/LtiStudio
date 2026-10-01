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
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogExtensibilityTest {

    @Test
    fun oneEntryAddsAToolToCapabilitiesCatalogProbesAndDispatch() {
        val testGroup = ToolGroup(
            id = ToolGroupId("test-script-group"),
            source = ToolSource.Git(
                recommendedUrl = "https://example.com/test-script.git",
                recommendedCommit = "0123456789abcdef0123456789abcdef01234567",
                recommendedLabel = "main",
            ),
            recipe = RecipeConfig.ScriptCopy(files = listOf("custom_tool.sh")),
            layout = listOf("custom_tool.sh"),
            outputs = listOf(
                ToolOutput(
                    toolId = "custom_tool",
                    file = "custom_tool",
                    kind = ToolOutputKind.SCRIPT,
                    probe = ToolProbe(args = listOf("--custom-flag"), acceptedExitCodes = setOf(0, 42)),
                    requiredForProduct = false,
                ),
            ),
            buildPackages = listOf("bash"),
        )

        assertFalse(ToolCapabilities.isRecipeSupported("custom_tool"))
        assertFalse(ToolCatalog.isCatalogTool("custom_tool"))

        ToolGroupCatalog.withTestGroup(testGroup) {
            // 1. Visible in ToolCapabilities
            assertTrue(ToolCapabilities.isRecipeSupported("custom_tool"))
            assertEquals("test-script-group", ToolCapabilities.recipeGroupFor("custom_tool"))
            val capability = ToolCapabilities.capabilityFor("custom_tool")
            assertNotNull(capability)
            assertEquals(listOf("--custom-flag"), capability.probeArguments)
            assertEquals(setOf(0, 42), capability.acceptedExitCodes)

            // 2. Visible in ToolCatalog
            assertTrue(ToolCatalog.isCatalogTool("custom_tool"))
            val entry = ToolCatalog.entryFor("custom_tool")
            assertNotNull(entry)
            assertEquals("custom_tool", entry.id)
            assertEquals("test-script-group", entry.recipeGroup)
            assertEquals(ToolKind.NATIVE, entry.kind)
            assertFalse(entry.requiredForProduct)

            // 3. Probes generated correctly
            val probePlan = ToolCatalog.probePlan("custom_tool", "/opt/tools/bin", "custom_tool")
            assertEquals(listOf("/opt/tools/bin/custom_tool", "--custom-flag"), probePlan.argv)
            assertEquals(listOf("test", "-x", "/opt/tools/bin/custom_tool"), probePlan.existsCheck)

            // 4. Build dispatch knows the group's recipe
            val resolvedGroup = ToolGroupCatalog.groupByToolId("custom_tool")
            assertNotNull(resolvedGroup)
            assertTrue(resolvedGroup.recipe is RecipeConfig.ScriptCopy)
            assertEquals(listOf("custom_tool.sh"), (resolvedGroup.recipe as RecipeConfig.ScriptCopy).files)
        }

        // Cleaned up after test block
        assertFalse(ToolCapabilities.isRecipeSupported("custom_tool"))
        assertFalse(ToolCatalog.isCatalogTool("custom_tool"))
    }
}
