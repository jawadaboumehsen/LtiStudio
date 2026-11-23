/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.GlassGlintState
import org.ide.lti.core.designsystem.component.layout.GlassRole
import org.ide.lti.core.designsystem.component.layout.glassAmbientGlint
import org.ide.lti.core.designsystem.component.layout.glassSurface
import org.ide.lti.core.designsystem.theme.AccentPresetColors
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.device.DeviceMode
import org.ide.lti.core.domain.device.DeviceProfile
import org.ide.lti.core.domain.device.DeviceSlot

/**
 * Modern glass status badge rendering connected device telemetry:
 * Mode (ADB/Fastboot/Fastbootd/Recovery/EDL), Slot, Platform, and Lock state
 * with ambient state-reactive glint.
 */
@Composable
fun DeviceStatusBadge(
    profile: DeviceProfile,
    modifier: Modifier = Modifier,
) {
    val glintState = when {
        !profile.mode.isConnected -> GlassGlintState.Idle
        profile.mode == DeviceMode.ADB_UNAUTHORIZED -> GlassGlintState.Warning("Unauthorized ADB key")
        profile.mode == DeviceMode.FASTBOOTD -> GlassGlintState.ActiveBuild()
        else -> GlassGlintState.DeviceConnected(profile.codename)
    }

    val modeColor = when (profile.mode) {
        DeviceMode.DISCONNECTED -> MaterialTheme.colorScheme.onSurfaceVariant
        DeviceMode.ADB_UNAUTHORIZED -> AccentPresetColors.Amber
        DeviceMode.ADB_ONLINE -> AccentPresetColors.Emerald
        DeviceMode.FASTBOOT -> AccentPresetColors.Cyan
        DeviceMode.FASTBOOTD -> AccentPresetColors.Purple
        DeviceMode.RECOVERY -> AccentPresetColors.Cobalt
        DeviceMode.EDL_9008 -> AccentPresetColors.Crimson
    }

    val modeLabel = when (profile.mode) {
        DeviceMode.DISCONNECTED -> "No Device"
        DeviceMode.ADB_UNAUTHORIZED -> "ADB (Unauthorized)"
        DeviceMode.ADB_ONLINE -> "ADB Online"
        DeviceMode.FASTBOOT -> "Fastboot"
        DeviceMode.FASTBOOTD -> "Fastbootd"
        DeviceMode.RECOVERY -> "Recovery"
        DeviceMode.EDL_9008 -> "EDL 9008"
    }

    Row(
        modifier = modifier
            .testTag("DeviceStatusBadge")
            .glassAmbientGlint(glintState = glintState, shape = GlassShapes.Pill)
            .glassSurface(role = GlassRole.FloatingControl, shape = GlassShapes.HazeCapsule, drawBorder = true)
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        // Mode indicator pill
        Box(
            modifier = Modifier
                .clip(GlassShapes.Pill)
                .background(modeColor.copy(alpha = AlphaTokens.Hover))
                .padding(horizontal = Spacing.Compact, vertical = Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = modeLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Bold,
                    color = modeColor,
                ),
            )
        }

        if (profile.mode.isConnected) {
            // Codename / Serial
            Text(
                text = profile.displayTitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = ideFontFamily(),
                    fontSize = FontSize.LabelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
            )

            // Slot Tag if dual slot
            if (profile.isDualSlot) {
                val slotText = when (profile.activeSlot) {
                    DeviceSlot.A -> "Slot A"
                    DeviceSlot.B -> "Slot B"
                    DeviceSlot.NONE -> ""
                }
                Text(
                    text = slotText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = codeFontFamily(),
                        fontSize = FontSize.Micro,
                        color = MaterialTheme.colorScheme.primary,
                    ),
                    modifier = Modifier
                        .clip(GlassShapes.ExtraSmall)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle))
                        .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.ExtraExtraSmall),
                )
            }

            // Lock indicator
            val lockText = if (profile.isUnlocked) "UNLOCKED" else "LOCKED"
            val lockColor = if (profile.isUnlocked) AccentPresetColors.Amber else AccentPresetColors.Emerald
            Text(
                text = lockText,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = codeFontFamily(),
                    fontSize = FontSize.Micro,
                    color = lockColor,
                ),
            )
        }
    }
}
