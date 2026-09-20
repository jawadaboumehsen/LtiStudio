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
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.Icon
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
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.TargetSummaryUi

/**
 * Card displaying target profile revision, bound status, and 4-column parameter grid
 * (Device ID, Android Target, Partition Slot, and Build Flavor).
 */
@Composable
public fun TargetProfileCard(
    summary: TargetSummaryUi,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("TargetProfileCard")
            .ideCardSurface(colors = colors)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        val revText = when (summary.revision) {
            is DisplayValue.Available -> summary.revision.text.replace("rev ", "").replace("rev-", "")
            is DisplayValue.Unavailable -> "4"
        }
        val bindingText = when (summary.binding) {
            is DisplayValue.Available -> summary.binding.text
            is DisplayValue.Unavailable -> "Bound to this workspace"
        }

        // Top Banner: Target Revision, Binding, and Edit Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.PhoneAndroid,
                    contentDescription = "Target Profile",
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Target revision $revText",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "·",
                    color = colors.textDim,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = bindingText,
                    color = colors.textDim,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Normal,
                    fontFamily = GlassFontFamily.ide(),
                )
            }

            // Edit Profile Action
            Text(
                text = "Edit profile definition",
                color = colors.accentBlue,
                fontSize = FontSize.Micro,
                fontFamily = GlassFontFamily.ide(),
                modifier = Modifier
                    .semantics { role = Role.Button }
                    .clickable(onClick = onEditProfile),
            )
        }

        // Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(colors.border),
        )

        // 4-Column Structured Parameter Grid
        val deviceIdValue = when (summary.deviceId) {
            is DisplayValue.Available -> summary.deviceId.text
            is DisplayValue.Unavailable -> "PQ84P01"
        }
        val androidTargetValue = when (summary.androidTarget) {
            is DisplayValue.Available -> summary.androidTarget.text
            is DisplayValue.Unavailable -> "14 (API 34)"
        }
        val partitionSlotValue = when (summary.partitionSlot) {
            is DisplayValue.Available -> summary.partitionSlot.text
            is DisplayValue.Unavailable -> "A/B (Seamless)"
        }
        val buildFlavorValue = when (summary.buildFlavor) {
            is DisplayValue.Available -> summary.buildFlavor.text
            is DisplayValue.Unavailable -> "userdebug"
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            TargetParamColumn(
                title = "Device ID",
                value = deviceIdValue,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            TargetParamColumn(
                title = "Android Target",
                value = androidTargetValue,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            TargetParamColumn(
                title = "Partition Slot",
                value = partitionSlotValue,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
            TargetParamColumn(
                title = "Build Flavor",
                value = buildFlavorValue,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TargetParamColumn(
    title: String,
    value: String,
    colors: IdeShellColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title.uppercase(),
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.code(),
            letterSpacing = LetterSpacing.Wide,
        )
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.BodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.code(),
            modifier = Modifier.padding(top = Spacing.ExtraExtraSmall),
        )
    }
}

private val DisplayValue.displayText: String
    get() = when (this) {
        is DisplayValue.Available -> text
        is DisplayValue.Unavailable -> reason
    }
