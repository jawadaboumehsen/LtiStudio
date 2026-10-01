/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.metadata

import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MetadataStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = MetadataStageEditor()
        assertEquals(StageId.GENERATE_OTA_MANIFEST, editor.stageId)
        assertEquals(CanonicalStageDescriptors.METADATA, editor.descriptor)
        assertEquals(5, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.METADATA.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = MetadataViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        assertEquals("internal", vm.settings.value.channel)
        assertEquals("https://example.com/updates", vm.settings.value.otaBaseUrl)

        vm.updateSettings(vm.settings.value.copy(channel = "stable"))
        assertEquals("stable", vm.settings.value.channel)
    }
}
