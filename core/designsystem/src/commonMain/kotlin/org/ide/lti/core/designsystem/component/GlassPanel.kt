/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.BackdropEffectScope
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.CornerRadius
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Sidebar/panel glass effect stack, per the kmp-liquid-glass skill's "moderate effect for
 * side navigation panels" guidance.
 */
val GlassPanelEffects: BackdropEffectScope.() -> Unit = {
    vibrancy()
    blur(6.dp.toPx())
    lens(12.dp.toPx(), 24.dp.toPx())
}

/**
 * Glass panel component optimized for IDE tool windows and sidebar panels.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    width: Dp? = null,
    shape: Shape = ContinuousRoundedRectangle(CornerRadius.Panel),
    effects: BackdropEffectScope.() -> Unit = GlassPanelEffects,
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
 * Header for panels with title and optional action buttons.
 */
@Composable
fun GlassPanelHeader(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
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
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = Spacing.Small)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
            )
        }
        actions?.invoke()
    }
}

/**
 * Action button inside a panel header.
 */
@Composable
fun GlassPanelHeaderAction(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(ComponentSize.PanelHeaderAction),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(IconSize.Medium),
        )
    }
}

/**
 * Container for panel content with optional header.
 */
@Composable
fun GlassPanelContainer(
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Surface(modifier = modifier) {
        Column(modifier = Modifier.fillMaxSize()) {
            header?.invoke()
            content()
        }
    }
}
