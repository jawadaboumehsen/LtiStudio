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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens

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
    Column(
        modifier = modifier
            .width(IdeShellDimens.PipelineRailWidth)
            .fillMaxHeight()
            .background(colors.railBackground),
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
        isSelected -> colors.controlSelected
        isPressed -> colors.controlHover
        isHovered -> colors.controlHover.copy(alpha = AlphaTokens.Half)
        else -> Color.Transparent
    }

    val contentColor = when {
        !stage.enabled -> colors.textDim.copy(alpha = AlphaTokens.Disabled)
        isSelected -> colors.accentBlue
        isHovered -> colors.textMain
        else -> colors.textMuted
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(IdeShellDimens.PipelineStageItemHeight)
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
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(IdeShellDimens.PipelineActiveIndicatorWidth)
                    .height(IdeShellDimens.PipelineActiveIndicatorHeight)
                    .background(colors.accentBlue),
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val vector = resolveRailIcon(stage.id)
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(IconSize.Medium),
            )
            Text(
                text = stage.label,
                color = contentColor,
                fontSize = FontSize.Micro,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                fontFamily = GlassFontFamily.ide(),
                maxLines = 1,
            )
        }
    }
}

private fun resolveRailIcon(stageId: String): ImageVector = when (stageId.lowercase()) {
    "acquire" -> Icons.Default.Download
    "extract" -> Icons.Default.Folder
    "assemble" -> Icons.Default.Layers
    "debloat" -> Icons.Default.Delete
    "patches" -> Icons.Default.Widgets
    "build" -> Icons.Default.Build
    "metadata" -> Icons.Default.Settings
    "publish" -> Icons.Default.Public
    else -> Icons.Default.Folder
}
