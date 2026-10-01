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
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.feature.rom.studio.stage.assemble.panes.AotCleanupPane
import org.ide.lti.feature.rom.studio.stage.assemble.panes.BootPreparationPane
import org.ide.lti.feature.rom.studio.stage.assemble.panes.BuildPropertiesPane
import org.ide.lti.feature.rom.studio.stage.assemble.panes.SystemExtHandlingPane
import org.ide.lti.feature.rom.studio.stage.assemble.panes.WorkTreesPane

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun AssembleEditor(
    settings: AssemblySettings,
    target: TargetDevice,
    onChange: (AssemblySettings) -> Unit,
    validationErrors: List<ValidationError> = emptyList(),
    dirtyPaths: Set<String> = emptySet(),
    selectedObjectId: String = "work-trees",
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("AssembleEditor"),
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
                "system-ext-handling", "system_ext_handling", "system_ext", "system-ext" -> SystemExtHandlingPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "aot-cleanup", "aot_cleanup", "aot" -> AotCleanupPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "boot-preparation", "boot_preparation", "boot" -> BootPreparationPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                "build-properties", "build_properties", "properties" -> BuildPropertiesPane(
                    settings = settings,
                    target = target,
                    onChange = onChange,
                    onValidate = onValidate,
                    onSave = onSave,
                )
                else -> WorkTreesPane(
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
