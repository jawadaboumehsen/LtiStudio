/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.component.progress

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.progressSemantics
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing

/** Indeterminate work with a visible, descriptive label. */
@Composable
fun GlassLoadingStatus(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSpinner(modifier = Modifier.size(IconSize.Small))
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
    }
}

/**
 * macOS-style polished placeholder for a section, tab, or screen whose content is loading.
 * Shows a status header with a spinner, an Apple-inspired slim progress bar, and shimmering
 * frosted glass skeleton rows.
 */
@Composable
fun GlassLoadingPlaceholder(
    label: String,
    modifier: Modifier = Modifier,
    showProgressBar: Boolean = true,
    rowsCount: Int = 3,
) {
    Column(
        modifier = modifier.fillMaxWidth().progressSemantics(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlassSpinner(modifier = Modifier.size(IconSize.Small))
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = FontSize.BodySmall)
        }
        if (showProgressBar) {
            GlassProgressBar(progress = null, modifier = Modifier.fillMaxWidth())
        }
        GlassSkeletonList(
            count = rowsCount,
            hasIcon = true,
            hasSubtitle = true,
            hasTrailingBadge = true,
        )
    }
}
