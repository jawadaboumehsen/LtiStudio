/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
internal fun HomeScreen(modifier: Modifier = Modifier) {
    HomeScreenContent(
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
internal fun HomeScreenContent(modifier: Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        GlassCard(
            contentPadding = Spacing.ExtraExtraLarge,
            modifier = Modifier.padding(Spacing.ExtraLarge),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                Text(
                    text = "Welcome to LtiRomGui",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                )

                Text(
                    text = "Professional glass morphism theme",
                    style = MaterialTheme.typography.bodyLarge,
                    color = GlassTheme.colors.onGlassSecondary,
                )

                Spacer(Modifier.height(Spacing.Small))

                GlassButton(onClick = { /* Navigate to workspace */ }) {
                    Text("Open Workspace")
                }
            }
        }
    }
}
