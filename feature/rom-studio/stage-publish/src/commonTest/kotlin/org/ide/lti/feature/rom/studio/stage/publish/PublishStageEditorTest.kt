/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PublishStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = PublishStageEditor()
        assertEquals(StageId.PUBLISH_RELEASE, editor.stageId)
        assertEquals(CanonicalStageDescriptors.PUBLISH, editor.descriptor)
        assertEquals(8, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.PUBLISH.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = PublishViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        // No provider is configured until the user chooses one - a workspace's publish
        // settings must never default to a fabricated GitHub repo/credential (see e42c3e3c).
        assertEquals(PublishProvider.NONE, vm.settings.value.provider)
        assertEquals(null, vm.settings.value.credentialRef)

        vm.updateSettings(vm.settings.value.copy(provider = PublishProvider.LOCAL_EXPORT))
        assertEquals(PublishProvider.LOCAL_EXPORT, vm.settings.value.provider)
    }
}
