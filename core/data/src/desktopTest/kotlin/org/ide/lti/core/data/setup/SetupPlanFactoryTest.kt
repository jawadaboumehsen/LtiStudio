package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.SetupPlanAction
import org.ide.lti.core.domain.setup.SetupPlanKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.ToolchainSetupState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SetupPlanFactoryTest {

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
