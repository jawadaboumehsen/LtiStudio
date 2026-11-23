/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins.components

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Standard card surface styling for plugin management panels and sections,
 * applying clipping, standard card background, and hairline glass outline border.
 */
@Composable
fun Modifier.pluginCardSurface(shape: Shape = GlassShapes.Medium): Modifier = this
    .clip(shape)
    .background(PluginThemeColors.CardBackground)
    .glassOutlineBorder(
        width = StrokeWidth.Hairline,
        color = PluginThemeColors.CardBorder,
        shape = shape,
    )
