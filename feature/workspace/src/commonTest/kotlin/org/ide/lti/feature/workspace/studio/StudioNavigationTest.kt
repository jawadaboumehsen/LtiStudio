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

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.ScrollAnchor
import org.ide.lti.core.model.studio.StudioPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioNavigationTest {

    @Test
    fun catalogHasExactlyEightStagesInContractOrderWithExactSubobjectCounts() {
        val stages = StudioDestinationCatalog.stages
        assertEquals(8, stages.size)

        val expectedStages = listOf(
            StageId.FIRMWARE_ACQUISITION to 5,
            StageId.FIRMWARE_EXTRACTION to 5,
            StageId.WORK_TREE_ASSEMBLY to 5,
            StageId.DEBLOAT to 6,
            StageId.MODULE_APPLICATION to 6,
            StageId.BUILD_FLASHABLE_ZIP to 7,
            StageId.GENERATE_OTA_MANIFEST to 5,
            StageId.PUBLISH_RELEASE to 8,
        )

        expectedStages.forEachIndexed { index, (expectedStageId, expectedCount) ->
            val stage = stages[index]
            assertEquals(expectedStageId, stage.stageId)
            assertEquals(index + 1, stage.position)
            assertEquals(
                expectedCount,
                stage.subobjects.size,
                "Stage $expectedStageId expected $expectedCount subobjects but got ${stage.subobjects.size}",
            )
        }
    }

    @Test
    fun catalogHasUniqueObjectIdsAcrossAllStages() {
        val allSubobjects = StudioDestinationCatalog.stages.flatMap { it.subobjects }
        val allIds = allSubobjects.map { it.id }
        val uniqueIds = allIds.toSet()

        assertEquals(allIds.size, uniqueIds.size, "Duplicate subobject IDs found: ${allIds - uniqueIds}")
        assertEquals(47, allSubobjects.size)
    }

    @Test
    fun overviewIsNotInStages() {
        val stageTitles = StudioDestinationCatalog.stages.map { it.title.lowercase() }
        assertTrue(stageTitles.none { it.contains("overview") })

        val subobjectIds = StudioDestinationCatalog.stages.flatMap { it.subobjects }.map { it.id.lowercase() }
        assertTrue(subobjectIds.none { it.contains("overview") })
    }

    @Test
    fun catalogLookupsReturnExpectedDestinationsAndSubobjects() {
        val firmwareAcquisition = StudioDestinationCatalog.stage(StageId.FIRMWARE_ACQUISITION)
        assertNotNull(firmwareAcquisition)
        assertEquals("Acquire", firmwareAcquisition.title)

        val owningStage = StudioDestinationCatalog.owningStage("firmware-baseline")
        assertNotNull(owningStage)
        assertEquals(StageId.FIRMWARE_ACQUISITION, owningStage.stageId)

        val subobject = StudioDestinationCatalog.subobject("system-ext-handling")
        assertNotNull(subobject)
        assertEquals("system_ext handling", subobject.title)

        assertNull(StudioDestinationCatalog.owningStage("non-existent-subobject"))
        assertNull(StudioDestinationCatalog.subobject("non-existent-subobject"))
    }

    @Test
    fun deepLinkBeatsStoredRestoration() {
        val storedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.DEBLOAT,
            editorObjectId = "inventory",
        )

        val resolved = resolveStudioEntry(
            presentation = storedPresentation,
            requestedStageId = StageId.FIRMWARE_ACQUISITION,
            requestedObjectId = "firmware-baseline",
        )

        val expected = StudioEntry.Editor(
            stageId = StageId.FIRMWARE_ACQUISITION,
            objectId = "firmware-baseline",
            reason = StudioEntryReason.DEEP_LINK,
        )
        assertEquals(expected, resolved)
    }

    @Test
    fun storedObjectThatNoLongerExistsFallsBackToOwningStageWithObjectNotFound() {
        val storedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.DEBLOAT,
            editorObjectId = "deleted-obsolete-subobject",
        )

        val resolved = resolveStudioEntry(
            presentation = storedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )

        val expected = StudioEntry.Editor(
            stageId = StageId.DEBLOAT,
            objectId = "inventory",
            reason = StudioEntryReason.OBJECT_NOT_FOUND,
        )
        assertEquals(expected, resolved)
    }

    @Test
    fun openedWorkspaceWithNoStoredEditorRestoresToOverview() {
        val requestedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = null,
            editorObjectId = null,
        )

        val resolvedFromEmptyStoredStage = resolveStudioEntry(
            presentation = requestedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.RESTORED), resolvedFromEmptyStoredStage)

        // A requested object that belongs to no stage is an OBJECT miss, not a stage miss.
        assertEquals(
            StudioEntry.Overview(StudioEntryReason.OBJECT_NOT_FOUND),
            resolveStudioEntry(presentation = null, requestedStageId = null, requestedObjectId = "no-such-object"),
        )
    }

    @Test
    fun freshWorkspaceGivesOverviewNewWorkspace() {
        val nullPresentationResolved = resolveStudioEntry(
            presentation = null,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE), nullPresentationResolved)

        val unopenedPresentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = false,
            editorStageId = StageId.FIRMWARE_ACQUISITION,
            editorObjectId = "source",
        )
        val unopenedResolved = resolveStudioEntry(
            presentation = unopenedPresentation,
            requestedStageId = null,
            requestedObjectId = null,
        )
        assertEquals(StudioEntry.Overview(StudioEntryReason.NEW_WORKSPACE), unopenedResolved)
    }

    @Test
    fun resolvingReturnsValueWithoutMutatingInputPresentation() {
        val original = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.WORK_TREE_ASSEMBLY,
            editorObjectId = "work-trees",
            scrollAnchors = mapOf("WORK_TREE_ASSEMBLY/work-trees" to ScrollAnchor(anchorId = "sec-1", offset = 120)),
            expandedSections = mapOf("work-trees" to setOf("sec-1", "sec-2")),
            navigatorWidth = 260,
            lastFocusId = "input-1",
        )

        val clonedOriginal = original.copy()

        val resolved = resolveStudioEntry(
            presentation = original,
            requestedStageId = StageId.DEBLOAT,
            requestedObjectId = "inventory",
        )

        assertEquals(
            StudioEntry.Editor(StageId.DEBLOAT, "inventory", StudioEntryReason.DEEP_LINK),
            resolved,
        )
        assertEquals(clonedOriginal, original, "Input presentation must not be mutated during resolution")
    }

    @Test
    fun pureHelpersWithEditorAndWithNavigatorWidth() {
        val initial = StudioPresentation(workspaceId = "ws-1", navigatorWidth = 240)
        val withEditor = initial.withEditor(StageId.PUBLISH_RELEASE, "provider")
        assertEquals(StageId.PUBLISH_RELEASE, withEditor.editorStageId)
        assertEquals("provider", withEditor.editorObjectId)
        assertTrue(withEditor.hasOpened, "Selecting an editor must mark the workspace opened, or restoration is lost")
        assertEquals(initial.workspaceId, withEditor.workspaceId)
        assertNotEquals(initial, withEditor)

        val clampedLow = initial.withNavigatorWidth(150)
        assertEquals(StudioPresentation.MIN_NAVIGATOR_WIDTH, clampedLow.navigatorWidth)

        val clampedHigh = initial.withNavigatorWidth(500)
        assertEquals(StudioPresentation.MAX_NAVIGATOR_WIDTH, clampedHigh.navigatorWidth)

        val inRange = initial.withNavigatorWidth(300)
        assertEquals(300, inRange.navigatorWidth)
    }
}
