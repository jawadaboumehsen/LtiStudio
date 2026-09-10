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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.kashif_e.backdrop.backdrops.LayerBackdrop
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.display.GlassEmptyStateOrientation
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.setup.SetupUiActions
import org.ide.lti.feature.setup.SetupUiState

/**
 * Extensions & Customization tab content.
 *
 * Provides entry points to configure custom IDE themes, syntax plugins, and partition inspectors.
 * Adheres strictly to Design System Guardrails (100% token usage, 0 raw literals).
 */
@Composable
fun PluginsStepContent(
    state: SetupUiState,
    actions: SetupUiActions,
    resolvedBackdrop: LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Column(
        modifier = modifier
            .widthIn(max = ComponentSize.SetupMaxContentWidth)
            .fillMaxWidth()
            .fillMaxHeight()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
    ) {
        // Responsive Header Row with auto-wrap
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = Spacing.ExtraSmall),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Extensions & Customization",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = glassColors.onGlass,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "Personalize your ROM engineering environment, syntax highlighters, and visual themes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            GlassPrimaryButton(
                onClick = actions.onOpenSettings,
            ) {
                Text("Open Settings", maxLines = 1, softWrap = false)
            }
        }

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            backdrop = resolvedBackdrop,
            contentPadding = Spacing.CardPadding,
        ) {
            GlassEmptyState(
                icon = AppIcons.Sparkles,
                title = "Themes & Plugins Registry",
                description = "Configure custom IDE themes (Dark Glass, Blue Glass, High Contrast) and partition inspectors in Settings.",
                badgeSize = ComponentSize.EmptyStateBadgeSize,
                iconSize = IconSize.Large,
                iconTint = glassColors.glassAccent,
                titleStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                orientation = GlassEmptyStateOrientation.Horizontal,
                action = {
                    GlassSecondaryButton(onClick = actions.onOpenSettings) {
                        Text("Configure Themes")
                    }
                },
            )
        }
    }
}
