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
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.ActivityItemUi
import org.ide.lti.feature.workspace.studio.WorkspaceActionUi

/**
 * Section displaying recent build executions or an empty state when no runs exist yet.
 * Matches Mockup 09 card presentation with natural typography.
 */
@Composable
public fun RecentActivitySection(
    activities: List<ActivityItemUi>,
    onOpenHistory: () -> Unit,
    onAction: (WorkspaceActionUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("RecentActivitySection")
            .ideCardSurface(),
    ) {
        // Card Header Row matching Mockup 09
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            Icon(
                imageVector = Icons.Default.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Recent activity",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "Latest events for this workspace.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        // Empty or Populated Container
        if (activities.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(GlassDimens.OverviewEmptyStateHeight)
                    .clickable(onClick = onOpenHistory)
                    .padding(Spacing.Medium),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Large),
                    )
                    Text(
                        text = "Build history will appear here.",
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.Medium,
                        fontFamily = GlassFontFamily.ide(),
                    )
                    Text(
                        text = "Run a build to see activity, logs and results.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.BodySmall,
                        fontFamily = GlassFontFamily.ide(),
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                activities.forEachIndexed { index, activity ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(Spacing.Hairline)
                                .background(MaterialTheme.colorScheme.outlineVariant),
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
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = FontSize.BodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = GlassFontFamily.ide(),
                            )
                            Text(
                                text = "${activity.timestamp} · ${activity.description}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = FontSize.BodySmall,
                                fontFamily = GlassFontFamily.ide(),
                            )
                        }

                        if (activity.action != null) {
                            Text(
                                text = "Details",
                                color = MaterialTheme.colorScheme.primary,
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
