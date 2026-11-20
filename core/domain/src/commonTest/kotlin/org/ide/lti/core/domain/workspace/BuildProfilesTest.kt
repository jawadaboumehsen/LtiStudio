/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildProfilesTest {
    private var snapshot = ConfigurationSnapshot.create(
        id = "s1",
        workspaceId = "ws",
        profileRevision = 1,
        acquisition = AcquisitionSettings(
            region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
        createdAt = Clock.System.now(),
    )
    private var saved = emptyList<BuildProfile>()
    private val profiles = BuildProfiles(
        store = object : BuildProfileStore {
            override suspend fun load(workspaceId: String) = saved
            override suspend fun update(workspaceId: String, change: (List<BuildProfile>) -> List<BuildProfile>) =
                change(saved).also { saved = it }
        },
        settings = object : WorkspaceSettingsStore {
            override suspend fun current(workspaceId: String) = snapshot
            override suspend fun update(workspaceId: String, change: (ConfigurationSnapshot) -> ConfigurationSnapshot) =
                Result.success(change(snapshot).also { snapshot = it })
            override suspend fun device(workspaceId: String) = null
        },
    )

    @Test
    fun aProfileKeepsEveryStagesSettingsAndUsingItBringsThemBack() = runTest {
        snapshot = snapshot.copy(build = snapshot.build.copy(brotliLevel = 9))
        profiles.saveCurrent("ws", " fast ")
        snapshot = snapshot.copy(
            build = snapshot.build.copy(brotliLevel = 2),
            debloat = snapshot.debloat.copy(removeSelectors = listOf("product:app/Facebook")),
        )
        // Saving under the same name replaces it.
        profiles.saveCurrent("ws", "small")
        profiles.saveCurrent("ws", "small")
        assertEquals(listOf("fast", "small"), profiles.list("ws").map { it.name })

        val fast = profiles.list("ws").first()
        assertTrue(profiles.differences("ws", fast).isNotEmpty())
        profiles.apply("ws", fast)
        assertEquals(9, snapshot.build.brotliLevel)
        assertEquals(emptyList(), snapshot.debloat.removeSelectors)
        assertEquals("s1", snapshot.id)
        assertEquals(emptyList(), profiles.differences("ws", fast))

        profiles.delete("ws", "fast")
        assertEquals(listOf("small"), profiles.list("ws").map { it.name })
    }
}
