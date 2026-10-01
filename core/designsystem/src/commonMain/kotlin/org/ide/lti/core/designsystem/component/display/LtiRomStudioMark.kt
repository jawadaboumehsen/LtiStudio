/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.StrokeWidth

/**
 * Branded geometric mark for LtiRom Studio consisting of three stacked isometric layers.
 */
@Composable
fun LtiRomStudioMark(modifier: Modifier = Modifier, tint: Color = BrandColors.OnLogoBadge) {
    Canvas(modifier = modifier.size(IconSize.Large)) {
        val lineStyle = Stroke(
            width = StrokeWidth.Standard.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        repeat(3) { layer ->
            val offset = size.height * (0.08f + layer * 0.22f)
            val path = Path().apply {
                moveTo(size.width * 0.50f, offset)
                lineTo(size.width * 0.88f, offset + size.height * 0.20f)
                lineTo(size.width * 0.50f, offset + size.height * 0.40f)
                lineTo(size.width * 0.12f, offset + size.height * 0.20f)
                close()
            }
            drawPath(path = path, color = tint, style = lineStyle)
        }
    }
}
