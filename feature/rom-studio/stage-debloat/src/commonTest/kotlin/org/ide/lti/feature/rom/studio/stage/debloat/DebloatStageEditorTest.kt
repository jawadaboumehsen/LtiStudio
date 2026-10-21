/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.debloat

import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DebloatStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = DebloatStageEditor()
        assertEquals(StageId.DEBLOAT, editor.stageId)
        assertEquals(CanonicalStageDescriptors.DEBLOAT, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.DEBLOAT.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = DebloatViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        // Debloat is off and nothing is picked until the user picks apps from the real inventory.
        assertTrue(!vm.settings.value.enabled)
        assertTrue(vm.settings.value.removeSelectors.isEmpty())

        val app = DebloatCandidate("system", "system/app/Facebook", 10L, null)
        vm.toggleRemoval(app)
        assertEquals(listOf("system:system/app/Facebook"), vm.settings.value.removeSelectors)
        assertTrue(vm.settings.value.enabled, "picking an app turns Debloat on")
        vm.toggleRemoval(app)
        assertTrue(vm.settings.value.removeSelectors.isEmpty())

        vm.toggleRemoval(DebloatCandidate("system", "system/priv-app/SystemUI", 10L, null))
        assertTrue(vm.settings.value.removeSelectors.isEmpty(), "a protected app is never picked")
    }
}
