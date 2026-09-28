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

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.feature.rom.studio.stage.acquire.panes.DownloadPolicyPane
import org.ide.lti.feature.rom.studio.stage.acquire.panes.ExistingArchivesPane
import org.ide.lti.feature.rom.studio.stage.acquire.panes.FirmwareBaselinePane
import org.ide.lti.feature.rom.studio.stage.acquire.panes.IntegrityPane
import org.ide.lti.feature.rom.studio.stage.acquire.panes.SourceModePane

@Suppress("LongParameterList")
@Composable
public fun AcquireEditor(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit,
    selectedObjectId: String = "integrity",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("AcquireEditor"),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase()) {
                "integrity" -> IntegrityPane(
                    settings = settings,
                    target = target,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "download-policy", "download_policy", "download" -> DownloadPolicyPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "existing-archives", "existing_archives", "archives" -> ExistingArchivesPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "firmware-baseline", "firmware_baseline", "baseline" -> FirmwareBaselinePane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                else -> SourceModePane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
            }
        }
    }
}
