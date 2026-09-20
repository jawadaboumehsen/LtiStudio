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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import org.ide.lti.core.designsystem.component.actions.*
import org.ide.lti.core.designsystem.component.devtools.*
import org.ide.lti.core.designsystem.component.feedback.*
import org.ide.lti.core.designsystem.component.inputs.*
import org.ide.lti.core.designsystem.component.layout.*
import org.ide.lti.core.designsystem.component.navigation.*
import org.ide.lti.core.designsystem.component.primitives.*
import org.ide.lti.core.designsystem.icon.AppIcon
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassEffects
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Sidebar/panel glass effect stack with deep frosted glass blur.
 * Delegated to canonical [GlassEffects.Panel].
 */
val GlassPanelEffects: BackdropEffectScope.() -> Unit = GlassEffects.Panel

/**
 * Glass panel component optimized for IDE tool windows and sidebar panels.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: Shape = GlassShapes.Panel,
    effects: BackdropEffectScope.() -> Unit = GlassEffects.Panel,
    backdrop: Backdrop? = null,
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
        effects = effects,
        backdrop = backdrop,
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
    data class IconAction(
        val icon: AppIcon,
        val contentDescription: String? = null,
        val onClick: () -> Unit,
    ) : PanelHeaderAction

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
                        tint = GlassTheme.colors.onGlassSecondary,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                    is AppIcon.Painted -> Icon(
                        painter = icon.painter(),
                        contentDescription = null,
                        tint = GlassTheme.colors.onGlassSecondary,
                        modifier = Modifier.size(IconSize.Medium),
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.Small))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = GlassTheme.colors.onGlass,
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
    backdrop: Backdrop? = null,
) {
    GlassIconButton(
        onClick = onClick,
        modifier = modifier,
        size = ComponentSize.PanelHeaderActionTouchTarget,
        backdrop = backdrop,
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
    backdrop: Backdrop? = null,
) = GlassPanelHeaderAction(
    icon = AppIcon.Vector(icon),
    contentDescription = contentDescription,
    onClick = onClick,
    modifier = modifier,
    backdrop = backdrop,
)

/**
 * Container for panel content with optional header and progressive-blur scroll-edge fades.
 *
 * @param modifier Modifier to be applied to the container.
 * @param header Optional header composable slot.
 * @param scrollEdgeFade Whether to apply progressive-blur scroll-edge fades at the top and bottom.
 * @param content The main panel content.
 */
@Composable
fun GlassPanelContainer(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    scrollEdgeFade: Boolean = false,
    content: @Composable () -> Unit,
) {
    val panelConfig = GlassTheme.panel
    val effectiveScrollEdgeFade = scrollEdgeFade || panelConfig.scrollEdgeFade

    Column(modifier = modifier.fillMaxSize()) {
        header?.let {
            it()
            if (panelConfig.scrollEdgeFade) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.Hairline)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    GlassTheme.colors.glassBorderHighlight.copy(alpha = AlphaTokens.Hover),
                                    GlassTheme.colors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent),
                                    GlassTheme.colors.glassBorder.copy(alpha = AlphaTokens.Hover),
                                ),
                            ),
                        ),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StrokeWidth.Standard)
                        .background(GlassTheme.colors.glassBorder),
                )
            }
        }
        if (!effectiveScrollEdgeFade) {
            content()
        } else {
            val contentBackdrop = rememberLayerBackdrop()
            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(contentBackdrop),
                ) {
                    content()
                }
                GlassScrollEdgeFade(
                    backdrop = contentBackdrop,
                    edge = GlassScrollEdge.Top,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
                GlassScrollEdgeFade(
                    backdrop = contentBackdrop,
                    edge = GlassScrollEdge.Bottom,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
