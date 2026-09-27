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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import dev.chrisbanes.haze.ExperimentalHazeApi
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdePipelineStageItem(
    val id: String,
    val label: String,
    val icon: String,
    val enabled: Boolean = true,
    val disabledReason: String? = null,
)

/**
 * 150 dp wide persistent pipeline rail exposing exactly eight ordered ROM workflow stages.
 */
@OptIn(ExperimentalHazeApi::class)
@Composable
fun IdePipelineRail(
    stages: List<IdePipelineStageItem>,
    selectedStageId: String?,
    onSelectStage: (String) -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .width(GlassDimens.PipelineRailWidth)
            .fillMaxHeight()
            .ideShellSurface(shape = GlassShapes.HazeFlat, drawBorder = false),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
    ) {
        if (header != null) {
            header()
        }
        stages.forEach { stage ->
            PipelineRailItem(
                stage = stage,
                isSelected = stage.id == selectedStageId,
                onSelectStage = onSelectStage,
            )
        }
    }
}

@Composable
private fun PipelineRailItem(
    stage: IdePipelineStageItem,
    isSelected: Boolean,
    onSelectStage: (String) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val visuals = resolveRailItemVisuals(stage, isSelected, isPressed, isHovered)

    val borderModifier = if (visuals.borderColor != null) {
        Modifier.border(
            width = GlassDimens.HairlineBorder,
            color = visuals.borderColor,
            shape = GlassShapes.ShellPill,
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(GlassDimens.PipelineStageItemHeight)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline)
            .clip(GlassShapes.ShellPill)
            .then(borderModifier)
            .background(visuals.bg)
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
        ) {
            RenderRailIcon(stageId = stage.id, contentColor = visuals.contentColor)
            Text(
                text = stage.label,
                color = visuals.contentColor,
                fontSize = FontSize.BodySmall,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
                maxLines = 1,
            )
        }
    }
}

private data class RailItemVisuals(val bg: Color, val contentColor: Color, val borderColor: Color?)

@Composable
private fun resolveRailItemVisuals(
    stage: IdePipelineStageItem,
    isSelected: Boolean,
    isPressed: Boolean,
    isHovered: Boolean,
): RailItemVisuals {
    val bg = when {
        !stage.enabled -> Color.Transparent
        isSelected -> MaterialTheme.colorScheme.primary
        isPressed -> MaterialTheme.colorScheme.surfaceContainerHigh
        isHovered -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = AlphaTokens.Half)
        else -> Color.Transparent
    }
    val contentColor = when {
        !stage.enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = AlphaTokens.Disabled)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isHovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        null
    }
    return RailItemVisuals(bg, contentColor, borderColor)
}

@Composable
private fun RenderRailIcon(stageId: String, contentColor: Color) {
    val painter = resolveRailPainter(stageId)
    if (painter != null) {
        Icon(
            painter = painter,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(IconSize.SidePanelRail),
        )
    } else {
        val vector = resolveRailIcon(stageId)
        Icon(
            imageVector = vector,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(IconSize.SidePanelRail),
        )
    }
}

@Composable
private fun resolveRailPainter(stageId: String): Painter? = when (stageId.lowercase()) {
    "acquire", "firmware_acquisition" -> AppIconsRomStages.acquire()
    "extract", "firmware_extraction" -> AppIconsRomStages.extract()
    "assemble", "work_tree_assembly" -> AppIconsRomStages.assemble()
    "debloat" -> AppIconsRomStages.debloat()
    "patches", "module_application" -> AppIconsRomStages.patches()
    "build", "build_flashable_zip" -> AppIconsRomStages.build()
    "metadata", "generate_ota_manifest" -> AppIconsRomStages.releaseMetadata()
    "publish", "publish_release" -> AppIconsRomStages.publish()
    else -> null
}

private fun resolveRailIcon(stageId: String): ImageVector = when (stageId.lowercase()) {
    "machine_setup" -> Icons.Default.Construction
    "environment_overview", "environment" -> Icons.Default.ViewInAr
    "wsl_runtime", "projects" -> Icons.Default.Folder
    "build_service" -> Icons.Default.Download
    "tool_packages", "tools" -> Icons.AutoMirrored.Filled.ListAlt
    "diagnostics_recovery", "recovery" -> Icons.Default.Healing
    else -> Icons.Default.Folder
}
