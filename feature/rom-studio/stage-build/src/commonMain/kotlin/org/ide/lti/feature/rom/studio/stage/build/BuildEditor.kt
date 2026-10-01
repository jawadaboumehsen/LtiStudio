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
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.feature.rom.studio.stage.build.panes.AvbKeysPane
import org.ide.lti.feature.rom.studio.stage.build.panes.CompressionPane
import org.ide.lti.feature.rom.studio.stage.build.panes.FlashableMembersPane
import org.ide.lti.feature.rom.studio.stage.build.panes.ImagesPane
import org.ide.lti.feature.rom.studio.stage.build.panes.OutputNamingPane
import org.ide.lti.feature.rom.studio.stage.build.panes.PackageSigningPane
import org.ide.lti.feature.rom.studio.stage.build.panes.PartitionLayoutPane

/**
 * Main editor for the Build & Package stage in ROM Studio.
 * Routes selected subobject IDs to pixel-perfect dedicated panes.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun BuildEditor(
    settings: BuildSettings,
    target: TargetDevice,
    onChange: (BuildSettings) -> Unit,
    validationErrors: List<ValidationError> = emptyList(),
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "compression",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("BuildEditor"),
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
                "images" -> ImagesPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "partition-layout", "partition_layout", "partition layout" -> PartitionLayoutPane(
                    settings = settings,
                    target = target,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "flashable-members", "flashable_members", "flashable members" -> FlashableMembersPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "avb-keys", "avb_keys", "avb keys" -> AvbKeysPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "package-signing", "package_signing", "package signing" -> PackageSigningPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "output-naming", "output_naming", "output naming" -> OutputNamingPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                else -> CompressionPane(
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
