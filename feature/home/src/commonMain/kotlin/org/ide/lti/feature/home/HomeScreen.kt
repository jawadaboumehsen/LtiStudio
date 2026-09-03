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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassEmptyState
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenWorkspace: () -> Unit = {},
) {
    HomeScreenContent(
        modifier = modifier,
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
                GlassEmptyState(
                    icon = AppIcons.Sparkles,
                    title = "Welcome to LtiRom",
                    description = "Lightweight, GPU-Accelerated Glass IDE for Desktop",
                    titleStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
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
                            painter = AppIcons.FolderOpenPainterResource(),
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
