/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.datetime.Instant
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.SigningPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class StudioDraftControllerTest {
    private val target = DefaultTargetCatalog.PQ84P01_DEFAULT
    private val workspaceId = "workspace-1"

    @Test
    fun olderSaveReconcilesMatchingPathsOnly() {
        val controller = controller()
        controller.applyAcquisition(acquisition(TargetRegion.CHINA))
        controller.applyExtraction(ExtractionSettings(adapter = "payload-dumper-go"))
        val savedAsOfRevision = controller.draft.value.draftRevision
        val committed = snapshot(controller.draft.value, "snapshot-2")

        controller.reconcileAfterSave(committed, savedAsOfRevision)

        assertTrue(controller.draft.value.dirtyPaths.isEmpty())
        assertEquals(committed.id, controller.draft.value.baseSnapshotId)
        assertEquals(committed.digest, controller.draft.value.baseSnapshotDigest)
        assertEquals(committed.acquisition, controller.draft.value.acquisition)
        assertEquals(committed.extraction, controller.draft.value.extraction)
    }

    @Test
    fun newerDraftStaysDirty() {
        val controller = controller()
        controller.applyAcquisition(acquisition(TargetRegion.CHINA))
        controller.applyExtraction(ExtractionSettings(adapter = "payload-dumper-go"))
        val savedAsOfRevision = controller.draft.value.draftRevision
        val committed = snapshot(controller.draft.value, "snapshot-2")
        val newestAcquisition = acquisition(TargetRegion.GLOBAL)

        controller.applyAcquisition(newestAcquisition)
        controller.reconcileAfterSave(committed, savedAsOfRevision)

        assertEquals(newestAcquisition, controller.draft.value.acquisition)
        assertTrue("acquisition" in controller.draft.value.dirtyPaths)
        assertFalse("extraction" in controller.draft.value.dirtyPaths)
        assertEquals(committed.extraction, controller.draft.value.extraction)
    }

    @Test
    fun resetDuplicateAndDeleteCoverAllSupportedSettings() {
        val base = snapshot(WorkspacePipelineDraft.create(workspaceId, null, target), "base")
        val controller = StudioDraftController(WorkspacePipelineDraft.create(workspaceId, base, target), target)
        controller.applyAcquisition(acquisition(TargetRegion.CHINA))
        controller.applyExtraction(ExtractionSettings(adapter = "payload-dumper-go"))
        val beforeDuplicate = controller.draft.value
        val duplicate = controller.duplicate()

        duplicate.applyExtraction(ExtractionSettings(adapter = "lpunpack"))
        assertEquals(beforeDuplicate.extraction, controller.draft.value.extraction)
        assertNotEquals(duplicate.draft.value.extraction, controller.draft.value.extraction)

        val revisionBeforeReset = controller.draft.value.draftRevision
        controller.reset()
        assertTrue(controller.draft.value.draftRevision > revisionBeforeReset)
        assertEquals(base.acquisition, controller.draft.value.acquisition)
        assertEquals(base.extraction, controller.draft.value.extraction)
        assertEquals(base.assembly, controller.draft.value.assembly)
        assertEquals(base.debloat, controller.draft.value.debloat)
        assertEquals(base.customization, controller.draft.value.customization)
        assertEquals(base.build, controller.draft.value.build)
        assertEquals(base.build.signing, controller.draft.value.signing)
        assertEquals(base.release, controller.draft.value.release)
        assertEquals(base.publish, controller.draft.value.publish)
        assertTrue(controller.draft.value.dirtyPaths.isEmpty())

        controller.delete()
        assertEquals(
            WorkspacePipelineDraft.create(workspaceId, null, target).acquisition,
            controller.draft.value.acquisition,
        )
        assertEquals(ExtractionSettings(), controller.draft.value.extraction)
        assertEquals(SigningPolicy(), controller.draft.value.signing)
        assertTrue(controller.draft.value.dirtyPaths.isEmpty())
    }

    @Test
    fun revisionsAreMonotonicAndInvalidEditsExposeDiagnostics() {
        val invalidTarget = target.copy(availableRegions = listOf(TargetRegion.CHINA))
        val controller = controller(invalidTarget)
        val first = controller.draft.value.draftRevision
        controller.applyAcquisition(acquisition(TargetRegion.GLOBAL))
        val second = controller.draft.value.draftRevision
        controller.applyExtraction(ExtractionSettings(adapter = "AUTO"))
        val third = controller.draft.value.draftRevision
        controller.reset()

        assertTrue(first < second && second < third && third < controller.draft.value.draftRevision)
        assertTrue(
            controller.draft.value.validationDiagnostics.errors.any { error ->
                error.fieldPath == "acquisition.region"
            },
        )
    }

    private fun controller(target: TargetDevice? = this.target): StudioDraftController = StudioDraftController(
        WorkspacePipelineDraft.create(workspaceId, null, target),
        target,
    )

    private fun acquisition(region: TargetRegion): AcquisitionSettings = AcquisitionSettings(
        region = region,
        firmware = if (region == TargetRegion.CHINA) {
            DefaultTargetCatalog.PQ84P01_CHINA_FIRMWARES.first()
        } else {
            DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first()
        },
    )

    private fun snapshot(draft: WorkspacePipelineDraft, id: String): ConfigurationSnapshot =
        ConfigurationSnapshot.create(
            id = id,
            workspaceId = draft.workspaceId,
            profileRevision = 1,
            acquisition = draft.acquisition,
            extraction = draft.extraction,
            assembly = draft.assembly,
            debloat = draft.debloat,
            customization = draft.customization,
            build = draft.build,
            release = draft.release,
            publish = draft.publish,
            createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        )
}
