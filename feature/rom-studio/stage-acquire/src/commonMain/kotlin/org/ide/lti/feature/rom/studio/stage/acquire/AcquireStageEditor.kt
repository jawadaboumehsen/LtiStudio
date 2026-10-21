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

public class AcquireStageEditor : StageEditor {
    override val stageId: StageId = StageId.FIRMWARE_ACQUISITION
    override val descriptor: StageDescriptor = CanonicalStageDescriptors.ACQUIRE

    @Composable
    override fun Content(context: StageEditorContext, modifier: Modifier) {
        val viewModel: AcquireViewModel = koinViewModel { parametersOf(context.workspaceId) }
        val settings by viewModel.settings.collectAsState()
        // Regions and firmwares come from the workspace's own device (device.json), the template until loaded.
        val device by viewModel.device.collectAsState()
        val archiveState by viewModel.archiveState.collectAsState()
        val saveError by viewModel.saveError.collectAsState()
        val integrity by viewModel.integrity.collectAsState()

        AcquireEditor(
            settings = settings,
            target = device ?: viewModel.targetDevice,
            onChange = { viewModel.updateSettings(it) },
            archiveState = archiveState,
            onProbe = viewModel::probe,
            onImport = viewModel::importArchive,
            onSelectArchive = viewModel::selectArchive,
            saveError = saveError,
            integrity = integrity,
            onRefreshIntegrity = viewModel::refreshIntegrity,
            selectedObjectId = context.selectedObjectId?.value ?: "source",
            onValidate = context.onValidationRequested,
            onSave = context.onSaveRequested,
            modifier = modifier,
        )
    }
}
