/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.assemble

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AssembleStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = AssembleStageEditor()
        assertEquals(StageId.WORK_TREE_ASSEMBLY, editor.stageId)
        assertEquals(CanonicalStageDescriptors.ASSEMBLE, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.ASSEMBLE.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = AssembleViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals("user", vm.settings.value.buildType)
        vm.updateSettings(vm.settings.value.copy(buildType = "userdebug"))
        assertEquals("userdebug", vm.settings.value.buildType)
    }
}
