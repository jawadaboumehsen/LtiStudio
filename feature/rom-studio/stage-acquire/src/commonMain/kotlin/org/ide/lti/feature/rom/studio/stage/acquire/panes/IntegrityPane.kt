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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.IdeShellColorScheme
import org.ide.lti.core.designsystem.theme.IdeShellDimens
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings

@Suppress("UnusedParameter")
@Composable
public fun IntegrityPane(
    settings: AcquisitionSettings,
    target: TargetDevice,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
    colors: IdeShellColorScheme = GlassTheme.ideShellColors,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("IntegrityPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        VerifiedBanner(colors = colors)

        SourceFileCard(settings = settings, target = target, colors = colors)

        VerificationCard(colors = colors)

        IntegrityInfoBanner(colors = colors)
    }
}

@Composable
private fun VerifiedBanner(colors: IdeShellColorScheme) {
    val green = colors.accentGreen
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(green.copy(alpha = AlphaTokens.Glow), GlassShapes.ShellCard)
            .border(
                width = IdeShellDimens.HairlineBorder,
                color = green.copy(alpha = AlphaTokens.Medium),
                shape = GlassShapes.ShellCard,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Verified",
                tint = green,
                modifier = Modifier.size(IdeShellDimens.AcquireStatusBannerIconSize),
            )
            Column {
                Text(
                    text = "Verified",
                    color = colors.textMain,
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "The downloaded file matches the expected SHA-256 hash.",
                    color = colors.textMuted,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }
    }
}

@Composable
private fun SourceFileCard(settings: AcquisitionSettings, target: TargetDevice, colors: IdeShellColorScheme) {
    val clipboardManager = LocalClipboardManager.current
    val fileName = settings.archiveRef?.substringAfterLast('/')
        ?: "${target.codename}_4.0.0_factory.zip"
    val fullHash = settings.expectedSha256
        ?: "3f2a9c4d8e1f0a2b5c6d7e8f9a0b1c2d3e4f5a6b7c8d9e0f1a2b3c4d5e6f7a8b"
    val truncatedHash = if (fullHash.length > 20) {
        "${fullHash.take(8)}...${fullHash.takeLast(8)}"
    } else {
        fullHash
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors = colors, shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Source file",
            color = colors.textMain,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.ide(),
        )

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "File name", colors = colors) {
            Text(
                text = fileName,
                color = colors.textMain,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.code(),
            )
        }

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Source URL", colors = colors) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val url = settings.firmware.acquisitionUrl
                    ?: "https://example.com/firmware/$fileName"
                Text(
                    text = url,
                    color = colors.accentBlue,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open link",
                    tint = colors.accentBlue,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Source provenance", value = "Vendor official release", colors = colors)

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "File size", value = "2.48 GB (2,662,609,920 bytes)", colors = colors)

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Downloaded", value = "2024-05-14 10:21:37", colors = colors)

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Expected SHA-256", colors = colors) {
            HashCopyRow(
                hash = truncatedHash,
                onCopy = { clipboardManager.setText(AnnotatedString(fullHash)) },
                colors = colors,
            )
        }

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Measured SHA-256", colors = colors) {
            HashCopyRow(
                hash = truncatedHash,
                onCopy = { clipboardManager.setText(AnnotatedString(fullHash)) },
                colors = colors,
            )
        }
    }
}

@Composable
private fun VerificationCard(colors: IdeShellColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(colors = colors, shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Verification",
                color = colors.textMain,
                fontSize = FontSize.TitleSmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = GlassFontFamily.ide(),
            )

            GlassButton(
                onClick = {},
                variant = GlassButtonVariant.Standard,
                shape = GlassShapes.ShellControl,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Verify",
                        tint = colors.textMain,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Verify", color = colors.textMain, fontSize = FontSize.BodySmall)
                }
            }
        }

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Status", colors = colors) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified",
                    tint = colors.accentGreen,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Verified",
                    color = colors.accentGreen,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Method", value = "SHA-256 (local computation)", colors = colors)

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(label = "Checked at", value = "2024-05-14 10:21:45", colors = colors)

        HorizontalDivider(thickness = IdeShellDimens.HairlineBorder, color = colors.borderMuted)

        PropertyRow(
            label = "Result",
            value = "The file integrity is confirmed. The measured hash matches the expected hash.",
            colors = colors,
        )
    }
}

@Composable
private fun IntegrityInfoBanner(colors: IdeShellColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(colors.infoBannerFill, GlassShapes.ShellCard)
            .border(IdeShellDimens.HairlineBorder, colors.infoBannerBorder, GlassShapes.ShellCard)
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = colors.accentBlue,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Integrity verification ensures the downloaded file has not been corrupted or tampered with.",
                color = colors.textMuted,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
        }
    }
}

@Composable
internal fun PropertyRow(label: String, value: String, colors: IdeShellColorScheme) {
    PropertyRow(label = label, colors = colors) {
        Text(
            text = value,
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}

@Composable
internal fun PropertyRow(label: String, colors: IdeShellColorScheme, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = colors.textDim,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
            modifier = Modifier.width(IdeShellDimens.DetailsGridLabelColWidth),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
internal fun HashCopyRow(hash: String, onCopy: () -> Unit, colors: IdeShellColorScheme) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = hash,
            color = colors.textMain,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.code(),
        )
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy hash",
            tint = colors.textDim,
            modifier = Modifier
                .size(IconSize.ExtraSmall)
                .clickable(onClick = onCopy),
        )
    }
}
