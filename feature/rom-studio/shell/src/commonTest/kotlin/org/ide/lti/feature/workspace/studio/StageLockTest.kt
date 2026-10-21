package org.ide.lti.feature.workspace.studio

import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StageLockTest {
    private val items = createDefaultPipelineRailItems()
    private val order = items.map { it.stageId }

    private fun report(vararg stages: StageId?, severity: Severity = Severity.ERROR) = ValidationReport(
        errors = stages.map { ValidationError(it, null, "f", "C", severity, "m") },
    )

    private fun open(report: ValidationReport) = lockStages(items, report).filter { it.isEnabled }.map { it.stageId }

    @Test
    fun everyStageOpensWhenNothingHasErrors() {
        assertEquals(order, open(ValidationReport(emptyList())))
    }

    @Test
    fun aStageWithErrorsIsOpenButLocksEveryStageAfterIt() {
        val locked = lockStages(items, report(StageId.WORK_TREE_ASSEMBLY))
        assertEquals(
            listOf(StageId.FIRMWARE_ACQUISITION, StageId.FIRMWARE_EXTRACTION, StageId.WORK_TREE_ASSEMBLY),
            locked.filter { it.isEnabled }.map { it.stageId },
        )
        assertEquals("Finish the Assemble stage first", locked.first { it.stageId == StageId.DEBLOAT }.disabledReason)
    }

    @Test
    fun theDeviceConfigurationGatesEveryStage() {
        val locked = lockStages(items, report(null))
        assertEquals(emptyList(), locked.filter { it.isEnabled })
        assertEquals("Finish the device configuration first", locked.first().disabledReason)
    }

    @Test
    fun warningsDoNotLock() {
        assertEquals(order, open(report(StageId.FIRMWARE_ACQUISITION, severity = Severity.WARNING)))
    }

    @Test
    fun theBlockingStageIsTheFirstWithErrorsBeforeTheTarget() {
        val r = report(StageId.DEBLOAT, StageId.FIRMWARE_EXTRACTION)
        assertEquals(StageId.FIRMWARE_EXTRACTION, stageBlocking(StageId.BUILD_FLASHABLE_ZIP, order, r))
        assertNull(stageBlocking(StageId.FIRMWARE_EXTRACTION, order, r), "a stage with errors itself stays open")
    }

    @Test
    fun aDeviceProblemReportedUnderAStageLocksAsTheDevice() {
        val superSize = ValidationError(
            StageId.BUILD_FLASHABLE_ZIP,
            DeviceConfigReadiness.DEVICE_OBJECT,
            "superPartitionBytes",
            "C",
            Severity.ERROR,
            "m",
        )
        val locked = lockStages(items, ValidationReport(listOf(superSize)))
        assertEquals(emptyList(), locked.filter { it.isEnabled })
        assertEquals("Finish the device configuration first", locked.last().disabledReason)
    }
}
