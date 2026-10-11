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

import org.ide.lti.core.data.setup.execute.handlers.toResolvedSource
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** [this] with the listed stages measured green. */
internal fun ToolchainSetupState.withStagesPassed(vararg stages: SetupStepStage): ToolchainSetupState =
    copy(steps = steps.map { if (it.stage in stages) it.copy(status = StepStatus.SUCCESS) else it })

/**
 * Regression tests for [SetupPlanFactory].
 *
 * Every plan that mutates tools produces a [SetupPlanAction.SwitchToolchain].
 * There are no SyncSources, BuildRecipes, or PublishTools actions.
 */
class SetupPlanFactoryTest {
    // -----------------------------------------------------------------------
    // FULL_SETUP
    // -----------------------------------------------------------------------

    @Test
    fun `full setup always produces exactly one SwitchToolchain action and no InstallPackages`() {
        val snapshot = ToolchainSetupState()
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.FULL_SETUP,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
            )
        assertEquals(1, plan.orderedActions.size, "FULL_SETUP must hold exactly one action")
        val switch = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        assertNotNull(switch, "FULL_SETUP must produce a SwitchToolchain action")
        assertTrue(
            plan.orderedActions.none { it is SetupPlanAction.InstallPackages },
            "FULL_SETUP must contain no InstallPackages",
        )
    }

    @Test
    fun `full setup SwitchToolchain contains all catalog groups`() {
        val snapshot = ToolchainSetupState()
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.FULL_SETUP,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
            )
        val switch = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().first()
        val catalogGroupIds = ToolGroupCatalog.allGroups.map { it.id }.toSet()
        assertEquals(
            catalogGroupIds,
            switch.inputs.keys,
            "SwitchToolchain inputs must contain exactly all catalog groups",
        )
    }

    @Test
    fun `full setup never produces InstallPackages even when bootstrap packages provided`() {
        val snapshot = ToolchainSetupState()
        val bootstrapPackages = listOf("build-essential", "cmake")
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.FULL_SETUP,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
                bootstrapPackages = bootstrapPackages,
            )
        assertEquals(1, plan.orderedActions.size, "FULL_SETUP must hold exactly one action")
        val switch = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        assertNotNull(switch, "FULL_SETUP must produce a SwitchToolchain action")
        assertTrue(
            plan.orderedActions.none { it is SetupPlanAction.InstallPackages },
            "FULL_SETUP must contain no InstallPackages",
        )
    }

    @Test
    fun `bootstrap packages plan produces InstallPackages and no SwitchToolchain`() {
        val snapshot = ToolchainSetupState()
        val bootstrapPackages = listOf("build-essential", "cmake")
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.BOOTSTRAP_PACKAGES,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
                bootstrapPackages = bootstrapPackages,
            )
        val install = plan.orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>().firstOrNull()
        assertNotNull(install, "BOOTSTRAP_PACKAGES plan must produce InstallPackages")
        assertEquals(bootstrapPackages, install.packages)
        // Bootstrap plan must NOT contain a SwitchToolchain — it only installs packages
        assertTrue(
            plan.orderedActions.none { it is SetupPlanAction.SwitchToolchain },
            "BOOTSTRAP_PACKAGES plan must not contain SwitchToolchain",
        )
    }

    // -----------------------------------------------------------------------
    // STAGE_RETRY
    // -----------------------------------------------------------------------

    @Test
    fun `stage retry of TOOLCHAIN_COMPILATION produces SwitchToolchain`() {
        val snapshot =
            ToolchainSetupState().withStagesPassed(
                SetupStepStage.WSL_DETECTION,
                SetupStepStage.SERVER_CONNECTIVITY,
                SetupStepStage.SYSTEM_PACKAGES,
            )
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.STAGE_RETRY,
                targetId = SetupStepStage.TOOLCHAIN_COMPILATION.name,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
            )
        val switch = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        assertNotNull(switch, "STAGE_RETRY of TOOLCHAIN_COMPILATION must produce SwitchToolchain")
    }

    @Test
    fun `stage retry of SYSTEM_PACKAGES produces InstallPackages only`() {
        val snapshot = ToolchainSetupState()
        val bootstrapPackages = listOf("openjdk-21-jdk")
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.STAGE_RETRY,
                targetId = SetupStepStage.SYSTEM_PACKAGES.name,
                snapshot = snapshot,
                environmentKey = "Ubuntu-24.04",
                bootstrapPackages = bootstrapPackages,
            )
        val install = plan.orderedActions.filterIsInstance<SetupPlanAction.InstallPackages>().firstOrNull()
        assertNotNull(install, "STAGE_RETRY of SYSTEM_PACKAGES must produce InstallPackages")
        assertTrue(
            plan.orderedActions.none { it is SetupPlanAction.SwitchToolchain },
            "STAGE_RETRY of SYSTEM_PACKAGES must not produce SwitchToolchain",
        )
    }

    // -----------------------------------------------------------------------
    // REPAIR_TOOL
    // -----------------------------------------------------------------------

    @Test
    fun `repair tool produces SwitchToolchain with forceRebuildGroups set`() {
        // lpmake belongs to android-tools
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.REPAIR_TOOL,
                targetId = "lpmake",
                snapshot = ToolchainSetupState(),
                environmentKey = "Ubuntu-24.04",
            )
        val switch = plan.orderedActions.filterIsInstance<SetupPlanAction.SwitchToolchain>().firstOrNull()
        assertNotNull(switch, "REPAIR_TOOL must produce SwitchToolchain")
        assertTrue(
            switch.forceRebuildGroups.isNotEmpty(),
            "REPAIR_TOOL SwitchToolchain must specify forceRebuildGroups",
        )
        val forceGroup = switch.forceRebuildGroups.first()
        val expectedGroup = ToolGroupCatalog.groupByToolId("lpmake")
        assertEquals(
            expectedGroup?.id,
            forceGroup,
            "forceRebuildGroups must contain the owning group for lpmake",
        )
    }

    @Test
    fun `repair tool with unknown tool id produces empty plan`() {
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.REPAIR_TOOL,
                targetId = "totally-unknown-binary",
                snapshot = ToolchainSetupState(),
                environmentKey = "Ubuntu-24.04",
            )
        assertTrue(plan.orderedActions.isEmpty(), "REPAIR_TOOL with unknown tool must produce empty plan")
    }

    // -----------------------------------------------------------------------
    // CACHE_RESET
    // -----------------------------------------------------------------------

    @Test
    fun `cache reset produces no actions`() {
        val plan =
            SetupPlanFactory.createPlan(
                kind = SetupPlanKind.CACHE_RESET,
                snapshot = ToolchainSetupState(),
                environmentKey = "Ubuntu-24.04",
            )
        assertTrue(plan.orderedActions.isEmpty(), "CACHE_RESET must produce an empty action list")
    }

    // -----------------------------------------------------------------------
    // Revision hash stability and staleness
    // -----------------------------------------------------------------------

    @Test
    fun `revision hash is stable for the same snapshot and inputs`() {
        val snapshot = ToolchainSetupState()
        val h1 = SetupPlanFactory.computeRevisionHash(snapshot)
        val h2 = SetupPlanFactory.computeRevisionHash(snapshot)
        assertEquals(h1, h2, "Revision hash must be stable for the same inputs")
    }

    @Test
    fun `revision hash includes switch inputs and selection revision`() {
        val snapshot = ToolchainSetupState()
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val h1 = SetupPlanFactory.computeRevisionHash(snapshot, switchInputs = inputs, selectionRevision = 1L)
        val h2 = SetupPlanFactory.computeRevisionHash(snapshot, switchInputs = inputs, selectionRevision = 2L)
        assertTrue(h1 != h2, "Revision hash must differ when selectionRevision changes")
    }

    // -----------------------------------------------------------------------
    // defaultCatalogInputs — no substitution, no null fallback
    // -----------------------------------------------------------------------

    @Test
    fun `defaultCatalogInputs returns exactly the catalog groups`() {
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        val catalogGroupIds = ToolGroupCatalog.DEFAULT_GROUPS.map { it.id }.toSet()
        assertEquals(catalogGroupIds, inputs.keys, "defaultCatalogInputs must contain all catalog groups")
    }

    @Test
    fun `defaultCatalogInputs Git inputs carry non-blank recommended commit`() {
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        inputs.values.filterIsInstance<org.ide.lti.core.domain.setup.ResolvedInput.Git>().forEach { git ->
            assertTrue(git.commit.isNotBlank(), "Git input for ${git.group.value} must have non-blank commit")
            assertEquals(
                40,
                git.commit.length,
                "Git input commit for ${git.group.value} must be a 40-char SHA",
            )
        }
    }

    @Test
    fun `defaultCatalogInputs Release inputs carry non-blank version and sha256`() {
        val inputs = SetupPlanFactory.defaultCatalogInputs()
        inputs.values.filterIsInstance<org.ide.lti.core.domain.setup.ResolvedInput.Release>().forEach { rel ->
            assertTrue(rel.version.isNotBlank(), "Release input for ${rel.group.value} must have non-blank version")
            assertTrue(rel.sha256.isNotBlank(), "Release input for ${rel.group.value} must have non-blank sha256")
            assertTrue(rel.url.isNotBlank(), "Release input for ${rel.group.value} must have non-blank url")
        }
    }

    @Test
    fun `a source survives the trip through a confirmed plan with its submodule commits`() {
        val group = ToolGroupCatalog.ANDROID_TOOLS
        val source =
            ResolvedSource.Git(
                repoUrl = "https://example.com/fork.git",
                ref = ToolRef.Tag("v9"),
                commit = "a".repeat(40),
                submodules = mapOf("vendor/core" to "b".repeat(40), "vendor/extras" to "c".repeat(40)),
            )

        val input = with(SetupPlanFactory) { source.toResolvedInput(group) }
        val back = input.toResolvedSource()

        assertEquals(source.submodules, (input as ResolvedInput.Git).submoduleCommits)
        assertEquals(source, back, "what the plan froze is exactly what gets built")
    }
}
