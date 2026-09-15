/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.MapSettings
import com.russhwolf.settings.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.ScrollAnchor
import org.ide.lti.core.model.studio.StudioPresentation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StudioPresentationStoreTest {

    @Test
    fun defaultStateIsEmpty() = runTest {
        val settings = MapSettings()
        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        assertTrue(store.currentPresentations.isEmpty())
        assertTrue(store.presentations.first().isEmpty())
        assertNull(store.get("ws-1"))
        assertNull(store.presentation("ws-1").first())
    }

    @Test
    fun twoWorkspacesKeepIndependentSelectionScrollAnchorsAndExpansions() = runTest {
        val settings = MapSettings()
        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val pres1 = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.FIRMWARE_ACQUISITION,
            editorObjectId = "source",
            scrollAnchors = mapOf("FIRMWARE_ACQUISITION/source" to ScrollAnchor("anchor-1", 100)),
            expandedSections = mapOf("source" to setOf("sec-a", "sec-b")),
            navigatorWidth = 250,
            lastFocusId = "focus-1",
        )

        val pres2 = StudioPresentation(
            workspaceId = "ws-2",
            hasOpened = true,
            editorStageId = StageId.DEBLOAT,
            editorObjectId = "inventory",
            scrollAnchors = mapOf("DEBLOAT/inventory" to ScrollAnchor("anchor-2", 200)),
            expandedSections = mapOf("inventory" to setOf("sec-c")),
            navigatorWidth = 280,
            lastFocusId = "focus-2",
        )

        store.put(pres1)
        store.put(pres2)

        val loaded1 = store.get("ws-1")
        val loaded2 = store.get("ws-2")

        assertNotNull(loaded1)
        assertNotNull(loaded2)

        assertEquals(StageId.FIRMWARE_ACQUISITION, loaded1.editorStageId)
        assertEquals("source", loaded1.editorObjectId)
        assertEquals(mapOf("FIRMWARE_ACQUISITION/source" to ScrollAnchor("anchor-1", 100)), loaded1.scrollAnchors)
        assertEquals(mapOf("source" to setOf("sec-a", "sec-b")), loaded1.expandedSections)
        assertEquals(250, loaded1.navigatorWidth)
        assertEquals("focus-1", loaded1.lastFocusId)

        assertEquals(StageId.DEBLOAT, loaded2.editorStageId)
        assertEquals("inventory", loaded2.editorObjectId)
        assertEquals(mapOf("DEBLOAT/inventory" to ScrollAnchor("anchor-2", 200)), loaded2.scrollAnchors)
        assertEquals(mapOf("inventory" to setOf("sec-c")), loaded2.expandedSections)
        assertEquals(280, loaded2.navigatorWidth)
        assertEquals("focus-2", loaded2.lastFocusId)

        // Mutating ws-1 does not change ws-2
        val updated1 = pres1.copy(
            editorObjectId = "firmware-baseline",
            navigatorWidth = 320,
            scrollAnchors = emptyMap(),
        )
        store.put(updated1)

        val reloaded2 = store.get("ws-2")
        assertEquals(pres2, reloaded2)
    }

    @Test
    fun scrollAnchorAndExpansionRoundTripExactly() = runTest {
        val settings = MapSettings()
        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val presentation = StudioPresentation(
            workspaceId = "ws-1",
            hasOpened = true,
            editorStageId = StageId.BUILD_FLASHABLE_ZIP,
            editorObjectId = "package-signing",
            scrollAnchors = mapOf(
                "BUILD_FLASHABLE_ZIP/package-signing" to ScrollAnchor(anchorId = "key-section", offset = 450),
                "BUILD_FLASHABLE_ZIP/images" to ScrollAnchor(anchorId = "super-partition", offset = 0),
            ),
            expandedSections = mapOf(
                "package-signing" to setOf("avb", "ota-certs", "keys"),
                "images" to setOf("partitions"),
            ),
            navigatorWidth = 275,
            lastFocusId = "key-path-input",
        )

        store.put(presentation)

        // Reload using a fresh store instance over the same settings to verify serialization round trip
        val reloadedStore = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val retrieved = reloadedStore.get("ws-1")

        assertNotNull(retrieved)
        assertEquals(presentation.scrollAnchors, retrieved.scrollAnchors)
        assertEquals(presentation.expandedSections, retrieved.expandedSections)
        assertEquals(presentation, retrieved)
    }

    @Test
    fun navigatorWidthIsClampedOnBothWriteAndRead() = runTest {
        val settings = MapSettings()
        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        // 1. Clamped on write
        val underflowOnWrite = StudioPresentation(workspaceId = "ws-low", navigatorWidth = 120)
        val overflowOnWrite = StudioPresentation(workspaceId = "ws-high", navigatorWidth = 500)

        store.put(underflowOnWrite)
        store.put(overflowOnWrite)

        assertEquals(StudioPresentation.MIN_NAVIGATOR_WIDTH, store.get("ws-low")?.navigatorWidth)
        assertEquals(StudioPresentation.MAX_NAVIGATOR_WIDTH, store.get("ws-high")?.navigatorWidth)

        // 2. Clamped on read when raw corrupted or out-of-range values are in settings
        val rawJson = """
            {
                "schemaVersion": 1,
                "presentations": {
                    "ws-raw-low": {
                        "workspaceId": "ws-raw-low",
                        "navigatorWidth": 50
                    },
                    "ws-raw-high": {
                        "workspaceId": "ws-raw-high",
                        "navigatorWidth": 999
                    }
                }
            }
        """.trimIndent()
        settings[StudioPresentationStore.KEY_PRESENTATIONS] = rawJson

        val storeFromRaw = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertEquals(StudioPresentation.MIN_NAVIGATOR_WIDTH, storeFromRaw.get("ws-raw-low")?.navigatorWidth)
        assertEquals(StudioPresentation.MAX_NAVIGATOR_WIDTH, storeFromRaw.get("ws-raw-high")?.navigatorWidth)
    }

    @Test
    fun invalidJsonYieldsEmptyStoreRatherThanException() = runTest {
        val settings = MapSettings()
        settings[StudioPresentationStore.KEY_PRESENTATIONS] = "{ this is not valid json at all ::: "

        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        assertTrue(store.currentPresentations.isEmpty())
        assertTrue(store.presentations.first().isEmpty())
        assertNull(store.get("ws-1"))

        // Storage was reset
        assertNull(settings.getStringOrNull(StudioPresentationStore.KEY_PRESENTATIONS))
    }

    @Test
    fun clearRemovesOneWorkspaceAndLeavesTheOther() = runTest {
        val settings = MapSettings()
        val store = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val pres1 = StudioPresentation(workspaceId = "ws-1", hasOpened = true)
        val pres2 = StudioPresentation(workspaceId = "ws-2", hasOpened = true)

        store.put(pres1)
        store.put(pres2)

        assertEquals(2, store.currentPresentations.size)

        store.clear("ws-1")

        assertNull(store.get("ws-1"))
        assertNotNull(store.get("ws-2"))
        assertEquals(1, store.currentPresentations.size)

        // Verify across reload
        val reloaded = StudioPresentationStore(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertNull(reloaded.get("ws-1"))
        assertNotNull(reloaded.get("ws-2"))
    }
}
