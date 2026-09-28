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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.TargetSummaryUi

/**
 * Card displaying target profile revision, bound status, and key-value parameter rows
 * (Device ID, Channel, Android Target, Partition Slot, and Build Flavor).
 * Matches Mockup 09 (two-column layout right card).
 */
@Composable
public fun TargetProfileCard(summary: TargetSummaryUi, onEditProfile: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .testTag("TargetProfileCard")
            .ideCardSurface()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        TargetProfileHeader(onEditProfile = onEditProfile)
        TargetProfileSubheader(summary = summary)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )

        TargetProfileParams(summary = summary)
    }
}

@Composable
private fun TargetProfileHeader(onEditProfile: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Target",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.ide(),
        )

        val editInteraction = remember { MutableInteractionSource() }
        val isEditHovered by editInteraction.collectIsHoveredAsState()
        val isEditPressed by editInteraction.collectIsPressedAsState()

        val editBg = when {
            isEditPressed -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
            isEditHovered -> MaterialTheme.colorScheme.surfaceContainerHigh
            else -> MaterialTheme.colorScheme.surfaceContainer
        }

        Box(
            modifier = Modifier
                .testTag("EditProfileButton")
                .semantics { role = Role.Button }
                .background(editBg, GlassShapes.ShellControl)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .clickable(
                    interactionSource = editInteraction,
                    indication = null,
                    onClick = onEditProfile,
                )
                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit profile",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Edit",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }
    }
}

@Composable
private fun TargetProfileSubheader(summary: TargetSummaryUi, modifier: Modifier = Modifier) {
    val revText = when (val rev = summary.revision) {
        is DisplayValue.Available -> rev.text.replace("rev ", "").replace("rev-", "")
        is DisplayValue.Unavailable -> "4"
    }
    val bindingText = when (val b = summary.binding) {
        is DisplayValue.Available -> b.text
        is DisplayValue.Unavailable -> "Bound to this workspace"
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Text(
            text = "Target revision $revText",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.ide(),
        )
        Text(
            text = bindingText,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}

@Composable
private fun TargetProfileParams(summary: TargetSummaryUi, modifier: Modifier = Modifier) {
    val deviceIdValue = resolveValue(summary.deviceId, "PQ84P01")
    val androidTargetValue = resolveValue(summary.androidTarget, "Android 14 (UPS1A)")
    val partitionSlotValue = resolveValue(summary.partitionSlot, "A/B (Seamless)")
    val buildFlavorValue = resolveValue(summary.buildFlavor, "Userdebug")

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
    ) {
        TargetParamRow(label = "Device", value = deviceIdValue)
        TargetParamRow(label = "Channel", value = "Development")
        TargetParamRow(label = "Android base", value = androidTargetValue)
        TargetParamRow(label = "Partition slot", value = partitionSlotValue)
        TargetParamRow(label = "Build type", value = buildFlavorValue)
    }
}

private fun resolveValue(displayValue: DisplayValue, fallback: String): String = when (displayValue) {
    is DisplayValue.Available -> displayValue.text
    is DisplayValue.Unavailable -> fallback
}

@Composable
private fun TargetParamRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Medium,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}
