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

import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolCatalogConsistencyTest {

    @Test
    fun testAllCatalogEntriesHaveValidMetadata() {
        val allGroups = ToolCapabilities.allRecipeGroups()
        assertTrue(ToolCatalog.entries.isNotEmpty(), "ToolCatalog must not be empty")

        for (entry in ToolCatalog.entries) {
            assertTrue(entry.id.isNotBlank(), "Tool ID must not be blank")
            assertTrue(
                entry.recipeGroup in allGroups,
                "Tool '${entry.id}' recipeGroup '${entry.recipeGroup}' must be known to ToolCapabilities",
            )
            assertNotNull(entry.kind, "Tool '${entry.id}' must declare a ToolKind")
        }
    }

    @Test
    fun testJarAndNativeKindsMatchBinaryExpectations() {
        val jarTools = ToolCatalog.entries.filter { it.kind == ToolKind.JAR }.map { it.id }.toSet()
        val expectedJarTools = setOf("signapk", "apktool")
        assertEquals(expectedJarTools, jarTools, "JAR tools in catalog must match expected jar tools")

        val nativeTools = ToolCatalog.entries.filter { it.kind == ToolKind.NATIVE }.map { it.id }.toSet()
        assertTrue(nativeTools.contains("lpmake"))
        assertTrue(nativeTools.contains("erofsfuse"))
        assertTrue(nativeTools.contains("adb"))
        assertTrue(nativeTools.contains("mkfs.erofs"))
        assertTrue(nativeTools.contains("payload-dumper-go"))

        for (entry in ToolCatalog.entries) {
            if (entry.kind == ToolKind.JAR) {
                assertEquals("${entry.id}.jar", entry.binaryName)
            } else {
                assertEquals(entry.id, entry.binaryName)
            }
        }
    }

    @Test
    fun testRequiredProductToolsAgreement() {
        val catalogRequired = ToolCatalog.REQUIRED_PRODUCT_TOOLS
        val policyRequired = ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS

        assertEquals(
            catalogRequired,
            policyRequired,
            "ToolchainReadinessPolicy.REQUIRED_PRODUCT_TOOLS must agree with ToolCatalog.REQUIRED_PRODUCT_TOOLS",
        )

        val expectedTen = setOf(
            "payload-dumper-go",
            "lpunpack",
            "lpmake",
            "simg2img",
            "erofsfuse",
            "mkfs.erofs",
            "avbtool",
            "unpack_bootimg",
            "img2sdat",
            "signapk",
        )
        assertEquals(expectedTen, catalogRequired, "Catalog must define exactly the 10 required product tools")
    }

    @Test
    fun testSetupPlanFactoryPublishesAllCatalogIdsOnFullSetup() {
        val plan = SetupPlanFactory.createPlan(
            kind = SetupPlanKind.FULL_SETUP,
            snapshot = ToolchainSetupState(),
            environmentKey = "Ubuntu-24.04",
        )
        val publishAction = plan.orderedActions.filterIsInstance<SetupPlanAction.PublishTools>().firstOrNull()
        assertNotNull(publishAction, "Full setup plan must include PublishTools action")

        val expectedIds = ToolCatalog.ALL_TOOL_IDS.toList().sorted()
        assertEquals(
            expectedIds,
            publishAction.toolIds.sorted(),
            "Full setup PublishTools must request all catalog tool IDs",
        )
    }

    @Test
    fun testSetupPlanFactoryPublishesUnpublishedCatalogIdsOnStageRetry() {
        val published = setOf("lpmake", "unpack_bootimg")
        val snapshot = ToolchainSetupState(publishedToolIds = published)
            .withStagesPassed(SetupStepStage.REPO_SYNCHRONIZATION)

        val plan = SetupPlanFactory.createPlan(
            kind = SetupPlanKind.STAGE_RETRY,
            targetId = SetupStepStage.TOOLCHAIN_COMPILATION.name,
            snapshot = snapshot,
            environmentKey = "Ubuntu-24.04",
        )

        val publishAction = plan.orderedActions.filterIsInstance<SetupPlanAction.PublishTools>().firstOrNull()
        assertNotNull(publishAction, "TOOLCHAIN_COMPILATION retry plan must include PublishTools action")

        val expectedUnpublished = (ToolCatalog.ALL_TOOL_IDS - published).toList().sorted()
        assertEquals(
            expectedUnpublished,
            publishAction.toolIds.sorted(),
            "Stage retry PublishTools must request exactly unpublished catalog tool IDs",
        )
    }

    @Test
    fun testProvisionerBinariesAndToolsComeFromCatalogAndAgree() {
        val catalogIds = ToolCatalog.ALL_TOOL_IDS

        for (coreBin in WslToolchainProvisioner.CORE_BINARIES) {
            assertTrue(
                coreBin in catalogIds,
                "WslToolchainProvisioner.CORE_BINARIES entry '$coreBin' must exist in ToolCatalog",
            )
        }
        assertEquals(
            ToolCatalog.CORE_BINARIES,
            WslToolchainProvisioner.CORE_BINARIES,
            "WslToolchainProvisioner.CORE_BINARIES must agree with ToolCatalog.CORE_BINARIES",
        )

        for (coreTool in WslToolchainProvisioner.ALL_CORE_TOOLS) {
            assertTrue(
                coreTool in catalogIds,
                "WslToolchainProvisioner.ALL_CORE_TOOLS entry '$coreTool' must exist in ToolCatalog",
            )
        }
        assertEquals(
            ToolCatalog.ALL_CORE_TOOLS,
            WslToolchainProvisioner.ALL_CORE_TOOLS,
            "WslToolchainProvisioner.ALL_CORE_TOOLS must agree with ToolCatalog.ALL_CORE_TOOLS",
        )
    }
}
