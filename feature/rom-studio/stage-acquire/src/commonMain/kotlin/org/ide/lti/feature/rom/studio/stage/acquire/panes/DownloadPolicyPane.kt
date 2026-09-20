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
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
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
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
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
            colors = colors,
        )

        AdvancedOptionsAccordion(
            isOpen = isAdvancedOpen,
            onToggle = { isAdvancedOpen = !isAdvancedOpen },
            colors = colors,
        )

        NeedHelpBanner(colors = colors)
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
    colors: IdeShellColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
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
                color = colors.textMain,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Applies to all downloads in this workspace.",
                color = colors.textDim,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }

        PolicyFieldRow(
            label = "Source URL",
            helperText = "Base URL for downloading source files.",
            isOverride = false,
            colors = colors,
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
            colors = colors,
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
                    colors = colors,
                )
                Column {
                    Text(
                        text = "Range: 0 – 5",
                        color = colors.textMuted,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                    Text(
                        text = "Number of retry attempts for failed downloads.",
                        color = colors.textDim,
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
            colors = colors,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                GlassToggle(
                    checked = resume,
                    onCheckedChange = onResumeChange,
                    activeColor = colors.accentBlue,
                )
                Text(
                    text = if (resume) "Enabled" else "Disabled",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        PolicyFieldRow(
            label = "Timeout per attempt (seconds)",
            helperText = "Time to wait for a response before retrying.",
            isOverride = true,
            colors = colors,
        ) {
            NumberSpinnerInput(
                value = timeoutSeconds,
                onValueChange = onTimeoutChange,
                colors = colors,
            )
        }

        PolicyFieldRow(
            label = "User agent (optional)",
            helperText = "Identifies this application to the server.",
            isOverride = false,
            colors = colors,
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
private fun PolicyFieldRow(
    label: String,
    helperText: String?,
    isOverride: Boolean,
    colors: IdeShellColorScheme,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(IdeShellDimens.AcquirePolicyLabelWidth)) {
            Text(
                text = label,
                color = colors.textMain,
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
                    color = colors.textDim,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        Spacer(Modifier.width(Spacing.Large))

        StatusPillBadge(isOverride = isOverride, colors = colors)
    }
}

@Composable
private fun StatusPillBadge(isOverride: Boolean, colors: IdeShellColorScheme) {
    val bg = if (isOverride) colors.accentGreen.copy(alpha = AlphaTokens.Glow) else colors.cardBackgroundMuted
    val borderColor = if (isOverride) colors.accentGreen.copy(alpha = AlphaTokens.Muted) else colors.borderMuted
    val textColor = if (isOverride) colors.accentGreen else colors.textMuted

    Box(
        modifier = Modifier
            .clip(GlassShapes.ShellControl)
            .background(bg)
            .border(IdeShellDimens.HairlineBorder, borderColor, GlassShapes.ShellControl)
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
private fun NumberStepper(value: Int, onValueChange: (Int) -> Unit, min: Int, max: Int, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .clip(GlassShapes.ShellControl)
            .background(colors.cardBackgroundMuted)
            .border(IdeShellDimens.HairlineBorder, colors.border, GlassShapes.ShellControl),
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
                tint = if (value > min) colors.textMain else colors.textDim,
                modifier = Modifier.size(IconSize.Small),
            )
        }

        Text(
            text = value.toString(),
            color = colors.textMain,
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
                tint = if (value < max) colors.textMain else colors.textDim,
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
private fun NumberSpinnerInput(value: Int, onValueChange: (Int) -> Unit, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier
            .width(IdeShellDimens.AcquirePolicyInputWidth)
            .clip(GlassShapes.ShellControl)
            .background(colors.cardBackgroundMuted)
            .border(IdeShellDimens.HairlineBorder, colors.border, GlassShapes.ShellControl)
            .padding(horizontal = Spacing.Small, vertical = Spacing.Hairline),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = value.toString(),
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )

        Column {
            Icon(
                imageVector = Icons.Default.KeyboardArrowUp,
                contentDescription = "Up",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(IconSize.ExtraSmall)
                    .clickable { onValueChange(value + 30) },
            )
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Down",
                tint = colors.textMuted,
                modifier = Modifier
                    .size(IconSize.ExtraSmall)
                    .clickable { if (value > 30) onValueChange(value - 30) },
            )
        }
    }
}

@Composable
private fun AdvancedOptionsAccordion(isOpen: Boolean, onToggle: () -> Unit, colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors, shape = GlassShapes.ShellCard)
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
                    tint = colors.textMuted,
                    modifier = Modifier
                        .size(IconSize.Small)
                        .rotate(if (isOpen) 90f else 0f),
                )
                Text(
                    text = "Advanced options",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
            }

            Text(
                text = "Proxy, headers and download limits",
                color = colors.textDim,
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
                    color = colors.textMuted,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Max parallel worker threads: 4",
                    color = colors.textMuted,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

@Composable
private fun NeedHelpBanner(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(colors.infoBannerFill, GlassShapes.ShellCard)
            .border(IdeShellDimens.HairlineBorder, colors.infoBannerBorder, GlassShapes.ShellCard)
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Medium),
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Need help?",
                    color = colors.textMain,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = ideFontFamily(),
                )
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(
                    text = "Specify the base URL for source files and configure retry and resume behavior. " +
                        "Use conservative settings for unstable networks. " +
                        "You can validate the configuration before saving.",
                    color = colors.textMuted,
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
                    color = colors.accentBlue,
                    fontSize = FontSize.BodySmall,
                    fontFamily = ideFontFamily(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }
    }
}
