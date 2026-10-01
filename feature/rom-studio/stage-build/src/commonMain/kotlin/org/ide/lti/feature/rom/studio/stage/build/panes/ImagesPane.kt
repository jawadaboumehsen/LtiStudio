/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.build.panes

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.IdeStatusBadge
import org.ide.lti.core.designsystem.component.display.IdeStatusSeverity
import org.ide.lti.core.designsystem.component.display.ideCardSurface
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
import org.ide.lti.core.model.workspace.BuildSettings

/**
 * Stage Build subobject pane for configuring partition images and verifying sizes.
 * Matches Blue Glass styling.
 */
@Suppress("LongParameterList", "UnusedParameter")
@Composable
public fun ImagesPane(
    settings: BuildSettings,
    target: TargetDevice,
    onChange: (BuildSettings) -> Unit,
    onValidate: () -> Unit = {},
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ImagesPane"),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        ImagesNoticeCard()
        ImagesTableCard(settings = settings)
        ImagesSummaryCard()
        ImagesValidationCard(onRunValidation = onValidate)
        ImagesFootnoteCard()
    }
}

@Composable
private fun ImagesNoticeCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Subtle))
            .border(
                GlassDimens.HairlineBorder,
                MaterialTheme.colorScheme.primary.copy(alpha = AlphaTokens.Ambient),
                GlassShapes.ShellCard,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(IconSize.Medium),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Hairline),
            ) {
                Text(
                    text = "Build Android system images from the prepared source trees.",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
                Text(
                    text = "Images use the patched checkpoints from earlier stages. Validate sizes before packaging.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                modifier = Modifier.clickable { },
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "How image building works",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = FontSize.Micro,
                    fontFamily = ideFontFamily(),
                )
            }
        }
    }
}

private data class ImageRowData(
    val index: String,
    val partition: String,
    val source: String,
    val isSourceConfigured: Boolean,
    val builder: String,
    val outputPath: String,
    val isReady: Boolean,
    val sizeEst: String,
)

@Suppress("UnusedParameter")
@Composable
private fun ImagesTableCard(settings: BuildSettings) {
    val rows = listOf(
        ImageRowData(
            index = "1",
            partition = "system",
            source = "out/target/product/sunfish/system",
            isSourceConfigured = true,
            builder = "EROFS (recommended)",
            outputPath = "out/images/system.img",
            isReady = true,
            sizeEst = "2.38 GB",
        ),
        ImageRowData(
            index = "2",
            partition = "product",
            source = "out/target/product/sunfish/product",
            isSourceConfigured = true,
            builder = "EROFS (recommended)",
            outputPath = "out/images/product.img",
            isReady = true,
            sizeEst = "612 MB",
        ),
        ImageRowData(
            index = "3",
            partition = "vendor",
            source = "out/target/product/sunfish/vendor",
            isSourceConfigured = true,
            builder = "EROFS (recommended)",
            outputPath = "out/images/vendor.img",
            isReady = true,
            sizeEst = "498 MB",
        ),
        ImageRowData(
            index = "4",
            partition = "odm",
            source = "Not configured",
            isSourceConfigured = false,
            builder = "Select builder...",
            outputPath = "-",
            isReady = false,
            sizeEst = "-",
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        ImagesTableHeader()
        HorizontalDivider(thickness = GlassDimens.HairlineBorder, color = MaterialTheme.colorScheme.outlineVariant)
        rows.forEach { row ->
            ImagesTableRow(row = row)
        }
    }
}

@Composable
private fun ImagesTableHeader() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "#",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.width(Spacing.Large),
        )
        Text(
            "Partition",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            text = "Source (patched checkpoint)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(2.0f),
        )
        Text(
            "Image builder",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            "Output path",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.5f),
        )
        Text(
            "Status",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        Text(
            "Size (est.)",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
    }
}

@Composable
private fun ImagesTableRow(row: ImageRowData) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraSmall),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            row.index,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            modifier = Modifier.width(Spacing.Large),
        )
        Text(
            text = row.partition,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
        if (row.isSourceConfigured) {
            Text(
                text = row.source,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = codeFontFamily(),
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(2.0f),
            )
        } else {
            Text(
                text = row.source,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                fontSize = FontSize.Micro,
                modifier = Modifier.weight(2.0f),
            )
        }
        Box(
            modifier = Modifier
                .weight(1.5f)
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .border(GlassDimens.HairlineBorder, MaterialTheme.colorScheme.outlineVariant, GlassShapes.ShellControl)
                .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = row.builder,
                    color = if (row.isReady) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontSize = FontSize.Micro,
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
            }
        }
        Spacer(Modifier.width(Spacing.Small))
        Text(
            text = row.outputPath,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = codeFontFamily(),
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.5f),
        )
        Box(modifier = Modifier.weight(1.0f)) {
            if (row.isReady) {
                IdeStatusBadge(label = "Ready", severity = IdeStatusSeverity.Ready)
            } else {
                IdeStatusBadge(label = "Disabled", severity = IdeStatusSeverity.Neutral)
            }
        }
        Text(
            text = row.sizeEst,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            fontSize = FontSize.Micro,
            modifier = Modifier.weight(1.0f),
        )
    }
}

@Composable
private fun ImagesSummaryCard() {
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
                text = "Image size summary",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = GlassTheme.diagnosticColors.success,
                    modifier = Modifier.size(IconSize.ExtraSmall),
                )
                Text(
                    text = "Within recommended size",
                    color = GlassTheme.diagnosticColors.success,
                    fontSize = FontSize.Micro,
                    fontWeight = FontWeight.Medium,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(Spacing.Small)
                .clip(GlassShapes.ShellControl)
                .background(MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.58f)
                    .height(Spacing.Small)
                    .clip(GlassShapes.ShellControl)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Estimated total size: 3.49 GB",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
            Text(
                text = "Target limit: 6.00 GB (typical)",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun ImagesValidationCard(onRunValidation: () -> Unit) {
    var expanded by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = "Validation",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = FontSize.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = ideFontFamily(),
                )
            }
        }

        if (expanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    ImagesValidationCheckItem("System image size is within recommended limits")
                    ImagesValidationCheckItem("Product image size is within recommended limits")
                    ImagesValidationCheckItem("Vendor image size is within recommended limits")
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                ) {
                    Text(
                        text = "Last validated 2024-06-12 10:24",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                    GlassButton(
                        onClick = onRunValidation,
                        variant = GlassButtonVariant.Primary,
                        shape = GlassShapes.ShellControl,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(IconSize.ExtraSmall),
                            )
                            Text(
                                "Run validation",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = FontSize.Micro,
                            )
                        }
                    }
                    Text(
                        text = "Checks image sizes and configuration before packaging.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = FontSize.Micro,
                        fontFamily = ideFontFamily(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ImagesValidationCheckItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            Icons.Default.Check,
            null,
            tint = GlassTheme.diagnosticColors.success,
            modifier = Modifier.size(IconSize.ExtraSmall),
        )
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}

@Composable
private fun ImagesFootnoteCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Icon(
            Icons.Default.Info,
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(IconSize.Small),
        )
        Text(
            text = "These images will be included in the flashable package after signing. " +
                "Continue to the next step when validation passes.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = FontSize.Micro,
            fontFamily = ideFontFamily(),
        )
    }
}
