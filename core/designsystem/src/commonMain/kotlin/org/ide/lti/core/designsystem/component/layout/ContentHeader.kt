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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.ideFontFamily

/**
 * Standard content panel header in the Blue Glass IDE frame.
 *
 * Sits directly inside the inset content panel and presents:
 * - Breadcrumb navigation trail
 * - Prominent H1 stage / section title
 * - Optional descriptive subtitle or status badge
 * - Right-aligned primary stage action controls (e.g. Validate, Save, Discard)
 */
@Composable
fun ContentHeader(
    title: String,
    modifier: Modifier = Modifier,
    breadcrumbs: List<String> = emptyList(),
    subtitle: String? = null,
    statusBadge: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Large)
            .testTag("ContentHeader"),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        if (breadcrumbs.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                modifier = Modifier.testTag("ContentHeaderBreadcrumbs"),
            ) {
                breadcrumbs.forEachIndexed { index, segment ->
                    val isLast = index == breadcrumbs.lastIndex
                    Text(
                        text = segment,
                        color = if (isLast) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontFamily = ideFontFamily(),
                        fontSize = FontSize.CodeMedium,
                        fontWeight = if (isLast) FontWeight.Medium else FontWeight.Normal,
                    )
                    if (!isLast) {
                        Text(
                            text = "/",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontFamily = ideFontFamily(),
                            fontSize = FontSize.CodeMedium,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = ideFontFamily(),
                        fontSize = FontSize.HeadlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("ContentHeaderTitle"),
                    )
                    statusBadge?.invoke()
                }

                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = ideFontFamily(),
                        fontSize = FontSize.BodyMedium,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.testTag("ContentHeaderSubtitle"),
                    )
                }
            }

            if (actions != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    modifier = Modifier.testTag("ContentHeaderActions"),
                ) {
                    actions()
                }
            }
        }
    }
}
