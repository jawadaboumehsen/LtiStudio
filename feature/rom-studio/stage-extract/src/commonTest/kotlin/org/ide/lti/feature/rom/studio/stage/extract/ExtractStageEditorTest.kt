/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.extract

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ExtractStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = ExtractStageEditor()
        assertEquals(StageId.FIRMWARE_EXTRACTION, editor.stageId)
        assertEquals(CanonicalStageDescriptors.EXTRACT, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.EXTRACT.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = ExtractViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals("AUTO", vm.settings.value.adapter)
        vm.updateSettings(vm.settings.value.copy(adapter = "payload-dumper-go"))
        assertEquals("payload-dumper-go", vm.settings.value.adapter)
    }
}
