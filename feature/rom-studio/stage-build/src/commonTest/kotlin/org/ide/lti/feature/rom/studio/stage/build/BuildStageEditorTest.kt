/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BuildStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = BuildStageEditor()
        assertEquals(StageId.BUILD_FLASHABLE_ZIP, editor.stageId)
        assertEquals(CanonicalStageDescriptors.BUILD, editor.descriptor)
        assertEquals(7, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.BUILD.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = BuildViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals("erofs", vm.settings.value.filesystem)
        assertEquals("lz4hc,9", vm.settings.value.compression)

        vm.updateSettings(vm.settings.value.copy(compression = "lz4hc,12"))
        assertEquals("lz4hc,12", vm.settings.value.compression)
    }
}
