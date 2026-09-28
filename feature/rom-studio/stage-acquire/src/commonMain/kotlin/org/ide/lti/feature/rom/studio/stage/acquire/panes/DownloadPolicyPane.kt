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

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Remove
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
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
    var sourceUrl by remember(settings) {
        mutableStateOf("https://downloads.vendor-example.com/firmware/")
    }
    var retryCount by remember(settings) { mutableStateOf(settings.retries) }
    var resumeDownloads by remember(settings) { mutableStateOf(settings.resume) }
    var timeoutSeconds by remember { mutableStateOf(600) }
    var userAgent by remember { mutableStateOf("LtiRomStudio/1.0 (+https://ltirom.example)") }
    var isAdvancedOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("DownloadPolicyPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        PolicySettingsCard(
            sourceUrl = sourceUrl,
            onSourceUrlChange = { sourceUrl = it },
            retryCount = retryCount,
            onRetryCountChange = {
                retryCount = it
                onChange(settings.copy(retries = it))
            },
            resume = resumeDownloads,
            onResumeChange = {
                resumeDownloads = it
                onChange(settings.copy(resume = it))
            },
            timeoutSeconds = timeoutSeconds,
            onTimeoutChange = { timeoutSeconds = it },
            userAgent = userAgent,
            onUserAgentChange = { userAgent = it },
        )

        AdvancedOptionsAccordion(
            isOpen = isAdvancedOpen,
            onToggle = { isAdvancedOpen = !isAdvancedOpen },
        )

        NeedHelpBanner()
    }
}

@Composable
private fun PolicySettingsCard(
    sourceUrl: String,
    onSourceUrlChange: (String) -> Unit,
    retryCount: Int,
    onRetryCountChange: (Int) -> Unit,
    resume: Boolean,
    onResumeChange: (Boolean) -> Unit,
    timeoutSeconds: Int,
    onTimeoutChange: (Int) -> Unit,
    userAgent: String,
    onUserAgentChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Policy settings",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Applies to all downloads in this workspace.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        PolicyFieldRow(
            label = "Source URL",
            helperText = "Base URL for downloading source files.",
            isOverride = false,
        ) {
            GlassTextField(
                value = sourceUrl,
                onValueChange = onSourceUrlChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        PolicyFieldRow(
            label = "Retry count",
            helperText = null,
            isOverride = false,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                NumberStepper(
                    value = retryCount,
                    onValueChange = onRetryCountChange,
                    min = 0,
                    max = 5,
                )
                Column {
                    Text(
                        text = "Range: 0 – 5",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                    Text(
                        text = "Number of retry attempts for failed downloads.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                }
            }
        }

        PolicyFieldRow(
            label = "Resume interrupted downloads",
            helperText = "Resume partial files when supported by the server.",
            isOverride = false,
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

        PolicyFieldRow(
            label = "Timeout per attempt (seconds)",
            helperText = "Time to wait for a response before retrying.",
            isOverride = true,
        ) {
            NumberSpinnerInput(
                value = timeoutSeconds,
                onValueChange = onTimeoutChange,
            )
        }

        PolicyFieldRow(
            label = "User agent (optional)",
            helperText = "Identifies this application to the server.",
            isOverride = false,
        ) {
            GlassTextField(
                value = userAgent,
                onValueChange = onUserAgentChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PolicyFieldRow(label: String, helperText: String?, isOverride: Boolean, control: @Composable () -> Unit) {
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

        StatusPillBadge(isOverride = isOverride)
    }
}

@Composable
private fun StatusPillBadge(isOverride: Boolean) {
    val bg = if (isOverride) {
        GlassTheme.diagnosticColors.success.copy(
            alpha = AlphaTokens.Glow,
        )
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val borderColor = if (isOverride) {
        GlassTheme.diagnosticColors.success.copy(
            alpha = AlphaTokens.Muted,
        )
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val textColor = if (isOverride) GlassTheme.diagnosticColors.success else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(GlassShapes.ShellControl)
            .background(bg)
            .border(GlassDimens.HairlineBorder, borderColor, GlassShapes.ShellControl)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (isOverride) "Override" else "Default",
            color = textColor,
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

@Composable
private fun NumberSpinnerInput(value: Int, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .width(GlassDimens.AcquirePolicyInputWidth)
            .clip(GlassShapes.ShellControl)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outline, GlassShapes.ShellControl)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = value.toString(),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )

        Column {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(IconSize.ExtraSmall)
                    .clickable { onValueChange(value + 30) },
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(IconSize.ExtraSmall)
                    .clickable { if (value > 30) onValueChange(value - 30) },
            )
        }
    }
}

@Composable
private fun AdvancedOptionsAccordion(isOpen: Boolean, onToggle: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .clickable(onClick = onToggle)
            .padding(Spacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = if (isOpen) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(IconSize.Small)
                        .rotate(if (isOpen) 90f else 0f),
                )
                Text(
                    text = "Advanced options",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
            }

            Text(
                text = "Proxy, headers and download limits",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        AnimatedVisibility(visible = isOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.Medium),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Configured proxy: Direct connection (no proxy configured)",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Max parallel worker threads: 4",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun NeedHelpBanner() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle), GlassShapes.ShellCard)
            .border(
                GlassDimens.HairlineBorder,
                MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Ambient),
                GlassShapes.ShellCard,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Medium),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Need help?",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ideFontFamily(),
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = "Specify the base URL for source files and configure retry and resume behavior. " +
                        "Use conservative settings for unstable networks. " +
                        "You can validate the configuration before saving.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.clickable {},
            ) {
                Text(
                    text = "Open documentation",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }
    }
}
