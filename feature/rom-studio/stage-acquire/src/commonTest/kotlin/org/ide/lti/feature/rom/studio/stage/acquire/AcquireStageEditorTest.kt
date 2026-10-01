/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AcquireStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = AcquireStageEditor()
        assertEquals(StageId.FIRMWARE_ACQUISITION, editor.stageId)
        assertEquals(CanonicalStageDescriptors.ACQUIRE, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.ACQUIRE.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultSettingsAndUpdates() {
        val vm = AcquireViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals(3, vm.settings.value.retries)
        vm.updateSettings(vm.settings.value.copy(retries = 5))
        assertEquals(5, vm.settings.value.retries)
    }
}
