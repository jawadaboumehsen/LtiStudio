/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralDownload
import org.ide.lti.core.designsystem.icon.ideStatusWarning
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.feature.plugins.components.ImportStep
import org.ide.lti.feature.plugins.components.PluginFooterBar
import org.ide.lti.feature.plugins.components.PluginStepper

@Composable
internal fun PluginImportTab(
    importPath: String,
    quarantinedPath: String?,
    inspection: PackageInspection?,
    isLegacyUnsupported: Boolean,
    legacyFormatName: String?,
    installState: InstallState?,
    onImportPathChange: (String) -> Unit,
    onInspectPath: (String) -> Unit,
    onDismissLegacyWarning: () -> Unit,
    onConfirmInstall: (TrustDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    var acknowledgedUnverified by remember(inspection?.identity?.key, importPath) {
        mutableStateOf(false)
    }

    val currentStep = when {
        installState is InstallState.Installed -> ImportStep.INSTALL
        inspection != null -> ImportStep.REVIEW
        else -> ImportStep.INSPECT
    }

    val effectivePath = quarantinedPath ?: importPath
    val displayFileName = effectivePath.substringAfterLast('/').substringAfterLast('\\').ifBlank {
        "No package selected"
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Workspace / Import package",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Review package installation",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Inspect and review package details before installing into your workspace.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ImportPackageHeader(
                displayFileName = displayFileName,
                inspection = inspection,
                importPath = importPath,
                onImportPathChange = onImportPathChange,
                onInspectPath = onInspectPath,
            )

            PluginStepper(currentStep = currentStep)

            when {
                isLegacyUnsupported -> {
                    UnsupportedLegacyFormatPanel(
                        formatName = legacyFormatName ?: "legacy script",
                        onDismiss = onDismissLegacyWarning,
                    )
                }
                inspection == null -> {
                    ImportInspectFirstEmptyState()
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        ImportLeftReviewColumn(
                            inspection = inspection,
                            displayFileName = displayFileName,
                            importPath = importPath,
                            modifier = Modifier.weight(1f),
                        )

                        ImportRightReviewColumn(
                            inspection = inspection,
                            acknowledgedUnverified = acknowledgedUnverified,
                            onAcknowledgedChange = { acknowledgedUnverified = it },
                            installState = installState,
                            onConfirmInstall = onConfirmInstall,
                            onCancel = { onImportPathChange("") },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        PluginFooterBar(
            message = "Installation does not enable this package or modify a ROM.",
        )
    }
}

@Composable
private fun ImportPackageHeader(
    displayFileName: String,
    inspection: PackageInspection?,
    importPath: String,
    onImportPathChange: (String) -> Unit,
    onInspectPath: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasPath = importPath.isNotBlank()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.LogoBadgeSize)
                    .clip(GlassShapes.MediumSmall)
                    .background(PluginThemeColors.SelectedRowBackground)
                    .glassOutlineBorder(
                        width = StrokeWidth.Hairline,
                        color = PluginThemeColors.SelectedRowBorder,
                        shape = GlassShapes.HazeMediumSmall,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = AppIcons.FilePainterResource(),
                    contentDescription = "Package Archive",
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Large),
                )
            }

            Column {
                Text(
                    text = displayFileName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (hasPath) {
                        "Local package archive · Ready for review"
                    } else {
                        "Select a local package archive (.ltimod / .zip) to inspect"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (inspection == null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassTextField(
                    value = importPath,
                    onValueChange = onImportPathChange,
                    placeholder = "C:/path/to/my-plugin.lti-mod.zip or /path/to/pkg.lti-mod.zip",
                    modifier = Modifier.weight(1f),
                )
                GlassButton(
                    onClick = { onInspectPath(importPath) },
                    enabled = hasPath,
                    variant = GlassButtonVariant.Primary,
                    shape = GlassShapes.HazeMediumSmall,
                    minHeight = ComponentSize.PluginActionBtnCompactHeight,
                ) {
                    Text("Inspect")
                }
            }
        }
    }
}

@Composable
private fun ImportInspectFirstEmptyState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.ExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Box(
                modifier = Modifier
                    .size(ComponentSize.ActionCardHeight)
                    .clip(GlassShapes.Medium)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Glow)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = AppIcons.FilePainterResource(),
                    contentDescription = "Inspect archive",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.ExtraLarge),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Text(
                    text = "Select and inspect a package archive",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Select a local package archive (.ltimod / .zip) and click Inspect to review " +
                        "package identity, integrity, signature status, and compatibility before installing.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ImportPackageIdentityCard(
    identityId: String?,
    identityVersion: String?,
    displayFileName: String,
    importPath: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Package identity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GlassHorizontalDivider(specular = true)

            ImportDetailRow("Name", identityId ?: "Identity unavailable")
            ImportDetailRow("Version", identityVersion ?: "Unavailable")
            ImportDetailRow("Package type", "LtiRom package archive")
            ImportDetailRow("File name", displayFileName)
            ImportDetailRow("File size", "Not reported by inspection")
            ImportDetailRow("Imported from", importPath)
            ImportDetailRow("Import time", "Not recorded")
        }
    }
}

@Composable
private fun ImportSdkCompatibilityCard(hasBlockingErrors: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "SDK compatibility",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = if (hasBlockingErrors) {
                        AppIcons.ClearPainterResource()
                    } else {
                        AppIcons.CheckPainterResource()
                    },
                    contentDescription = if (hasBlockingErrors) "Blocked" else "Inspection passed",
                    tint = if (hasBlockingErrors) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = if (hasBlockingErrors) "Blocked" else "Inspection passed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasBlockingErrors) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                )
            }
            Text(
                text = if (hasBlockingErrors) {
                    "The inspection report contains blocking compatibility or validation errors."
                } else {
                    "No blocking compatibility errors were reported."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportContentIntegrityCard(hasBlockingErrors: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Content integrity",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = if (hasBlockingErrors) {
                        AppIcons.ClearPainterResource()
                    } else {
                        AppIcons.CheckPainterResource()
                    },
                    contentDescription = if (hasBlockingErrors) "Failed" else "Inspection passed",
                    tint = if (hasBlockingErrors) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = if (hasBlockingErrors) "Failed" else "Inspection passed",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasBlockingErrors) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                )
            }
            Text(
                text = if (hasBlockingErrors) {
                    "Review the reported validation errors before installation."
                } else {
                    "No blocking structure or integrity errors were reported."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportSignatureCard(isSigned: Boolean) {
    val sigColor = if (isSigned) GlassTheme.diagnosticColors.success else MaterialTheme.colorScheme.onSurfaceVariant
    val sigPainter = if (isSigned) AppIcons.CheckPainterResource() else AppIcons.InfoPainterResource()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Signature",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = sigPainter,
                    contentDescription = "Signature status",
                    tint = sigColor,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = if (isSigned) "Verified signature" else "Unsigned · Unverified publisher",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = sigColor,
                )
            }
            Text(
                text = if (isSigned) {
                    "Digitally signed by verified publisher."
                } else {
                    "This package is not digitally signed."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportLeftReviewColumn(
    inspection: PackageInspection,
    displayFileName: String,
    importPath: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        ImportPackageIdentityCard(
            identityId = inspection.identity?.id,
            identityVersion = inspection.identity?.version,
            displayFileName = displayFileName,
            importPath = importPath,
        )
        val hasBlockingErrors = inspection.report.hasBlockingErrors()
        ImportSdkCompatibilityCard(hasBlockingErrors = hasBlockingErrors)
        ImportContentIntegrityCard(hasBlockingErrors = hasBlockingErrors)
        ImportSignatureCard(isSigned = inspection.hasSignature)
    }
}

@Composable
private fun ImportRightReviewColumn(
    inspection: PackageInspection,
    acknowledgedUnverified: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    installState: InstallState?,
    onConfirmInstall: (TrustDecision) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isSigned = inspection.hasSignature

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        if (!isSigned) {
            UnsignedSecurityAlertCard()
        }

        PackageEffectsCard()

        DependencyResolutionCard(inspection = inspection)

        if (!isSigned) {
            UnsignedAcknowledgementRow(
                acknowledged = acknowledgedUnverified,
                onAcknowledgedChange = onAcknowledgedChange,
            )
        }

        val canInstall = canInstallInspectedPackage(inspection, acknowledgedUnverified)
        val isInstalling = installState is InstallState.Downloading || installState is InstallState.Quarantined

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            GlassButton(
                onClick = {
                    val decision = if (isSigned) {
                        TrustDecision.TRUSTED_SIGNATURE
                    } else {
                        TrustDecision.TRUSTED_UNVERIFIED
                    }
                    onConfirmInstall(decision)
                },
                enabled = canInstall && !isInstalling,
                variant = GlassButtonVariant.Primary,
                shape = GlassShapes.HazeMediumSmall,
                minHeight = ComponentSize.PluginActionBtnCompactHeight,
                contentPadding = PaddingValues(
                    horizontal = Spacing.Medium,
                    vertical = Spacing.ExtraSmall,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        painter = AppIcons.ideGeneralDownload(),
                        contentDescription = "Install",
                        tint = BrandColors.OnLogoBadge,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text(if (isInstalling) "Installing..." else "Install package")
                }
            }

            GlassButton(
                onClick = onCancel,
                variant = GlassButtonVariant.Standard,
                shape = GlassShapes.HazeMediumSmall,
                minHeight = ComponentSize.PluginActionBtnCompactHeight,
                contentPadding = PaddingValues(
                    horizontal = Spacing.Medium,
                    vertical = Spacing.ExtraSmall,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Icon(
                        painter = AppIcons.ClearPainterResource(),
                        contentDescription = "Cancel",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun UnsignedSecurityAlertCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.WarningBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.WarningBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = AppIcons.ideStatusWarning(),
                contentDescription = "Warning",
                tint = PluginThemeColors.WarningText,
                modifier = Modifier.size(IconSize.Medium),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "A valid package is not proof of harmless behavior.",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = PluginThemeColors.WarningText,
                )
                Text(
                    text = "This package was not signed by a verified publisher. Only install packages " +
                        "from sources you trust. Malicious packages can modify your build output or target device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = PluginThemeColors.WarningBody,
                )
            }
        }
    }
}

@Composable
private fun PackageEffectsCard(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            Text(
                text = "Declared effects",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            GlassHorizontalDivider(specular = true)

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.InfoPainterResource(),
                    contentDescription = "Effects unavailable",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "This inspection contract does not expose an operation/effect summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DependencyResolutionCard(inspection: PackageInspection, modifier: Modifier = Modifier) {
    val hasBlockingErrors = inspection.report.hasBlockingErrors()
    val dependencyCount = inspection.dependencies.size
    val conflictCount = inspection.conflicts.size
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        ) {
            Text(
                text = "Dependency resolution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    painter = if (hasBlockingErrors || conflictCount > 0) {
                        AppIcons.ClearPainterResource()
                    } else {
                        AppIcons.CheckPainterResource()
                    },
                    contentDescription = if (hasBlockingErrors || conflictCount > 0) "Blocked" else "Resolved",
                    tint = if (hasBlockingErrors || conflictCount > 0) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = if (hasBlockingErrors || conflictCount > 0) {
                        "Dependency review blocked"
                    } else {
                        "No blocking dependency errors"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (hasBlockingErrors || conflictCount > 0) {
                        GlassTheme.diagnosticColors.error
                    } else {
                        GlassTheme.diagnosticColors.success
                    },
                )
            }
            Text(
                text = "$dependencyCount declared dependencies · $conflictCount declared conflicts.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UnsignedAcknowledgementRow(
    acknowledged: Boolean,
    onAcknowledgedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable(role = Role.Checkbox) {
                onAcknowledgedChange(!acknowledged)
            }
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Box(
            modifier = Modifier
                .size(ComponentSize.PluginStepperNodeSize)
                .clip(GlassShapes.ExtraSmall)
                .background(
                    if (acknowledged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                )
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.ExtraSmall,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (acknowledged) {
                Icon(
                    painter = AppIcons.CheckPainterResource(),
                    contentDescription = "Checked",
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Small),
                )
            }
        }

        Column {
            Text(
                text = "I trust this local package",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "I understand the risks and want to install this package.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportDetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun UnsupportedLegacyFormatPanel(formatName: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.ClearPainterResource(),
                    contentDescription = "Legacy Format Warning",
                    tint = GlassTheme.diagnosticColors.warning,
                    modifier = Modifier.size(IconSize.Medium),
                )
                Text(
                    text = "Unsupported legacy package: $formatName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = "This archive uses a deprecated raw format. " +
                    "To ensure reproducible builds and tamper protection, please repackage using Author Tools.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            GlassButton(
                onClick = onDismiss,
                variant = GlassButtonVariant.Standard,
            ) {
                Text("Dismiss warning")
            }
        }
    }
}
