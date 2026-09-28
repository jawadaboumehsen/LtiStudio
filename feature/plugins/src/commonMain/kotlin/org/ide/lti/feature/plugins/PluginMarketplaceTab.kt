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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.display.GlassHorizontalDivider
import org.ide.lti.core.designsystem.component.feedback.GlassDialog
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldLeadingSlot
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideToolwindowsRepositories
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.icon.AppIconsRomStages
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.BrandColors
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.feature.plugins.components.PluginFooterBar
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun PluginMarketplaceTab(
    source: IndexSource?,
    urlInput: String,
    entries: List<IndexEntry>,
    fetchResult: IndexFetchResult?,
    isLoading: Boolean,
    installState: InstallState?,
    searchQuery: String,
    onSearchChange: (String) -> Unit = {},
    onUrlInputChange: (String) -> Unit,
    onConfigureSource: (String, String) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToImport: () -> Unit,
    onInstall: (IndexEntry, TrustDecision) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingInstallEntry by remember { mutableStateOf<IndexEntry?>(null) }
    var showConfigureModal by remember { mutableStateOf(false) }

    val filtered = entries.filter {
        searchQuery.isBlank() ||
            it.publisher.contains(searchQuery, ignoreCase = true) ||
            it.id.contains(searchQuery, ignoreCase = true) ||
            it.targetSummary.contains(searchQuery, ignoreCase = true)
    }

    var selectedEntryId by remember { mutableStateOf<String?>(null) }

    val selectedEntry = resolveMarketplaceSelection(filtered, selectedEntryId)

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = Spacing.Large, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            MarketplaceHeader(
                source = source,
                fetchResult = fetchResult,
                isLoading = isLoading,
                onRefresh = onRefresh,
                onOpenConfigure = { showConfigureModal = true },
                searchQuery = searchQuery,
                onSearchChange = onSearchChange,
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                MarketplaceMasterTable(
                    filtered = filtered,
                    selectedEntry = selectedEntry,
                    searchQuery = searchQuery,
                    onSelectEntry = { selectedEntryId = it.id },
                    onNavigateToImport = onNavigateToImport,
                    modifier = Modifier.weight(1f),
                )

                MarketplaceInspectorPanel(
                    entry = selectedEntry,
                    fetchResult = fetchResult,
                    filteredEmpty = filtered.isEmpty(),
                    searchQuery = searchQuery,
                    installState = installState,
                    onInstallClick = { pendingInstallEntry = selectedEntry },
                    modifier = Modifier.width(ComponentSize.PluginInspectorWidth),
                )
            }
        }

        PluginFooterBar(
            message = "Connect to refresh trust and revocation information.",
            trailingContent = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    GlassButton(
                        onClick = { showConfigureModal = true },
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
                                painter = AppIcons.SettingsPainterResource(),
                                contentDescription = "Configure sources",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text("Configure sources")
                        }
                    }
                    GlassButton(
                        onClick = onNavigateToImport,
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
                                painter = AppIcons.FolderPainterResource(),
                                contentDescription = "Import local package",
                                tint = BrandColors.OnLogoBadge,
                                modifier = Modifier.size(IconSize.Small),
                            )
                            Text("Import local package")
                        }
                    }
                }
            },
        )
    }

    if (showConfigureModal) {
        ConfigureSourceDialog(
            url = urlInput,
            onUrlChange = onUrlInputChange,
            onDismiss = { showConfigureModal = false },
            onConfirm = {
                onConfigureSource(source?.label ?: "Custom", urlInput)
                showConfigureModal = false
            },
        )
    }

    pendingInstallEntry?.let { entry ->
        MarketplaceTrustDialog(
            entry = entry,
            onDismiss = { pendingInstallEntry = null },
            onConfirmTrust = { decision ->
                onInstall(entry, decision)
                pendingInstallEntry = null
            },
        )
    }
}

@Composable
private fun MarketplaceHeader(
    source: IndexSource?,
    fetchResult: IndexFetchResult?,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onOpenConfigure: () -> Unit,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
            Text(
                text = "Marketplace",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Find and install operation packages for your project.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Row(
                    modifier = Modifier
                        .clip(GlassShapes.Small)
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .glassOutlineBorder(
                            width = StrokeWidth.Hairline,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = AlphaTokens.Faint),
                            shape = GlassShapes.Small,
                        )
                        .clickable(role = Role.Button, onClick = onOpenConfigure)
                        .padding(horizontal = Spacing.SmallMedium, vertical = Spacing.Compact),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Compact),
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.ideToolwindowsRepositories),
                        contentDescription = "Repository",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(IconSize.Small),
                    )
                    Text(
                        text = "${source?.label ?: "No catalog configured"} ▾",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val cacheAgeText = when (fetchResult) {
                    is IndexFetchResult.Cached -> {
                        val elapsedMs = System.currentTimeMillis() - fetchResult.observedAtEpochMs
                        val ageSec = (elapsedMs / 1000).coerceAtLeast(0)
                        if (ageSec < 60) "Cached ${ageSec}s ago" else "Cached ${ageSec / 60}m ago"
                    }
                    is IndexFetchResult.Fetched -> "Updated now"
                    else -> "Not loaded"
                }
                Text(
                    text = cacheAgeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.clickable(onClick = onRefresh),
                )

                if (fetchResult is IndexFetchResult.Cached) {
                    GlassChip(label = "Offline")
                } else if (isLoading) {
                    GlassChip(label = "Fetching...")
                }
            }

            GlassTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = "Search packages...",
                leading = TextFieldLeadingSlot.Icon(AppIcons.Search),
                modifier = Modifier.width(ComponentSize.PluginInspectorWidth),
            )
        }
    }
}

@Composable
private fun MarketplaceMasterTable(
    filtered: List<IndexEntry>,
    selectedEntry: IndexEntry?,
    searchQuery: String,
    onSelectEntry: (IndexEntry) -> Unit,
    onNavigateToImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    Text(
                        text = if (searchQuery.isBlank()) {
                            "No packages available in this source index."
                        } else {
                            "No packages match '$searchQuery'."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GlassButton(
                        onClick = onNavigateToImport,
                        variant = GlassButtonVariant.Standard,
                        shape = GlassShapes.HazeMediumSmall,
                        minHeight = ComponentSize.PluginActionBtnCompactHeight,
                    ) {
                        Text("Import local package (.ltimod)")
                    }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                MarketplaceTableHeader()

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(
                        items = filtered,
                        key = { _, e -> "${e.publisher}:${e.id}:${e.version}" },
                    ) { _, entry ->
                        val isSelected = entry.id == selectedEntry?.id
                        MarketplaceTableRow(
                            entry = entry,
                            isSelected = isSelected,
                            onClick = { onSelectEntry(entry) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceTableHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(PluginThemeColors.HeaderBackground)
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Package",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Publisher",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.ButtonMinWidth * 2),
        )
        Text(
            text = "SDK compatibility",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(ComponentSize.ButtonMinWidth * 2),
        )
        Spacer(modifier = Modifier.width(ComponentSize.PluginActionBtnCompactWidth + Spacing.Medium))
    }
}

@Composable
private fun getMarketplaceRowIcon(id: String): Painter = when {
    id.contains("hook", ignoreCase = true) || id.contains("framework", ignoreCase = true) ->
        AppIconsRomStages.assemble()
    id.contains("resource", ignoreCase = true) || id.contains("overlay", ignoreCase = true) ->
        AppIconsRomStages.patches()
    else -> AppIcons.FilePainterResource()
}

@Composable
private fun MarketplaceTableRow(
    entry: IndexEntry,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowBg = if (isSelected) PluginThemeColors.SelectedRowBackground else Color.Transparent
    val contentTint = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (isSelected) BrandColors.OnLogoBadge else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isSelected) {
        BrandColors.OnLogoBadge.copy(alpha = AlphaTokens.Hover)
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
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

    val rowIcon = getMarketplaceRowIcon(entry.id)
    val sdkRange = entry.sdkApiRange.ifBlank { "Not declared" }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = ComponentSize.PluginTableRowHeight)
            .clip(GlassShapes.Small)
            .then(borderModifier)
            .background(rowBg)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { selected = isSelected }
            .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Icon(
                painter = rowIcon,
                contentDescription = "Package",
                tint = contentTint,
                modifier = Modifier.size(IconSize.Large),
            )
            Column {
                Text(
                    text = formatPluginDisplayName(entry.id),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = titleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = entry.targetSummary.ifBlank { "No package summary provided." },
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = entry.publisher,
            style = MaterialTheme.typography.bodySmall,
            color = contentTint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ComponentSize.ButtonMinWidth * 2),
        )

        Text(
            text = sdkRange,
            style = MaterialTheme.typography.bodySmall,
            color = contentTint,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(ComponentSize.ButtonMinWidth * 2),
        )

        GlassButton(
            onClick = onClick,
            variant = GlassButtonVariant.Standard,
            shape = GlassShapes.HazeMediumSmall,
            minWidth = ComponentSize.PluginActionBtnCompactWidth,
            minHeight = ComponentSize.PluginActionBtnCompactHeight,
            contentPadding = PaddingValues(
                horizontal = Spacing.SmallMedium,
                vertical = Spacing.ExtraSmall,
            ),
        ) {
            Text(
                text = "View details",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = titleColor,
            )
        }
    }
}

@Composable
private fun MarketplaceInspectorHeader(entry: IndexEntry) {
    Row(
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
                contentDescription = "Package",
                tint = BrandColors.OnLogoBadge,
                modifier = Modifier.size(IconSize.Large),
            )
        }

        Column {
            Text(
                text = formatPluginDisplayName(entry.id),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.publisher,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MarketplaceUnverifiedWarningCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.WarningBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.WarningBorder,
                shape = GlassShapes.HazeMediumSmall,
            )
            .padding(Spacing.Medium),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = AppIcons.InfoPainterResource(),
                contentDescription = "Verification warning",
                tint = PluginThemeColors.WarningText,
                modifier = Modifier
                    .size(IconSize.Small)
                    .padding(top = Spacing.ExtraExtraSmall),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall)) {
                Text(
                    text = "Requires source verification before install.",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = PluginThemeColors.WarningText,
                )
                Text(
                    text = "This package has not been verified in the current offline state. " +
                        "Connect to refresh trust and revocation information.",
                    style = MaterialTheme.typography.labelSmall,
                    color = PluginThemeColors.WarningBody,
                )
            }
        }
    }
}

@Composable
private fun MarketplaceInspectorPanel(
    entry: IndexEntry?,
    fetchResult: IndexFetchResult?,
    filteredEmpty: Boolean,
    searchQuery: String,
    installState: InstallState?,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isInstalling = installState is InstallState.Downloading || installState is InstallState.Quarantined
    val sourceVerified = marketplaceSourceIsVerified(fetchResult)

    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(GlassShapes.Medium)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.Medium,
            ),
    ) {
        if (entry == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(Spacing.Large),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (filteredEmpty && searchQuery.isNotBlank()) {
                        "No packages match '$searchQuery'."
                    } else {
                        "Select a package to inspect details."
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
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                MarketplaceInspectorHeader(entry = entry)

                Text(
                    text = entry.targetSummary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                GlassHorizontalDivider(specular = true)

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    MarketplacePropertyRow(
                        label = "SDK compatibility",
                        value = entry.sdkApiRange.ifBlank { "Not declared" },
                    )
                    GlassHorizontalDivider(specular = true)
                    MarketplacePropertyRow(
                        label = "Content digest",
                        value = entry.contentDigest.ifBlank { "Not declared" },
                    )
                    GlassHorizontalDivider(specular = true)
                    MarketplacePropertyRow(
                        label = "Signature identity",
                        value = entry.signatureIdentity ?: "Unsigned",
                    )
                    GlassHorizontalDivider(specular = true)
                    MarketplacePropertyRow(
                        label = "Revocation status",
                        value = if (entry.revoked) "Revoked" else "Not marked revoked",
                    )
                }

                if (!sourceVerified) {
                    MarketplaceUnverifiedWarningCard()
                }

                if (sourceVerified) {
                    Spacer(modifier = Modifier.weight(1f))

                    GlassButton(
                        onClick = onInstallClick,
                        enabled = !isInstalling,
                        variant = GlassButtonVariant.Primary,
                        shape = GlassShapes.HazeMediumSmall,
                        minHeight = ComponentSize.PluginActionBtnHeight,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (isInstalling) "Installing..." else "Install package")
                    }
                }
            }
        }
    }
}

@Composable
private fun MarketplacePropertyRow(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
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
private fun ConfigureSourceDialog(
    url: String,
    onUrlChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    GlassDialog(
        title = "Configure Marketplace Source",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(onClick = onConfirm, variant = GlassButtonVariant.Primary) {
                Text("Save and Fetch")
            }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) {
                Text("Cancel")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            Text(
                text = "Marketplace index repository must be a strictly secure HTTPS endpoint.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GlassTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = "https://example.com/plugins/index.json",
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun MarketplaceTrustDialog(entry: IndexEntry, onDismiss: () -> Unit, onConfirmTrust: (TrustDecision) -> Unit) {
    var trustDecision by remember { mutableStateOf(TrustDecision.TRUSTED_UNVERIFIED) }

    GlassDialog(
        title = "Install Package: ${entry.id}",
        onDismissRequest = onDismiss,
        confirmButton = {
            GlassButton(
                onClick = { onConfirmTrust(trustDecision) },
                variant = GlassButtonVariant.Primary,
            ) {
                Text("Confirm and Install")
            }
        },
        dismissButton = {
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Standard) {
                Text("Cancel")
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            Text(
                text = "Publisher: ${entry.publisher}\nVersion: ${entry.version}\nTarget: ${entry.targetSummary}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                val sigVariant = if (trustDecision == TrustDecision.TRUSTED_SIGNATURE) {
                    GlassButtonVariant.Primary
                } else {
                    GlassButtonVariant.Standard
                }
                val unverVariant = if (trustDecision == TrustDecision.TRUSTED_UNVERIFIED) {
                    GlassButtonVariant.Primary
                } else {
                    GlassButtonVariant.Standard
                }

                GlassButton(
                    onClick = { trustDecision = TrustDecision.TRUSTED_SIGNATURE },
                    variant = sigVariant,
                ) {
                    Text("Trust Signature")
                }
                GlassButton(
                    onClick = { trustDecision = TrustDecision.TRUSTED_UNVERIFIED },
                    variant = unverVariant,
                ) {
                    Text("Trust Unverified")
                }
            }
        }
    }
}
