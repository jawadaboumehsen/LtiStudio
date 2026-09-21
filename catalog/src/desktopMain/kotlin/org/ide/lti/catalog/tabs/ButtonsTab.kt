/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.catalog.tabs

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassIconToggleButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassIcon
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Composable
fun ButtonsTab() {
    val scrollState = rememberScrollState()
    var clickCount by remember { mutableIntStateOf(0) }
    var isMuted by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(true) }
    var isFavorite by remember { mutableStateOf(false) }

    val backdrop = GlassTheme.scene

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraLarge)
    ) {
        // Section 1: Button Variants
        SectionContainer(
            title = "1. Glass Button Variants (GlassButtonVariant)",
            description = "Interactive buttons with GPU backdrop sampling and fluid press response."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    onClick = { clickCount++ },
                    variant = GlassButtonVariant.Primary,
                    backdrop = backdrop
                ) {
                    Text("Primary Variant")
                }

                GlassButton(
                    onClick = { clickCount++ },
                    variant = GlassButtonVariant.Secondary,
                    backdrop = backdrop
                ) {
                    Text("Secondary Variant")
                }

                GlassButton(
                    onClick = { clickCount++ },
                    variant = GlassButtonVariant.Standard,
                    backdrop = backdrop
                ) {
                    Text("Standard Variant")
                }

                GlassButton(
                    onClick = { clickCount++ },
                    variant = GlassButtonVariant.Text,
                    backdrop = backdrop
                ) {
                    Text("Text Variant")
                }
            }

            Spacer(Modifier.height(Spacing.SmallMedium))
            Text(
                text = "Total clicks registered: $clickCount",
                style = MaterialTheme.typography.labelMedium,
                color = GlassTheme.colors.glassAccent
            )
        }

        // Section 2: Convenience Button Helpers
        SectionContainer(
            title = "2. Convenience Composables (GlassPrimaryButton & GlassTextButton)",
            description = "Semantic button wrappers for rapid IDE toolbar and action construction."
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPrimaryButton(onClick = { clickCount++ }) {
                    Icon(painter = AppIcons.CheckPainterResource(), contentDescription = null, modifier = Modifier.size(IconSize.Small))
                    Spacer(Modifier.size(Spacing.ExtraSmall))
                    Text("Save Changes")
                }

                GlassTextButton(onClick = { clickCount = 0 }) {
                    Text("Reset Counter")
                }
            }
        }

        // Section 3: Disabled State Handling
        SectionContainer(
            title = "3. Disabled Button State",
            description = "Disabled buttons suppress interaction and render with muted alpha overlay."
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassButton(
                    onClick = {},
                    enabled = false,
                    variant = GlassButtonVariant.Primary
                ) {
                    Text("Disabled Primary")
                }

                GlassButton(
                    onClick = {},
                    enabled = false,
                    variant = GlassButtonVariant.Standard
                ) {
                    Text("Disabled Standard")
                }

                GlassTextButton(
                    onClick = {},
                    enabled = false
                ) {
                    Text("Disabled Text")
                }
            }
        }

        // Section 4: Glass Icon Buttons & Toggle Buttons
        SectionContainer(
            title = "4. Toolbar Icon Buttons & Toggle Buttons",
            description = "Compact 40dp circular controls for IDE toolbars and inspectors."
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(
                    onClick = { isPinned = !isPinned },
                    selected = isPinned
                ) {
                    Icon(
                        painter = AppIcons.LockedPainterResource(),
                        contentDescription = "Pin Panel",
                        modifier = Modifier.size(IconSize.Medium)
                    )
                }

                GlassIconToggleButton(
                    selected = isMuted,
                    onSelectedChange = { isMuted = it }
                ) {
                    Icon(
                        painter = AppIcons.NotificationsPainterResource(),
                        contentDescription = "Toggle Mute",
                        modifier = Modifier.size(IconSize.Medium)
                    )
                }

                GlassIconToggleButton(
                    selected = isFavorite,
                    onSelectedChange = { isFavorite = it }
                ) {
                    Icon(
                        painter = AppIcons.InfoPainterResource(),
                        contentDescription = "Info",
                        modifier = Modifier.size(IconSize.Medium)
                    )
                }
            }
        }

        // Section 5: Standalone GlassIcon
        SectionContainer(
            title = "5. Standalone GlassIcon Container",
            description = "Glass surface icon container with optional custom tint and surface color."
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIcon(
                    onClick = {},
                    backdrop = backdrop,
                    modifier = Modifier.size(Spacing.ExtraExtraLarge)
                ) {
                    Icon(painter = AppIcons.HomePainterResource(), contentDescription = "Home", modifier = Modifier.size(IconSize.Medium))
                }

                GlassIcon(
                    onClick = {},
                    backdrop = backdrop,
                    tint = GlassTheme.colors.glassAccent,
                    modifier = Modifier.size(Spacing.ExtraExtraLarge)
                ) {
                    Icon(painter = AppIcons.FolderPainterResource(), contentDescription = "Folder", modifier = Modifier.size(IconSize.Medium))
                }
            }
        }
    }
}

@Composable
fun SectionContainer(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.Card)
            .background(GlassTheme.colors.glassSurfaceElevated)
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = GlassTheme.colors.onGlass)
        Text(description, style = MaterialTheme.typography.bodySmall, color = GlassTheme.colors.onGlassSecondary)
        Spacer(Modifier.height(Spacing.Small))
        CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
            content()
        }
    }
}
