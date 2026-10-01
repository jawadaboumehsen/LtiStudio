/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassIconToggleButton
import org.ide.lti.core.designsystem.component.primitives.GlassSurface
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.HazeShape
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Glass panel component optimized for IDE tool windows and sidebar panels.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: HazeShape = GlassShapes.HazePanel,
    surfaceColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    contentPadding: Dp = Spacing.PanelPadding,
    content: @Composable () -> Unit,
) {
    val panelModifier = modifier
        .then(if (width != null && width > Spacing.None) Modifier.width(width) else Modifier)
        .fillMaxHeight()

    GlassSurface(
        modifier = panelModifier,
        shape = shape,
        surfaceColor = surfaceColor,
        tint = tint,
        borderColor = borderColor,
    ) {
        Box(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}

/**
 * Action item inside a panel header adhering to Open/Closed Principle.
 */
sealed interface PanelHeaderAction {
    data class IconAction(val icon: AppIcon, val contentDescription: String? = null, val onClick: () -> Unit) :
        PanelHeaderAction

    data class ToggleAction(
        val icon: AppIcon,
        val isChecked: Boolean,
        val contentDescription: String? = null,
        val onToggle: (Boolean) -> Unit,
    ) : PanelHeaderAction

    data class Custom(val content: @Composable () -> Unit) : PanelHeaderAction
}

/**
 * Header for panels with title and optional action buttons.
 */
@Composable
fun GlassPanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            if (icon != null) {
                when (icon) {
                    is AppIcon.Vector -> Icon(
                        imageVector = icon.imageVector,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    is AppIcon.Painted -> Icon(
                        painter = icon.painter(),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Small))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actions?.invoke()
    }
}

/**
 * Header for panels using strongly typed [PanelHeaderAction] items.
 */
@Composable
fun GlassPanelHeader(
    title: String,
    headerActions: List<PanelHeaderAction>,
    modifier: Modifier = Modifier,
    icon: AppIcon? = null,
) = GlassPanelHeader(
    title = title,
    modifier = modifier,
    icon = icon,
    actions = {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            headerActions.forEach { action ->
                when (action) {
                    is PanelHeaderAction.IconAction -> GlassPanelHeaderAction(
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        onClick = action.onClick,
                    )
                    is PanelHeaderAction.ToggleAction -> GlassIconToggleButton(
                        checked = action.isChecked,
                        onCheckedChange = action.onToggle,
                        icon = action.icon,
                        contentDescription = action.contentDescription,
                        size = ComponentSize.PanelHeaderActionTouchTarget,
                    )
                    is PanelHeaderAction.Custom -> action.content()
                }
            }
        }
    },
)

@Composable
fun GlassPanelHeader(
    title: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
) = GlassPanelHeader(
    title = title,
    modifier = modifier,
    icon = icon?.let { AppIcon.Vector(it) },
    actions = actions,
)

/**
 * Action button inside a panel header.
 */
@Composable
fun GlassPanelHeaderAction(
    icon: AppIcon,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        size = ComponentSize.PanelHeaderActionTouchTarget,
    ) {
        when (icon) {
            is AppIcon.Vector -> Icon(
                imageVector = icon.imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize.PanelHeaderAction),
            )
            is AppIcon.Painted -> Icon(
                painter = icon.painter(),
                contentDescription = contentDescription,
                modifier = Modifier.size(IconSize.PanelHeaderAction),
            )
        }
    }
}

@Composable
fun GlassPanelHeaderAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = GlassPanelHeaderAction(
    icon = AppIcon.Vector(icon),
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
)

/**
 * Container for panel content with an optional header.
 *
 * @param modifier Modifier to be applied to the container.
 * @param header Optional header composable slot.
 * @param content The main panel content.
 */
@Composable
fun GlassPanelContainer(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        header?.let {
            it()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Standard)
                    .background(MaterialTheme.colorScheme.outline),
            )
        }
        content()
    }
}
