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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.component.tooltip.GlassTooltipArea
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.feature.plugins.components.PluginFooterBar
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun PluginInstalledTab(
    records: List<InstalledRecord>,
    references: Map<String, List<String>>,
    searchQuery: String,
    onSearchChange: (String) -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onOpenDetails: (InstalledRecord) -> Unit,
    onArchive: (InstalledRecord) -> Unit,
    onUninstall: (InstalledRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = records.filter { record ->
        searchQuery.isBlank() ||
            record.identity.publisher.contains(searchQuery, ignoreCase = true) ||
            record.identity.id.contains(searchQuery, ignoreCase = true) ||
            record.displayName.contains(searchQuery, ignoreCase = true) ||
            record.identity.version.contains(searchQuery, ignoreCase = true)
    }

    var selectedKey by remember { mutableStateOf<String?>(null) }
    val selectedRecord = resolveInstalledSelection(filtered, selectedKey)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // Header: Breadcrumb + Title
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Workspace / Plugin Manager",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Installed plugins",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Manage plugins for this workspace.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // 2-Column Main Content
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                // Left Column: Toolbar + Master Table
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(Spacing.SmallMedium),
                ) {
                    // Toolbar row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        GlassTextField(
                            value = searchQuery,
                            onValueChange = onSearchChange,
                            placeholder = "Search plugins...",
                            leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
                            modifier = Modifier.width(ComponentSize.PluginSearchFieldWidth),
                        )

                        GlassButton(
                            onClick = onNavigateToImport,
                            variant = GlassButtonVariant.Standard,
                            shape = GlassShapes.HazeMediumSmall,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                            ) {
                                Icon(
                                    painter = AppIcons.AddPainterResource(),
                                    contentDescription = "Import",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(IconSize.Small),
                                )
                                Text("Import package")
                            }
                        }
                    }

                    // Table
                    InstalledMasterTable(
                        filtered = filtered,
                        selectedRecord = selectedRecord,
                        references = references,
                        searchQuery = searchQuery,
                        onSelectKey = { selectedKey = it },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Right Column: Inspector Panel
                InstalledInspectorPanel(
                    record = selectedRecord,
                    refs = selectedRecord?.let { references[it.rowKey] }.orEmpty(),
                    filteredEmpty = filtered.isEmpty(),
                    searchQuery = searchQuery,
                    onOpenDetails = { selectedRecord?.let(onOpenDetails) },
                    onArchive = { selectedRecord?.let(onArchive) },
                    onUninstall = { selectedRecord?.let(onUninstall) },
                    modifier = Modifier.width(ComponentSize.PluginInspectorWidth),
                )
            }
        }

        PluginFooterBar(
            message = "Installation never changes a ROM automatically.",
        )
    }
}

@Composable
private fun InstalledMasterTable(
    filtered: List<InstalledRecord>,
    selectedRecord: InstalledRecord?,
    references: Map<String, List<String>>,
    searchQuery: String,
    onSelectKey: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .ideCardSurface(),
    ) {
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (searchQuery.isBlank()) {
                        "No plugins installed yet. Explore Marketplace or Import a local package."
                    } else {
                        "No installed plugins match '$searchQuery'."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                InstalledTableHeader()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(filtered, key = { _, r -> r.rowKey }) { index, record ->
                        val isSelected = record.rowKey == selectedRecord?.rowKey
                        val refs = references[record.rowKey].orEmpty()

                        InstalledTableRow(
                            index = index,
                            record = record,
                            isSelected = isSelected,
                            referencesCount = refs.size,
                            onClick = { onSelectKey(record.rowKey) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstalledTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PluginThemeColors.HeaderBackground)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "#",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableIndexColWidth),
        )
        Text(
            text = "Name",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Version",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableVersionColWidth),
        )
        Text(
            text = "Status",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableStatusColWidth),
        )
        Text(
            text = "References",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableRefsColWidth),
        )
    }
}

@Composable
private fun InstalledTableRow(
    index: Int,
    record: InstalledRecord,
    isSelected: Boolean,
    referencesCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBg = if (isSelected) {
        PluginThemeColors.SelectedRowBackground
    } else {
        Color.Transparent
    }

    val borderModifier = if (isSelected) {
        Modifier.glassOutlineBorder(
            width = StrokeWidth.Hairline,
            color = PluginThemeColors.SelectedRowBorder,
            shape = GlassShapes.Small,
        )
    } else {
        val dividerColor = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint)
        Modifier.drawBehind {
            drawLine(
                color = dividerColor,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = StrokeWidth.Hairline.toPx(),
            )
        }
    }

    val isVerified = record.trust == TrustDecision.TRUSTED_SIGNATURE

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentSize.PluginTableRowHeight)
            .clip(GlassShapes.Small)
            .background(rowBg)
            .then(borderModifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = (index + 1).toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = codeFontFamily(),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableIndexColWidth),
        )

        Row(
            modifier = Modifier.weight(1f).padding(end = Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ideNodesPlugin),
                contentDescription = "Plugin",
                tint = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Large),
            )
            // Long names wrap to a second line; the full name is in the tooltip and the details panel.
            GlassTooltipArea(tooltipText = record.title) {
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = record.identity.version,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = codeFontFamily(),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableVersionColWidth),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            modifier = Modifier.width(ComponentSize.PluginTableStatusColWidth),
        ) {
            Icon(
                painter = if (isVerified) AppIcons.CheckPainterResource() else AppIcons.InfoPainterResource(),
                contentDescription = if (isVerified) "Verified" else "Unverified",
                tint = if (isVerified) PluginThemeColors.StatusVerified else PluginThemeColors.StatusUnverified,
                modifier = Modifier.size(IconSize.Medium),
            )
            Text(
                text = if (isVerified) "Verified" else "Unverified",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isVerified) PluginThemeColors.StatusVerified else PluginThemeColors.StatusUnverified,
            )
        }

        Text(
            text = referencesCount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableRefsColWidth),
        )
    }
}

@Composable
private fun InstalledInspectorPanel(
    record: InstalledRecord?,
    refs: List<String>,
    filteredEmpty: Boolean,
    searchQuery: String,
    onOpenDetails: () -> Unit,
    onArchive: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .ideCardSurface(),
    ) {
        if (record == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filteredEmpty && searchQuery.isNotBlank()) {
                        "No plugins match '$searchQuery'."
                    } else {
                        "Select a plugin to inspect details."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Spacing.Large),
                verticalArrangement = Arrangement.spacedBy(Spacing.Large),
            ) {
                InstalledInspectorHeader(
                    record = record,
                    onOpenDetails = onOpenDetails,
                )

                GlassHorizontalDivider(specular = true)

                InstalledInspectorProperties(record = record)

                GlassHorizontalDivider(specular = true)

                InstalledInspectorActions(
                    label = "${record.title} ${record.identity.version}",
                    refs = refs,
                    onArchive = onArchive,
                    onUninstall = onUninstall,
                )
            }
        }
    }
}

@Composable
private fun InstalledInspectorHeader(
    record: InstalledRecord,
    onOpenDetails: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clickable { onOpenDetails() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Icon(
            painter = painterResource(Res.drawable.ideNodesPlugin),
            contentDescription = "Plugin",
            tint = BrandColors.OnLogoBadge,
            modifier = Modifier.size(ComponentSize.LogoBadgeSize),
        )

        Column {
            Text(
                text = record.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Version ${record.identity.version}",
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InstalledInspectorProperties(record: InstalledRecord, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (record.summary.isNotBlank()) {
            Text(
                text = record.summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        InspectorRow(label = "Package identity", value = record.identity.key)
        InspectorRow(
            label = "Needs",
            value = record.dependencies.joinToString { "${it.publisher}:${it.id}" }.ifBlank { "Nothing else" },
        )
        InspectorRow(label = "Trust", value = trustLabel(record.trust))
        // Installed plugins are offered to every workspace; each turns them on in its own Patches stage.
        InspectorRow(
            label = "Use in a build",
            value = "Patches & Mods stage",
            explanation = "Turn it on in the workspace's Patches & Mods stage; that list is what the build applies.",
        )
    }
}

private fun trustLabel(trust: TrustDecision): String = when (trust) {
    TrustDecision.TRUSTED_SIGNATURE -> "Signed by a verified publisher"
    TrustDecision.TRUSTED_UNVERIFIED -> "Unsigned; you trusted it at install"
    TrustDecision.UNTRUSTED -> "Not trusted"
}

@Composable
private fun InstalledInspectorActions(
    label: String,
    refs: List<String>,
    onArchive: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pending by remember(label) { mutableStateOf<PendingRemoval?>(null) }
    pending?.let { removal ->
        GlassDialog(
            title = if (removal == PendingRemoval.UNINSTALL) "Uninstall $label?" else "Archive $label?",
            onDismissRequest = { pending = null },
            confirmButton = {
                GlassButton(
                    onClick = {
                        pending = null
                        if (removal == PendingRemoval.UNINSTALL) onUninstall() else onArchive()
                    },
                    variant = GlassButtonVariant.Primary,
                ) { Text(if (removal == PendingRemoval.UNINSTALL) "Uninstall" else "Archive") }
            },
            dismissButton = { GlassButton(onClick = { pending = null }) { Text("Cancel") } },
        ) {
            Text(
                text = if (removal == PendingRemoval.UNINSTALL) {
                    "The package is deleted from this computer. To use it again, import its .lti-mod.zip again."
                } else {
                    "The package stays on this computer but can no longer be turned on in a build. " +
                        "Archived packages are kept for builds that already used them."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        if (refs.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(Spacing.Compact),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(GlassShapes.MediumSmall)
                        .background(PluginThemeColors.TableRowAlt)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = PluginThemeColors.CardBorder,
                            shape = GlassShapes.HazeMediumSmall,
                        )
                        .padding(horizontal = Spacing.Medium, vertical = Spacing.SmallMedium),
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
                                painter = AppIcons.DeletePainterResource(),
                                contentDescription = "Uninstall",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Medium),
                            )
                            Column {
                                Text(
                                    text = "Uninstall",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    text = "Needed by ${refs.size} plugin(s) or workspace(s)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Icon(
                            painter = AppIcons.InfoPainterResource(),
                            contentDescription = "Uninstall blocked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                }

                Text(
                    text = "Needed by: ${refs.joinToString()}. Disable or remove those first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                GlassButton(
                    onClick = { pending = PendingRemoval.UNINSTALL },
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.HazeMediumSmall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Uninstall")
                }
                GlassButton(
                    onClick = { pending = PendingRemoval.ARCHIVE },
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.HazeMediumSmall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Archive")
                }
            }
        }
    }
}

@Composable
private fun InspectorRow(label: String, value: String, explanation: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (explanation != null) {
            Text(
                text = explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class PendingRemoval { UNINSTALL, ARCHIVE }
