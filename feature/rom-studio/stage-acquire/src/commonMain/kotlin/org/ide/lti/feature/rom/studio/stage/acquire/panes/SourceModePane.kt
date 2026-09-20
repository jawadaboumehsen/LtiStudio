/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.acquire.panes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.AcquisitionSettings

@Composable
public fun SourceModePane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("SourceModePane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        AcquireHeader(
            title = "Source",
            subtitle = "Configure acquisition mode and target firmware source.",
            targetSpec = "${target.codename} / Development",
            targetRevision = "Target revision ${target.revision}",
            onValidate = onValidate,
            onSave = onSave,
            colors = colors,
        )

        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = GlassShapes.Panel,
            surfaceColor = colors.cardBackground,
            borderColor = colors.border,
            contentPadding = Spacing.Medium,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
                Text(
                    text = "Source mode",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textMain,
                )
                Text(
                    text = "Pinned target: ${target.friendlyTitle}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textMuted,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    AcquisitionMode.entries.forEach { mode ->
                        val enabled = mode == AcquisitionMode.DOWNLOAD || !settings.archiveRef.isNullOrBlank()
                        GlassChip(
                            label = mode.name.replace('_', ' '),
                            selected = settings.mode == mode,
                            enabled = enabled,
                            onClick = if (enabled) {
                                { onChange(settings.copy(mode = mode)) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }
}
