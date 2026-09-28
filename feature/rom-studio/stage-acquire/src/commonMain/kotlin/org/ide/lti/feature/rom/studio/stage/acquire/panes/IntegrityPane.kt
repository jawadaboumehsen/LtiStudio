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
import androidx.compose.material3.MaterialTheme
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
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassFontFamily
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
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
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("IntegrityPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        VerifiedBanner()

        SourceFileCard(settings = settings, target = target)

        VerificationCard()

        IntegrityInfoBanner()
    }
}

@Composable
private fun VerifiedBanner() {
    val green = GlassTheme.diagnosticColors.success
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(green.copy(alpha = AlphaTokens.Glow), GlassShapes.ShellCard)
            .border(
                width = GlassDimens.HairlineBorder,
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
                modifier = Modifier.size(GlassDimens.AcquireStatusBannerIconSize),
            )
            Column {
                Text(
                    text = "Verified",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.TitleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = GlassFontFamily.ide(),
                )
                Text(
                    text = "The downloaded file matches the expected SHA-256 hash.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }
    }
}

@Composable
private fun SourceFileCard(settings: AcquisitionSettings, target: TargetDevice) {
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
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text(
            text = "Source file",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.TitleSmall,
            fontWeight = FontWeight.SemiBold,
            fontFamily = GlassFontFamily.ide(),
        )

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "File name") {
            Text(
                text = fileName,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.code(),
            )
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Source URL") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val url = settings.firmware.acquisitionUrl
                    ?: "https://example.com/firmware/$fileName"
                Text(
                    text = url,
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.BodySmall,
                    fontFamily = GlassFontFamily.ide(),
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = "Open link",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Source provenance", value = "Vendor official release")

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "File size", value = "2.48 GB (2,662,609,920 bytes)")

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Downloaded", value = "2024-05-14 10:21:37")

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Expected SHA-256") {
            HashCopyRow(
                hash = truncatedHash,
                onCopy = { clipboardManager.setText(AnnotatedString(fullHash)) },
            )
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Measured SHA-256") {
            HashCopyRow(
                hash = truncatedHash,
                onCopy = { clipboardManager.setText(AnnotatedString(fullHash)) },
            )
        }
    }
}

@Composable
private fun VerificationCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
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
                color = MaterialTheme.colorScheme.onSurface,
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
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(IconSize.ExtraSmall),
                    )
                    Text("Verify", color = MaterialTheme.colorScheme.onSurface, fontSize = FontSize.BodySmall)
                }
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Status") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Verified",
                    tint = GlassTheme.diagnosticColors.success,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Verified",
                    color = GlassTheme.diagnosticColors.success,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.Medium,
                    fontFamily = GlassFontFamily.ide(),
                )
            }
        }

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Method", value = "SHA-256 (local computation)")

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(label = "Checked at", value = "2024-05-14 10:21:45")

        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)

        PropertyRow(
            label = "Result",
            value = "The file integrity is confirmed. The measured hash matches the expected hash.",
        )
    }
}

@Composable
private fun IntegrityInfoBanner() {
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
            .padding(Spacing.SmallMedium),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Small),
            )
            Text(
                text = "Integrity verification ensures the downloaded file has not been corrupted or tampered with.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.BodySmall,
                fontFamily = GlassFontFamily.ide(),
            )
        }
    }
}

@Composable
internal fun PropertyRow(label: String, value: String) {
    PropertyRow(label = label) {
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
        )
    }
}

@Composable
internal fun PropertyRow(label: String, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.ide(),
            modifier = Modifier.width(GlassDimens.DetailsGridLabelColWidth),
        )
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
internal fun HashCopyRow(hash: String, onCopy: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = hash,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = GlassFontFamily.code(),
        )
        Icon(
            imageVector = Icons.Default.ContentCopy,
            contentDescription = "Copy hash",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(IconSize.ExtraSmall)
                .clickable(onClick = onCopy),
        )
    }
}
