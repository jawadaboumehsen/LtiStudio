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
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** [this] with the listed stages measured green. */
internal fun ToolchainSetupState.withStagesPassed(vararg stages: SetupStepStage): ToolchainSetupState =
    copy(steps = steps.map { if (it.stage in stages) it.copy(status = StepStatus.SUCCESS) else it })

class SetupPlanFactoryTest {

    private fun actionsOf(kind: SetupPlanKind, snapshot: ToolchainSetupState, target: SetupStepStage? = null) =
        SetupPlanFactory.createPlan(
            kind = kind,
            targetId = target?.name,
            snapshot = snapshot,
            environmentKey = "Ubuntu-24.04",
        ).orderedActions.map { it::class.simpleName }

    @Test
    fun `a clean machine's toolchain retry syncs sources before building`() {
        // Packages in, nothing synced or built: the recommended retry must not compile missing sources.
        val clean = ToolchainSetupState().withStagesPassed(
            SetupStepStage.WSL_DETECTION,
            SetupStepStage.SYSTEM_PACKAGES,
            SetupStepStage.SERVER_CONNECTIVITY,
            SetupStepStage.SYSTEM_DIAGNOSTICS,
        )

        assertEquals(
            listOf("SyncSources", "BuildRecipes", "PublishTools"),
            actionsOf(SetupPlanKind.STAGE_RETRY, clean, SetupStepStage.TOOLCHAIN_COMPILATION),
        )
    }

    @Test
    fun `full setup that syncs sources also builds, even when the toolchain looked ready`() {
        // New pins with a green toolchain: the sync invalidates the changed groups, so a build must follow.
        val newPins = ToolchainSetupState().withStagesPassed(
            SetupStepStage.SYSTEM_DIAGNOSTICS,
            SetupStepStage.TOOLCHAIN_COMPILATION,
        )

        assertEquals(
            listOf("SyncSources", "BuildRecipes", "PublishTools"),
            actionsOf(SetupPlanKind.FULL_SETUP, newPins),
        )
    }

    @Test
    fun testFullSetupPublishesAllCatalogIds() {
        val snapshot = ToolchainSetupState()
        val plan = SetupPlanFactory.createPlan(
            kind = SetupPlanKind.FULL_SETUP,
            snapshot = snapshot,
            environmentKey = "Ubuntu-24.04",
        )

        val publishAction = plan.orderedActions.filterIsInstance<SetupPlanAction.PublishTools>().firstOrNull()
        assertNotNull(publishAction, "Full setup plan must include PublishTools action")

        val expectedIds = ToolCatalog.ALL_TOOL_IDS.toList().sorted()
        assertEquals(
            expectedIds,
            publishAction.toolIds.sorted(),
            "Full setup must publish all catalog tool IDs",
        )
    }

    @Test
    fun testStageRetryOfToolchainCompilationProducesPublishToolsWithUnpublishedCatalogIds() {
        val published = setOf("lpmake", "unpack_bootimg", "signapk")
        val snapshot = ToolchainSetupState(publishedToolIds = published)
            .withStagesPassed(SetupStepStage.REPO_SYNCHRONIZATION)

        val plan = SetupPlanFactory.createPlan(
            kind = SetupPlanKind.STAGE_RETRY,
            targetId = SetupStepStage.TOOLCHAIN_COMPILATION.name,
            snapshot = snapshot,
            environmentKey = "Ubuntu-24.04",
        )

        val publishAction = plan.orderedActions.filterIsInstance<SetupPlanAction.PublishTools>().firstOrNull()
        assertNotNull(publishAction, "Stage retry of TOOLCHAIN_COMPILATION must include PublishTools action")

        val expectedUnpublished = (ToolCatalog.ALL_TOOL_IDS - published).toList().sorted()
        assertEquals(
            expectedUnpublished,
            publishAction.toolIds.sorted(),
            "Stage retry of TOOLCHAIN_COMPILATION must request publish for unpublished catalog IDs",
        )
    }

    @Test
    fun testStageRetryOfToolchainCompilationWhenAllPublishedProducesNoPublishTools() {
        val allPublished = ToolCatalog.ALL_TOOL_IDS
        val snapshot = ToolchainSetupState(publishedToolIds = allPublished)
            .withStagesPassed(SetupStepStage.REPO_SYNCHRONIZATION)

        val plan = SetupPlanFactory.createPlan(
            kind = SetupPlanKind.STAGE_RETRY,
            targetId = SetupStepStage.TOOLCHAIN_COMPILATION.name,
            snapshot = snapshot,
            environmentKey = "Ubuntu-24.04",
        )

        val publishAction = plan.orderedActions.filterIsInstance<SetupPlanAction.PublishTools>().firstOrNull()
        assertEquals(
            null,
            publishAction,
            "When all catalog tools are published, no PublishTools action should be produced",
        )
    }
}
