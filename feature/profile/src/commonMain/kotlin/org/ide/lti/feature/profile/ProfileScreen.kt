/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.profile

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassButton
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
internal fun ProfileScreen(modifier: Modifier = Modifier) {
    ProfileScreenContent(
        modifier = modifier.fillMaxSize(),
    )
}

@Composable
internal fun ProfileScreenContent(modifier: Modifier) {
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
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Profile icon
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Profile",
                    modifier = Modifier.size(IconSize.Avatar),
                    tint = GlassTheme.colors.glassAccent,
                )

                Text(
                    text = "User Profile",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                )

                Spacer(Modifier.height(Spacing.Small))

                // Profile info
                ProfileInfo(
                    icon = Icons.Default.Person,
                    label = "Username",
                    value = "developer",
                )

                ProfileInfo(
                    icon = Icons.Default.Email,
                    label = "Email",
                    value = "developer@lti.com",
                )

                Spacer(Modifier.height(Spacing.Medium))

                GlassButton(
                    onClick = { /* Edit profile */ },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Edit Profile")
                }
            }
        }
    }
}

@Composable
private fun ProfileInfo(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = GlassTheme.colors.glassAccent,
        )

        Column {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.colors.onGlassSecondary,
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = GlassTheme.colors.onGlass,
            )
        }
    }
}
