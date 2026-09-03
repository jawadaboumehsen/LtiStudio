/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.profile

import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.GlassCard
import org.ide.lti.core.designsystem.component.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.GlassValueTile
import org.ide.lti.core.designsystem.component.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit = {},
) {
    ProfileScreenContent(
        modifier = modifier.fillMaxSize(),
        onEditProfile = onEditProfile,
    )
}

@Composable
fun ProfileScreenContent(
    modifier: Modifier = Modifier,
    onEditProfile: () -> Unit = {},
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
                // Profile Avatar Container
                Box(
                    modifier = Modifier
                        .size(IconSize.Avatar)
                        .clip(GlassShapes.Capsule)
                        .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                        .glassOutlineBorder(
                            color = GlassTheme.colors.glassBorderHighlight.copy(alpha = AlphaTokens.Border),
                            width = StrokeWidth.Hairline,
                            shape = GlassShapes.Capsule,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = AppIcons.UserPainterResource(),
                        contentDescription = "Profile Avatar",
                        modifier = Modifier.size(IconSize.ActionCard),
                        tint = GlassTheme.colors.glassAccent,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "User Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = GlassTheme.colors.onGlass,
                    )
                    Text(
                        text = "developer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = GlassTheme.colors.glassAccent,
                    )
                }

                // Profile Info Grouped Box
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Medium)
                        .background(GlassTheme.colors.glassSurfaceElevated.copy(alpha = AlphaTokens.Faint))
                        .glassOutlineBorder(
                            color = GlassTheme.colors.glassBorder,
                            width = StrokeWidth.Hairline,
                            shape = GlassShapes.Medium,
                        )
                        .padding(Spacing.CardPadding),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    GlassValueTile(
                        icon = AppIcons.User,
                        title = "Username",
                        value = "developer",
                    )

                    GlassHorizontalDivider()

                    GlassValueTile(
                        icon = AppIcons.Contact,
                        title = "Email",
                        value = "developer@lti.com",
                    )

                    GlassHorizontalDivider()

                    GlassValueTile(
                        icon = AppIcons.Locked,
                        title = "Role",
                        value = "Administrator",
                    )
                }

                Spacer(Modifier.height(Spacing.Small))

                GlassPrimaryButton(
                    onClick = onEditProfile,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = AppIcons.EditPainterResource(),
                            contentDescription = null,
                            modifier = Modifier.size(IconSize.Medium),
                        )
                        Text("Edit Profile")
                    }
                }
            }
        }
    }
}

