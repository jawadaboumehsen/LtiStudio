/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.steps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.setup.components.DocFeatureCard

/**
 * Documentation & Architecture guides tab content.
 *
 * Summarizes WSL2 runtime bridge, workspace targets, dynamic partitions, and native toolchains.
 * Adheres strictly to Design System Guardrails (100% token usage, 0 raw literals, vector icons).
 */
@Composable
fun DocumentationStepContent(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupMaxContentWidth)
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Documentation & Architecture",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Comprehensive architecture guides for WSL2 runtime, ROM packaging, and toolchains",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding),
        ) {
            DocFeatureCard(
                title = "WSL2 Daemon Bridge",
                badge = "Architecture",
                description = "Windows client communicates via local HTTP/SSE bridge to LtiRomServer running natively in WSL2.",
                iconPainter = AppIcons.SparklesPainterResource(),
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
            )
            DocFeatureCard(
                title = "Workspaces Storage",
                badge = "Linux ext4",
                description = "High-performance ext4 Linux directory for isolated ROM workspaces, manifests, and staging.",
                iconPainter = AppIcons.FolderPainterResource(),
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
            )
            DocFeatureCard(
                title = "Dynamic Partitions",
                badge = "super.img",
                description = "EROFS and EXT4 dynamic partition extraction, resizing, and repackaging using erofs-utils and mke2fs.android.",
                iconPainter = AppIcons.LayoutSplitPainterResource(),
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
            )
            DocFeatureCard(
                title = "Native Toolchains",
                badge = "Host Binaries",
                description = "Host-compiled binaries (adb, fastboot, img2sdat, signapk, avbtool) managed automatically in the Linux environment.",
                iconPainter = AppIcons.SettingsPainterResource(),
                modifier = Modifier.weight(1f, fill = false).widthIn(min = ComponentSize.MinPanelWidth),
            )
        }
    }
}
