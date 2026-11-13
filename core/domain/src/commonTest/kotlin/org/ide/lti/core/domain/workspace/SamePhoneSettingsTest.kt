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

import kotlinx.datetime.Clock
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals

class SamePhoneSettingsTest {
    private fun snapshot(id: String) = ConfigurationSnapshot.create(
        id = id,
        workspaceId = id,
        profileRevision = 1,
        acquisition = AcquisitionSettings(
            region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        ),
        createdAt = Clock.System.now(),
    )

    @Test
    fun thePhonesSettingsComeAcrossAndTheOldFirmwaresStayBehind() {
        val old = snapshot("old").let {
            it.copy(
                assembly = it.assembly.copy(buildType = "userdebug", kernelOverrides = mapOf("boot" to "abc")),
                debloat = it.debloat.copy(enabled = true, removeSelectors = listOf("product:app/Facebook")),
                build = it.build.copy(brotliLevel = 9, keepStock = setOf("odm")),
            )
        }
        val new = snapshot("new")
        val carried = SamePhoneSettings.carried(old, new)
        assertEquals("userdebug", carried.assembly.buildType)
        assertEquals(emptyMap(), carried.assembly.kernelOverrides)
        assertEquals(listOf("product:app/Facebook"), carried.debloat.removeSelectors)
        assertEquals(9, carried.build.brotliLevel)
        assertEquals(emptySet(), carried.build.keepStock)
        // The new firmware's own acquisition and identity are untouched.
        assertEquals(new.acquisition, carried.acquisition)
        assertEquals("new", carried.id)
    }
}
