/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.display.GlassPanelContainer
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.model.target.TargetDevice

/**
 * Layer 3: Central Detail Cockpit for Target Hardware & Configuration.
 *
 * Responsibilities:
 * - Rich, expansive detail view with full horizontal breathing room.
 * - Displays hardware telemetry for the workspace's bound target (editing lives in the
 *   Target & Config panel).
 * - Top breadcrumb header.
 * - 100% token conformance (zero raw dp or alpha float literals).
 */
@Composable
fun TargetDetailPanel(selectedTarget: TargetDevice?, modifier: Modifier = Modifier) {
    GlassPanelContainer(
        modifier = modifier.fillMaxSize(),
        header = {
            TargetDetailHeader(
                selectedTarget = selectedTarget,
            )
        },
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(Spacing.Medium),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                if (selectedTarget != null) {
                    TargetTelemetryCard(
                        target = selectedTarget,
                    )
                }
            }
        }
    }
}

@Composable
private fun TargetDetailHeader(selectedTarget: TargetDevice?) {
    if (selectedTarget != null) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        ) {
            BreadcrumbTrail(
                selectedTarget = selectedTarget,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BreadcrumbTrail(selectedTarget: TargetDevice, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        Text(
            text = "Target",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "/",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${selectedTarget.name} (${selectedTarget.codename})",
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = codeFontFamily()),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
