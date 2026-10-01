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
        assertEquals(6, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.DEBLOAT.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = DebloatViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        // Debloat is off and no preset is chosen until the user configures it - a workspace's
        // debloat settings must never default to fabricated remove/keep selectors (see e42c3e3c).
        assertTrue(!vm.settings.value.enabled)
        assertEquals(null, vm.settings.value.presetId)
        vm.updateSettings(vm.settings.value.copy(presetId = "aggressive"))
        assertEquals("aggressive", vm.settings.value.presetId)
    }
}
