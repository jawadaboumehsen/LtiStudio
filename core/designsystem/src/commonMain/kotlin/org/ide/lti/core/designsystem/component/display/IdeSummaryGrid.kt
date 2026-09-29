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

package org.ide.lti.core.designsystem.component.display

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.LetterSpacing
import org.ide.lti.core.designsystem.theme.Spacing

@Immutable
data class IdeSummaryItem(
    val title: String,
    val value: String,
    val detail: String? = null,
    val isUnavailable: Boolean = false,
)

/**
 * Stable four-column structured target summary grid.
 * Layout columns remain fixed even when contents update.
 */
@Composable
fun IdeSummaryGrid(items: List<IdeSummaryItem>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, GlassShapes.ShellCard)
            .glassOutlineBorder(
                color = MaterialTheme.colorScheme.outline,
                width = GlassDimens.HairlineBorder,
                shape = GlassShapes.ShellCard,
            )
            .padding(Spacing.Medium),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        items.forEachIndexed { index, item ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .testTag("SummaryItem_$index"),
            ) {
                Text(
                    text = item.title.uppercase(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = GlassFontFamily.code(),
                    letterSpacing = LetterSpacing.Wide,
                )
                Text(
                    text = item.value,
                    color = if (item.isUnavailable) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    fontSize = FontSize.CodeMedium,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.code(),
                    modifier = Modifier.padding(top = Spacing.ExtraExtraSmall),
                )
                if (item.detail != null) {
                    Text(
                        text = item.detail,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = GlassFontFamily.ide(),
                        modifier = Modifier.padding(top = Spacing.Hairline),
                    )
                }
            }
        }
    }
}
