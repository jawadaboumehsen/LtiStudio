/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatchStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = PatchStageEditor()
        assertEquals(StageId.MODULE_APPLICATION, editor.stageId)
        assertEquals(CanonicalStageDescriptors.PATCH, editor.descriptor)
        assertEquals(6, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.PATCH.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = PatchViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertTrue(vm.settings.value.enabledPackages.isNotEmpty())
        assertEquals(2, vm.settings.value.enabledPackages.size)

        vm.updateSettings(vm.settings.value.copy(enabledPackages = emptyList()))
        assertTrue(vm.settings.value.enabledPackages.isEmpty())
    }
}
