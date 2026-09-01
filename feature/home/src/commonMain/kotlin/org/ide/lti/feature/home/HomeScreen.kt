/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenWorkspace: () -> Unit = {},
) {
    HomeScreenContent(
        modifier = modifier.fillMaxSize(),
        onOpenWorkspace = onOpenWorkspace,
    )
}

@Composable
fun HomeScreenContent(
    modifier: Modifier = Modifier,
    onOpenWorkspace: () -> Unit = {},
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            contentPadding = Spacing.ExtraExtraLarge,
            modifier = Modifier
                .widthIn(max = ComponentSize.ModalCardMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = Spacing.ScreenHorizontal, vertical = Spacing.ScreenVertical),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.SectionGap),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier
                        .size(ComponentSize.EmptyStateBadgeSize)
                        .clip(CircleShape)
                        .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                        .border(
                            StrokeWidth.Hairline,
                            GlassTheme.colors.glassBorderHighlight.copy(alpha = AlphaTokens.Border),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = AppIcons.Sparkles,
                        contentDescription = null,
                        tint = GlassTheme.colors.glassAccent,
                        modifier = Modifier.size(IconSize.ExtraLarge),
                    )
                }

                Text(
                    text = "Welcome to LtiRom",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                    textAlign = TextAlign.Center,
                )

                Text(
                    text = "Lightweight, GPU-Accelerated Glass IDE for Desktop",
                    style = MaterialTheme.typography.bodyMedium,
                    color = GlassTheme.colors.onGlassSecondary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(Spacing.Small))

                GlassPrimaryButton(
                    onClick = onOpenWorkspace,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = AppIcons.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        Text("Open Workspace")
                    }
                }
            }
        }
    }
}
