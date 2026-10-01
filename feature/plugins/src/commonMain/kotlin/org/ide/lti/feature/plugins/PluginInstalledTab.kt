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
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.ideGeneralFilter
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
    activeWorkspaceId: String?,
    onSearchChange: (String) -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onOpenDetails: (InstalledRecord) -> Unit,
    onEnableWorkspace: (InstalledRecord, String) -> Unit,
    onDisableWorkspace: (InstalledRecord, String) -> Unit,
    onArchive: (InstalledRecord) -> Unit,
    onUninstall: (InstalledRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = records.filter { record ->
        searchQuery.isBlank() ||
            record.identity.publisher.contains(searchQuery, ignoreCase = true) ||
            record.identity.id.contains(searchQuery, ignoreCase = true) ||
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

                        Row(
                            modifier = Modifier
                                .clip(GlassShapes.MediumSmall)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .glassOutlineBorder(
                                    width = StrokeWidth.Hairline,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                                    shape = GlassShapes.HazeMediumSmall,
                                )
                                .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                        ) {
                            Icon(
                                painter = AppIcons.ideGeneralFilter(),
                                contentDescription = "Filter",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text(
                                text = "Compatible with ${activeWorkspaceId ?: "PQ84P01"} ▾",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

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
                    refs = selectedRecord?.let { references[it.identity.key] }.orEmpty(),
                    activeWorkspaceId = activeWorkspaceId,
                    filteredEmpty = filtered.isEmpty(),
                    searchQuery = searchQuery,
                    onOpenDetails = { selectedRecord?.let(onOpenDetails) },
                    onEnableWorkspace = { wsId -> selectedRecord?.let { onEnableWorkspace(it, wsId) } },
                    onDisableWorkspace = { wsId -> selectedRecord?.let { onDisableWorkspace(it, wsId) } },
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
                    itemsIndexed(filtered, key = { _, r -> r.identity.key }) { index, record ->
                        val isSelected = record.identity.key == selectedRecord?.identity?.key
                        val refs = references[record.identity.key].orEmpty()

                        InstalledTableRow(
                            index = index,
                            record = record,
                            isSelected = isSelected,
                            referencesCount = refs.size,
                            onClick = { onSelectKey(record.identity.key) },
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
            text = "Source",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableSourceColWidth),
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
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = painterResource(Res.drawable.ideNodesPlugin),
                contentDescription = "Plugin",
                tint = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(IconSize.Large),
            )
            Text(
                text = formatPluginDisplayName(record.identity.id),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = record.identity.version,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = codeFontFamily(),
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.PluginTableVersionColWidth),
        )

        Text(
            text = "Local",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ComponentSize.PluginTableSourceColWidth),
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
    activeWorkspaceId: String?,
    filteredEmpty: Boolean,
    searchQuery: String,
    onOpenDetails: () -> Unit,
    onEnableWorkspace: (String) -> Unit,
    onDisableWorkspace: (String) -> Unit,
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

                InstalledInspectorProperties(
                    record = record,
                    activeWorkspaceId = activeWorkspaceId,
                )

                GlassHorizontalDivider(specular = true)

                InstalledInspectorActions(
                    record = record,
                    refs = refs,
                    activeWorkspaceId = activeWorkspaceId,
                    onEnableWorkspace = onEnableWorkspace,
                    onDisableWorkspace = onDisableWorkspace,
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
                text = formatPluginDisplayName(record.identity.id),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
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
private fun InstalledInspectorProperties(
    record: InstalledRecord,
    activeWorkspaceId: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        InspectorRow(label = "Package identity", value = record.identity.key)
        InspectorRow(label = "License", value = "Apache-2.0")
        InspectorRow(
            label = "Scope",
            value = "Property edits",
            explanation = "Modify or inject system build properties during the build.",
        )

        val isEnabled = activeWorkspaceId != null && record.enabledInWorkspaces.contains(activeWorkspaceId)
        InspectorRow(
            label = "Status",
            value = if (isEnabled) {
                "Installed — enabled in this workspace"
            } else {
                "Installed — not enabled in this workspace"
            },
            explanation = if (isEnabled) {
                "The plugin is active for this workspace and will run in the next build."
            } else {
                "The plugin is available locally but is not active for this workspace. Enable it to include it in the next build."
            },
        )
    }
}

@Composable
private fun InstalledInspectorActions(
    record: InstalledRecord,
    refs: List<String>,
    activeWorkspaceId: String?,
    onEnableWorkspace: (String) -> Unit,
    onDisableWorkspace: (String) -> Unit,
    onArchive: () -> Unit,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        val isEnabled = activeWorkspaceId != null && record.enabledInWorkspaces.contains(activeWorkspaceId)
        GlassButton(
            onClick = {
                if (activeWorkspaceId != null) {
                    if (isEnabled) {
                        onDisableWorkspace(activeWorkspaceId)
                    } else {
                        onEnableWorkspace(activeWorkspaceId)
                    }
                }
            },
            enabled = !record.revoked && !record.archived,
            variant = if (isEnabled) GlassButtonVariant.Secondary else GlassButtonVariant.Primary,
            shape = GlassShapes.HazeMediumSmall,
            modifier = Modifier.fillMaxWidth().height(ComponentSize.PluginActionBtnHeight),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Icon(
                    painter = AppIcons.PlayPainterResource(),
                    contentDescription = "Play",
                    tint = BrandColors.OnLogoBadge,
                    modifier = Modifier.size(IconSize.Small),
                )
                Text(
                    text = if (isEnabled) "Disable for workspace" else "Enable for next build",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

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
                                    text = "Referenced by saved snapshots (${refs.size})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        Icon(
                            painter = AppIcons.InfoPainterResource(),
                            contentDescription = "Snapshot constraint",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(IconSize.Small),
                        )
                    }
                }

                Text(
                    text = "This plugin is referenced by saved snapshots and cannot be uninstalled.",
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
                    onClick = onUninstall,
                    variant = GlassButtonVariant.Standard,
                    shape = GlassShapes.HazeMediumSmall,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Uninstall")
                }
                GlassButton(
                    onClick = onArchive,
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
