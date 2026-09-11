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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetPreferencesDataSourceTest {

    private fun createSampleTarget(id: String = "TARGET_1", name: String = "Test Device"): TargetDevice {
        return TargetDevice(
            id = id,
            name = name,
            codename = id.lowercase(),
            socPlatform = "Snapdragon 8 Elite",
            filesystemType = "erofs",
            dynamicPartitions = listOf("system", "vendor"),
            bootPartitions = listOf("boot", "vendor_boot"),
            status = TargetStatus.QUALIFIED,
            description = "Test device profile",
        )
    }

    @Test
    fun testDefaultStateIsEmpty() = runTest {
        val settings = MapSettings()
        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val targets = dataSource.targets.first()
        val selectedId = dataSource.selectedTargetId.first()

        assertTrue(targets.isEmpty())
        assertNull(selectedId)
        assertTrue(dataSource.currentTargets.isEmpty())
        assertNull(dataSource.currentSelectedTargetId)
    }

    @Test
    fun testSaveAndRetrieveTargets() = runTest {
        val settings = MapSettings()
        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val target1 = createSampleTarget("T1", "Device 1")
        val target2 = createSampleTarget("T2", "Device 2")

        dataSource.saveTargets(listOf(target1, target2))
        assertEquals(2, dataSource.currentTargets.size)
        assertEquals("T1", dataSource.currentTargets[0].id)
        assertEquals("T2", dataSource.currentTargets[1].id)

        // Verify round-trip across new instance reading same settings
        val reloaded = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertEquals(2, reloaded.currentTargets.size)
        assertEquals("T1", reloaded.currentTargets[0].id)
        assertEquals("T2", reloaded.currentTargets[1].id)
    }

    @Test
    fun testSaveAndRetrieveSelectedTargetId() = runTest {
        val settings = MapSettings()
        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        dataSource.saveSelectedTargetId("T1")
        assertEquals("T1", dataSource.currentSelectedTargetId)
        assertEquals("T1", dataSource.selectedTargetId.first())

        // Verify round-trip across new instance reading same settings
        val reloaded = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertEquals("T1", reloaded.currentSelectedTargetId)
        assertEquals("T1", reloaded.selectedTargetId.first())
    }

    @Test
    fun testCorruptedJsonRecoversGracefully() = runTest {
        val settings = MapSettings()
        settings.putString(TargetPreferencesDataSource.KEY_TARGETS, "{ corrupt_targets: true ...")
        settings.putString(TargetPreferencesDataSource.KEY_SELECTED_TARGET_ID, "{ corrupt_id: true ...")

        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        assertTrue(dataSource.currentTargets.isEmpty())
        assertNull(dataSource.currentSelectedTargetId)
    }

    @Test
    fun testClearTargetsAndSelectedTargetId() = runTest {
        val settings = MapSettings()
        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val target = createSampleTarget("T1", "Device 1")
        dataSource.saveTargets(listOf(target))
        dataSource.saveSelectedTargetId("T1")

        assertEquals(1, dataSource.currentTargets.size)
        assertEquals("T1", dataSource.currentSelectedTargetId)

        dataSource.clear()

        assertTrue(dataSource.currentTargets.isEmpty())
        assertNull(dataSource.currentSelectedTargetId)
    }

    @Test
    fun testBareArrayFallbackLoadsTargetsWithDefaultRevision() = runTest {
        val settings = MapSettings()
        val bareArrayJson = """
            [
                {
                    "id": "LEGACY_T1",
                    "name": "Legacy Target",
                    "codename": "legacy_t1",
                    "socPlatform": "Snapdragon 8 Elite",
                    "filesystemType": "erofs",
                    "dynamicPartitions": ["system", "vendor"],
                    "bootPartitions": ["boot"],
                    "status": "QUALIFIED",
                    "description": "Legacy target profile"
                }
            ]
        """.trimIndent()
        settings.putString(TargetPreferencesDataSource.KEY_TARGETS, bareArrayJson)

        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val targets = dataSource.targets.first()

        assertEquals(1, targets.size)
        assertEquals("LEGACY_T1", targets.first().id)
        assertEquals(1, targets.first().revision)
    }

    @Test
    fun testTargetListWrapperSchemaVersion2RoundTrips() = runTest {
        val settings = MapSettings()
        val dataSource = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val target = createSampleTarget("T_MODERN", "Modern Target").copy(revision = 3)
        dataSource.saveTargets(listOf(target))

        val raw = settings.getStringOrNull(TargetPreferencesDataSource.KEY_TARGETS)
        assertNotNull(raw)
        assertTrue(raw.contains(""""schemaVersion":2""") || raw.contains(""""schemaVersion": 2"""))
        assertTrue(raw.contains(""""items""""))

        val reloaded = TargetPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val reloadedTargets = reloaded.targets.first()
        assertEquals(1, reloadedTargets.size)
        assertEquals("T_MODERN", reloadedTargets.first().id)
        assertEquals(3, reloadedTargets.first().revision)
    }
}
