/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.kashif_e.backdrop.drawBackdrop
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.IdeShellShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.config.AppTheme

@Immutable
data class IdePipelineStageItem(
    val id: String,
    val label: String,
    val icon: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/**
 * 56 dp wide persistent pipeline rail exposing exactly eight ordered ROM workflow stages.
 */
@Composable
fun IdePipelineRail(
    stages: List<IdePipelineStageItem>,
    selectedStageId: String?,
    onSelectStage: (String) -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    val actualBackdrop = GlassTheme.backdrop
    val isBlueGlass = GlassTheme.appTheme == AppTheme.Blue && GlassTheme.effectsEnabled
    val backgroundModifier = if (isBlueGlass && actualBackdrop != null) {
        Modifier.drawBackdrop(
            backdrop = actualBackdrop,
            shape = { RectangleShape },
            effects = GlassEffects.DockedNav,
            highlight = { null },
            onDrawSurface = { drawRect(colors.railBackground) },
        )
    } else {
        Modifier.background(colors.railBackground)
    }

    Column(
        modifier = modifier
            .width(IdeShellDimens.PipelineRailWidth)
            .fillMaxHeight()
            .then(backgroundModifier),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        stages.forEach { stage ->
            PipelineRailItem(
                stage = stage,
                isSelected = stage.id == selectedStageId,
                onSelectStage = onSelectStage,
                colors = colors,
            )
        }
    }
}

@Composable
private fun PipelineRailItem(
    stage: IdePipelineStageItem,
    isSelected: Boolean,
    onSelectStage: (String) -> Unit,
    colors: IdeShellColorScheme,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()

    val bg = when {
        !stage.enabled -> Color.Transparent
        isSelected -> colors.accentBlue
        isPressed -> colors.controlHover
        isHovered -> colors.controlHover.copy(alpha = AlphaTokens.Half)
        else -> Color.Transparent
    }

    val contentColor = when {
        !stage.enabled -> colors.textDim.copy(alpha = AlphaTokens.Disabled)
        isSelected -> colors.textMain
        isHovered -> colors.textMain
        else -> colors.textMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IdeShellDimens.PipelineStageItemHeight)
            .padding(horizontal = Spacing.ExtraSmall, vertical = Spacing.Hairline)
            .clip(IdeShellShapes.Control)
            .background(bg)
            .testTag("RailItem_${stage.id}")
            .semantics {
                role = Role.Tab
                selected = isSelected
                contentDescription = stage.label + (stage.disabledReason?.let { " ($it)" } ?: "")
                if (!stage.enabled) {
                    disabled()
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = stage.enabled,
                onClick = { onSelectStage(stage.id) },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            val vector = resolveRailIcon(stage.id)
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.SidePanelRail),
            )
            Text(
                text = stage.label,
                color = contentColor,
                fontSize = FontSize.BodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
                maxLines = 1,
            )
        }
    }
}

private fun resolveRailIcon(stageId: String): ImageVector = when (stageId.lowercase()) {
    "acquire", "firmware_acquisition" -> Icons.Default.Download
    "extract", "firmware_extraction" -> Icons.Default.Description
    "assemble", "work_tree_assembly" -> Icons.Default.ViewInAr
    "debloat" -> Icons.Default.Delete
    "patches", "module_application" -> Icons.Default.Healing
    "build", "build_flashable_zip" -> Icons.Default.Construction
    "metadata", "generate_ota_manifest" -> Icons.AutoMirrored.Filled.ListAlt
    "publish", "publish_release" -> Icons.Default.Upload
    else -> Icons.Default.Folder
}
