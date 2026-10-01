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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import org.ide.lti.feature.rom.studio.api.StageDescriptor
import org.ide.lti.feature.rom.studio.api.StageEditor
import org.ide.lti.feature.rom.studio.api.StageEditorContext
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

public class PatchStageEditor : StageEditor {
    override val stageId: StageId = StageId.MODULE_APPLICATION
    override val descriptor: StageDescriptor = CanonicalStageDescriptors.PATCH

    @Composable
    override fun Content(context: StageEditorContext, modifier: Modifier) {
        val viewModel: PatchViewModel = koinViewModel { parametersOf(context.workspaceId) }
        val settings by viewModel.settings.collectAsState()

        PatchEditor(
            settings = settings,
            target = viewModel.targetDevice,
            onChange = { viewModel.updateSettings(it) },
            selectedObjectId = context.selectedObjectId?.value ?: "module-catalog",
            onValidate = context.onValidationRequested,
            onSave = context.onSaveRequested,
            modifier = modifier,
        )
    }
}
