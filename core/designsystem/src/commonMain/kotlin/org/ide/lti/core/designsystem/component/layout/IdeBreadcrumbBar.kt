/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Breadcrumb bar matching the 32 dp Stitch design specification.
 */
@Composable
fun IdeBreadcrumbBar(segments: List<String>, modifier: Modifier = Modifier, onSegmentClick: ((Int) -> Unit)? = null) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GlassDimens.BreadcrumbHeight)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        segments.forEachIndexed { index, segment ->
            val isLast = index == segments.size - 1
            val textColor = if (isLast) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
            val fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal

            Text(
                text = segment,
                color = textColor,
                fontSize = FontSize.BodySmall,
                fontWeight = fontWeight,
                fontFamily = GlassFontFamily.ide(),
                modifier = if (onSegmentClick != null && !isLast) {
                    Modifier.clickable { onSegmentClick(index) }
                } else {
                    Modifier
                },
            )

            if (!isLast) {
                Text(
                    text = "/",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }
    }
}
