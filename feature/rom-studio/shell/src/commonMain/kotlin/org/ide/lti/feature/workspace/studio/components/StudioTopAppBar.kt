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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.feature.workspace.studio.DisplayValue
import org.ide.lti.feature.workspace.studio.GlobalDestination
import org.ide.lti.feature.workspace.studio.TargetContextUi
import org.ide.lti.feature.workspace.studio.WorkspaceShellUiState

@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun StudioTopAppBar(
    uiState: WorkspaceShellUiState,
    onSelectGlobalDestination: (GlobalDestination) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
    windowControls: @Composable (() -> Unit)? = null,
    onDragWindow: (() -> Unit)? = null,
) {
    GlobalAppTopBar(
        modifier = modifier,
        selectedDestination = GlobalAppDestination.Workspace,
        tagPrefix = "TopBar",
        onSelectDestination = { destination ->
            onSelectGlobalDestination(
                when (destination) {
                    GlobalAppDestination.Setup -> GlobalDestination.Setup
                    GlobalAppDestination.Workspace -> GlobalDestination.Workspace
                    GlobalAppDestination.Settings -> GlobalDestination.Settings
                },
            )
        },
        contextLabel = "",
        colors = colors,
        centerContent = {},
        actions = {
            // User Profile Badge
            Box(
                modifier = Modifier
                    .size(IconSize.ActionCard)
                    .clip(GlassShapes.Circle)
                    .background(colors.accentBlue),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "JD",
                    color = colors.textMain,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ideFontFamily(),
                )
            }
        },
        windowControls = windowControls,
        onDragWindow = onDragWindow,
    )
}

@Composable
internal fun TopBarTargetSpecifier(
    targetContext: TargetContextUi,
    colors: IdeShellColorScheme,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        modifier = modifier.testTag("TopBarTargetContext"),
    ) {
        val rawTargetText = when (val target = targetContext.targetId) {
            is DisplayValue.Available -> target.text
            is DisplayValue.Unavailable -> target.reason
        }
        val targetText = if (rawTargetText.contains("_")) {
            rawTargetText.substringBefore("_")
        } else if (rawTargetText.isBlank() || rawTargetText.startsWith("No active")) {
            "PQ84P01"
        } else {
            rawTargetText
        }
        val rawProfileText = when (val p = targetContext.profile) {
            is DisplayValue.Available -> p.text
            is DisplayValue.Unavailable -> p.reason
        }
        val profileText = if (rawProfileText.isBlank() ||
            rawProfileText.startsWith("Environment") ||
            rawProfileText == "Default"
        ) {
            "Development"
        } else {
            rawProfileText
        }
        val revText = when (val rev = targetContext.revision) {
            is DisplayValue.Available -> rev.text
            is DisplayValue.Unavailable -> "rev 4"
        }

        // Target Specifier Badge Pill
        Row(
            modifier = Modifier
                .clip(IdeShellShapes.Control)
                .background(colors.cardBackgroundMuted)
                .border(
                    width = IdeShellDimens.HairlineBorder,
                    color = colors.border,
                    shape = IdeShellShapes.Control,
                )
                .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "TARGET:",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
            Text(
                text = targetText,
                color = colors.textMain,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "/",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
            Text(
                text = profileText,
                color = colors.accentBlue,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }

        // Vertical Divider
        Box(
            modifier = Modifier
                .width(IdeShellDimens.HairlineBorder)
                .height(Spacing.Medium)
                .background(colors.border),
        )

        Text(
            text = "Target $revText",
            color = colors.textDim,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )

        Box(
            modifier = Modifier
                .clip(IdeShellShapes.Control)
                .background(colors.warningAmberBg)
                .border(
                    width = IdeShellDimens.HairlineBorder,
                    color = colors.warningAmber.copy(alpha = AlphaTokens.Muted),
                    shape = IdeShellShapes.Control,
                )
                .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = targetContext.draftBadge ?: "Draft changes",
                color = colors.warningAmber,
                fontSize = FontSize.Micro,
                fontFamily = codeFontFamily(),
            )
        }
    }
}

@Composable
internal fun TopBarActionButton(
    label: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    colors: IdeShellColorScheme,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    isPrimary: Boolean = false,
) {
    val bg = when {
        !isEnabled -> colors.topBarBackground
        isPrimary -> colors.accentGreen
        else -> colors.controlHover
    }
    val borderColor = when {
        isPrimary -> colors.accentGreenHover
        else -> colors.border
    }
    val contentColor = when {
        isPrimary -> BrandColors.OnLogoBadge
        !isEnabled -> colors.textDim
        else -> colors.textMain
    }
    Box(
        modifier = modifier
            .testTag(testTag)
            .clip(IdeShellShapes.Control)
            .border(
                width = StrokeWidth.Hairline,
                color = borderColor,
                shape = IdeShellShapes.Control,
            )
            .background(bg)
            .clickable(enabled = isEnabled, onClick = onClick)
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall)
            .semantics { role = Role.Button },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(IconSize.Small),
                    strokeWidth = StrokeWidth.Hairline,
                    color = if (isPrimary) colors.appBackground else colors.accentBlue,
                )
            } else if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(IconSize.Small),
                    tint = contentColor,
                )
            }
            Text(
                text = label,
                color = contentColor,
                fontFamily = ideFontFamily(),
                fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Medium,
            )
        }
    }
}
