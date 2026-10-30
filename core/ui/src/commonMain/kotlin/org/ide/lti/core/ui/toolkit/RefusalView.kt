/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.toolkit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerAction
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.Refusal

/**
 * Standard visual representation of a [Refusal] (contract: `contracts/messages.md`, FR-037).
 *
 * Displays:
 * 1. Cause: prominent sentence explaining why the operation was refused or failed.
 * 2. Item: the file, partition, or tool involved.
 * 3. Next step: the plain sentence action the user can take.
 * 4. Optional Details expander: technical diagnostics (stderr, exit codes) collapsed by default.
 * 5. Optional action button (e.g. "Add entries" for coverage refusals).
 */
@Composable
public fun RefusalView(
    refusal: Refusal,
    modifier: Modifier = Modifier,
    isInfo: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    var isDetailsExpanded by remember { mutableStateOf(false) }

    val severity = if (isInfo) GlassBannerSeverity.INFO else GlassBannerSeverity.ERROR
    val primaryAction = if (actionLabel != null && onAction != null) {
        GlassBannerAction(label = actionLabel, onClick = onAction)
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        GlassBanner(
            severity = severity,
            title = refusal.cause,
            description = "${refusal.item} • ${refusal.nextStep}",
            primaryAction = primaryAction,
        )

        val details = refusal.details
        if (!details.isNullOrBlank()) {
            RefusalDetailsExpander(
                details = details,
                isExpanded = isDetailsExpanded,
                onToggle = { isDetailsExpanded = !isDetailsExpanded },
            )
        }
    }
}

@Composable
private fun RefusalDetailsExpander(
    details: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val detailsBackground = colors.surfaceContainerHighest.copy(alpha = AlphaTokens.UltraFaint)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Small)
            .background(detailsBackground)
            .padding(Spacing.Small),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GlassShapes.Small)
                .clickable(onClick = onToggle)
                .padding(vertical = Spacing.ExtraExtraSmall, horizontal = Spacing.ExtraSmall),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (isExpanded) "Collapse details" else "Expand details",
                tint = colors.onSurfaceVariant,
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.Small)
                        .background(colors.surfaceContainerLowest)
                        .padding(Spacing.Small),
                ) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = codeFontFamily()),
                        color = colors.onSurface,
                    )
                }
            }
        }
    }
}
