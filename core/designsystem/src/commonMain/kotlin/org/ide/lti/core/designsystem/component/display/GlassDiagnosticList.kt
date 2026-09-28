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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.theme.Spacing

/**
 * Reusable Liquid Glass list rendering a sequence of [GlassDiagnosticItem]s.
 *
 * Automatically keys items by [GlassDiagnosticItem.id] and provides uniform spacing.
 *
 * @param items List of diagnostic items to render.
 * @param modifier Optional layout modifier.
 */
@Composable
fun GlassDiagnosticList(items: List<GlassDiagnosticItem>, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            items.forEach { item ->
                key(item.id) {
                    GlassDiagnosticTile(item = item)
                }
            }
        }
    }
}
