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

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Standard data table container for IDE tool, baseline, and package listings.
 * Encapsulates the normative Blue Glass surface, header, and row styling.
 */
@Composable
public fun IdeTable(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard),
        content = content,
    )
}

/**
 * Header row for [IdeTable] with fixed 36 dp height and muted title-case labels.
 */
@Composable
public fun IdeTableHeader(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDimens.TableHeaderRowHeight)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Body row for [IdeTable] with fixed 40 dp height, selection state, and optional click handling.
 */
@Composable
public fun IdeTableRow(
    isSelected: Boolean = false,
    isEnabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val rowBg = when {
        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Faded)
        isHovered && isEnabled -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> Color.Transparent
    }

    val clickableModifier = if (onClick != null && isEnabled) {
        Modifier
            .semantics { role = Role.Button }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (isEnabled) 1.0f else AlphaTokens.Disabled),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlassDimens.TableBodyRowHeight)
                .background(rowBg)
                .then(clickableModifier)
                .padding(horizontal = Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Hairline)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
    }
}

/**
 * Header cell text utility for [IdeTableHeader].
 */
@Composable
public fun IdeTableHeaderCell(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontSize = FontSize.Micro,
        fontWeight = FontWeight.SemiBold,
        fontFamily = GlassFontFamily.ide(),
        modifier = modifier,
    )
}

/**
 * Circular 18 dp outline radio selection indicator for table rows.
 */
@Composable
public fun IdeTableRadioIndicator(isSelected: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(GlassDimens.TableSelectionIndicatorSize)
            .glassOutlineBorder(
                width = GlassDimens.HairlineBorder,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                shape = GlassShapes.Circle,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(GlassDimens.StatusBadgeIconSize)
                    .background(MaterialTheme.colorScheme.primary, GlassShapes.Circle),
            )
        }
    }
}
