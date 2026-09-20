/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.GlassToggleTile
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.SettingsThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme

/**
 * Notifications & Alert Settings Pane.
 */
@Composable
fun NotificationsSettingsPane(
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue
    var pushEnabled by remember { mutableStateOf(true) }
    var soundEnabled by remember { mutableStateOf(false) }
    var autoSaveEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier.fillMaxWidth().testTag("NotificationsSettingsPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
            Text(
                text = "Notifications",
                fontSize = FontSize.HeadlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
            )
            Text(
                text = "System alerts, auditory cues, and background job notifications.",
                fontSize = FontSize.BodyMedium,
                color = if (isBlueGlass) BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.High) else colors.textMuted,
            )
        }

        GlassCard(
            surfaceColor = if (isBlueGlass) SettingsThemeColors.CardBackground else colors.cardBackground,
            borderColor = if (isBlueGlass) SettingsThemeColors.CardBorder else colors.border,
            contentPadding = Spacing.CardPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                Text(
                    text = "System Alerts & Audio",
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
                )

                GlassToggleTile(
                    icon = AppIcons.Notifications,
                    title = "Push Notifications",
                    description = "Receive workspace alerts and background task notifications",
                    checked = pushEnabled,
                    onCheckedChange = { pushEnabled = it },
                )

                GlassHorizontalDivider()

                GlassToggleTile(
                    icon = AppIcons.Sparkles,
                    title = "Sound Effects",
                    description = "Play subtle auditory cues on build completion and diagnostics",
                    checked = soundEnabled,
                    onCheckedChange = { soundEnabled = it },
                )
            }
        }

        GlassCard(
            surfaceColor = if (isBlueGlass) SettingsThemeColors.CardBackground else colors.cardBackground,
            borderColor = if (isBlueGlass) SettingsThemeColors.CardBorder else colors.border,
            contentPadding = Spacing.CardPadding,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.CardPadding)) {
                Text(
                    text = "Persistence & Window Focus",
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isBlueGlass) BrandColors.OnLogoBadge else colors.textMain,
                )

                GlassToggleTile(
                    icon = AppIcons.Save,
                    title = "Auto-Save on Focus Lost",
                    description = "Automatically save modified files when switching windows",
                    checked = autoSaveEnabled,
                    onCheckedChange = { autoSaveEnabled = it },
                )
            }
        }
    }
}
