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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.BuildSettings

/** Build & Sign: every pane edits a setting the stage uses, or shows what the last build produced. */
@Suppress("LongParameterList")
@Composable
public fun BuildEditor(
    settings: BuildSettings,
    target: TargetDevice,
    facts: BuildFacts,
    onChange: (BuildSettings) -> Unit,
    onRefresh: () -> Unit = {},
    selectedObjectId: String = "compression",
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(Unit) { if (!facts.loaded) onRefresh() }
    Box(modifier = modifier.fillMaxSize().testTag("BuildEditor"), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
        ) {
            when (selectedObjectId.lowercase().replace('_', '-').replace(' ', '-')) {
                "images" -> ImagesPane(target, facts)
                "partition-layout" -> PartitionLayoutPane(target, facts)
                "flashable-members" -> FlashableMembersPane(target, facts)
                "avb-keys" -> AvbKeysPane(settings, facts, onChange)
                "package-signing" -> PackageSigningPane(settings, facts, onChange)
                "output-naming" -> OutputNamingPane(settings, target, facts, onChange)
                else -> CompressionPane(settings, onChange)
            }
        }
    }
}
