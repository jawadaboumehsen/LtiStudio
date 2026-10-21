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

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassToggle
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings

@Suppress("UnusedParameter")
@Composable
public fun DownloadPolicyPane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onChange: (AcquisitionSettings) -> Unit,
    onValidate: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("DownloadPolicyPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        PolicySettingsCard(
            downloadUrl = settings.firmware.acquisitionUrl,
            retryCount = settings.retries,
            onRetryCountChange = { onChange(settings.copy(retries = it)) },
            resume = settings.resume,
            onResumeChange = { onChange(settings.copy(resume = it)) },
        )
    }
}

@Composable
private fun PolicySettingsCard(
    downloadUrl: String?,
    retryCount: Int,
    onRetryCountChange: (Int) -> Unit,
    resume: Boolean,
    onResumeChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Text(
            text = "Download policy",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = ideFontFamily(),
        )

        PolicyFieldRow(
            label = "Download URL",
            helperText = "From the selected firmware (Firmware baseline). Used only in Download mode.",
            onReset = null,
        ) {
            Text(
                text = downloadUrl ?: "none — this firmware has no URL; import the archive instead",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = ideFontFamily(),
            )
        }

        PolicyFieldRow(
            label = "Retry count",
            helperText = "Retries for a failed download (curl --retry), 0 to 5.",
            onReset = { onRetryCountChange(DEFAULT_RETRIES) }.takeIf { retryCount != DEFAULT_RETRIES },
        ) {
            NumberStepper(value = retryCount, onValueChange = onRetryCountChange, min = 0, max = 5)
        }

        PolicyFieldRow(
            label = "Resume interrupted downloads",
            helperText = "Continue a partial file when the server supports ranges; otherwise start over.",
            onReset = { onResumeChange(true) }.takeIf { !resume },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                GlassToggle(
                    checked = resume,
                    onCheckedChange = onResumeChange,
                    activeColor = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = if (resume) "Enabled" else "Disabled",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun PolicyFieldRow(
    label: String,
    helperText: String?,
    /** Puts the default back; null when the value is the default (or not editable here). */
    onReset: (() -> Unit)?,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(GlassDimens.AcquirePolicyLabelWidth)) {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.Medium,
                fontFamily = ideFontFamily(),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            control()
            if (helperText != null) {
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = helperText,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        Spacer(Modifier.width(Spacing.Large))

        if (onReset != null) ResetPill(onReset)
    }
}

/** Shown only for a changed value: says so, and puts the default back when clicked. */
@Composable
private fun ResetPill(onReset: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(GlassShapes.ShellControl)
            .background(GlassTheme.diagnosticColors.success.copy(alpha = AlphaTokens.Glow))
            .border(
                GlassDimens.HairlineBorder,
                GlassTheme.diagnosticColors.success.copy(alpha = AlphaTokens.Muted),
                GlassShapes.ShellControl,
            )
            .clickable(onClick = onReset)
            .testTag("ResetToDefault")
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Changed · Reset",
            color = GlassTheme.diagnosticColors.success,
            fontSize = FontSize.Micro,
            fontWeight = FontWeight.Medium,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun NumberStepper(value: Int, onValueChange: (Int) -> Unit, min: Int, max: Int) {
    Row(
        modifier = Modifier
            .clip(GlassShapes.ShellControl)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outline, GlassShapes.ShellControl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .clickable(enabled = value > min) { onValueChange(value - 1) }
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "Decrease",
                tint = if (value >
                    min
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(IconSize.Small),
            )
        }

        Text(
            text = value.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontWeight = FontWeight.Bold,
            fontFamily = codeFontFamily(),
            modifier = Modifier.padding(horizontal = Spacing.Medium),
        )

        Box(
            modifier = Modifier
                .clickable(enabled = value < max) { onValueChange(value + 1) }
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Increase",
                tint = if (value <
                    max
                ) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

private const val DEFAULT_RETRIES = 3
