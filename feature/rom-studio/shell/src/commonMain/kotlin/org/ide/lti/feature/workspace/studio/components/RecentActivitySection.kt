/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.ActivityItemUi
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * Section displaying recent build executions or a dashed empty state when no runs exist yet.
 */
@Composable
public fun RecentActivitySection(
    activities: List<ActivityItemUi>,
    onOpenHistory: () -> Unit,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RecentActivitySection"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Section Header Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "RECENT ACTIVITY",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.code(),
                letterSpacing = LetterSpacing.Wide,
            )
        }

        // Empty or Populated Container
        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IdeShellDimens.OverviewEmptyStateHeight)
                    .ideCardSurface(colors = colors, isMuted = true)
                    .clickable(onClick = onOpenHistory)
                    .padding(Spacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = colors.textDim,
                        modifier = Modifier.size(IconSize.Large),
                    )
                    Text(
                        text = "Build history will appear here. " +
                            "Start your first build after configuring source firmware.",
                        color = colors.textDim,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.ide(),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .ideCardSurface(colors = colors),
            ) {
                activities.forEachIndexed { index, activity ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Spacing.Hairline)
                                .background(colors.borderMuted),
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(Spacing.Medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(Spacing.Hairline)) {
                            Text(
                                text = activity.title,
                                color = colors.textMain,
                                fontSize = FontSize.BodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                            Text(
                                text = "${activity.timestamp} · ${activity.description}",
                                color = colors.textDim,
                                fontSize = FontSize.Micro,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }

                        if (activity.action != null) {
                            Text(
                                text = "Details",
                                color = colors.accentBlue,
                                fontSize = FontSize.BodySmall,
                                fontWeight = FontWeight.Medium,
                                fontFamily = GlassFontFamily.ide(),
                                modifier = Modifier
                                    .semantics { role = Role.Button }
                                    .clickable { onAction(activity.action) },
                            )
                        }
                    }
                }
            }
        }
    }
}
