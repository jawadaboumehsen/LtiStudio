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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily

/** Standard bordered glass section card used across ROM Studio stage editors and panes. */
@Composable
fun IdeSectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    titleFontSize: TextUnit = FontSize.TitleSmall,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .ideShellCardSurface(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (title != null) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = titleFontSize,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
        }
        content()
    }
}

/** Labelled monospaced key-value detail row with fixed policy label width. */
@Composable
fun IdeDetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    labelWidth: Dp = GlassDimens.AcquirePolicyLabelWidth,
    valueColor: Color? = null,
    fontSize: TextUnit = FontSize.BodySmall,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = fontSize,
            fontFamily = ideFontFamily(),
            modifier = Modifier.width(labelWidth),
        )
        Text(
            text = value,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
            fontSize = fontSize,
            fontFamily = codeFontFamily(),
        )
    }
}

/** Secondary hint or error-styled description label. */
@Composable
fun IdeHintText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: TextUnit = FontSize.BodySmall,
) {
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontFamily = ideFontFamily(),
        modifier = modifier,
    )
}

/** Monospaced metadata or value line. */
@Composable
fun IdeMonoText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = FontSize.BodySmall,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color,
        fontSize = fontSize,
        fontFamily = codeFontFamily(),
        maxLines = maxLines,
        modifier = modifier,
    )
}
