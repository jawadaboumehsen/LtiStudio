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

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.navigation.GlassTab
import org.ide.lti.core.designsystem.component.navigation.GlassTabBar
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.plugin.key
import org.koin.compose.viewmodel.koinViewModel

@Composable
public fun PluginManagerScreen(
    initialDestination: String = "installed",
    initialWorkspaceId: String? = null,
    initialPackageId: String? = null,
    onNavigateBack: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PluginManagerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(initialDestination) {
        if (initialDestination.isNotBlank()) {
            viewModel.selectDestination(PluginDestination.fromId(initialDestination))
        }
    }

    LaunchedEffect(initialWorkspaceId) {
        if (!initialWorkspaceId.isNullOrBlank()) {
            viewModel.setActiveWorkspaceId(initialWorkspaceId)
        }
    }

    LaunchedEffect(initialPackageId) {
        if (!initialPackageId.isNullOrBlank()) {
            viewModel.selectPackage(initialPackageId)
        }
    }

    CompositionLocalProvider(LocalContentColor provides GlassTheme.colors.onGlass) {
        Column(modifier = modifier.fillMaxSize()) {
            PluginManagerTopBar(
                activeWorkspaceId = uiState.activeWorkspaceId,
                onNavigateBack = onNavigateBack,
                onOpenSettings = onOpenSettings,
            )

            uiState.notificationMessage?.let { msg ->
                PluginStatusBanner(
                    message = msg,
                    isError = false,
                    onDismiss = { viewModel.dismissNotification() },
                )
            }

            uiState.errorMessage?.let { msg ->
                PluginStatusBanner(
                    message = msg,
                    isError = true,
                    onDismiss = { viewModel.dismissNotification() },
                )
            }

            PluginNavigationControls(
                currentDestination = uiState.destination,
                searchQuery = uiState.searchQuery,
                onSelectDestination = { viewModel.selectDestination(it) },
                onSearchChange = { viewModel.setSearchQuery(it) },
            )

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (uiState.destination) {
                    PluginDestination.INSTALLED -> {
                        PluginInstalledTab(
                            records = uiState.installedPackages,
                            references = uiState.packageReferences,
                            searchQuery = uiState.searchQuery,
                            activeWorkspaceId = uiState.activeWorkspaceId,
                            onOpenDetails = { viewModel.openDetails(it) },
                            onEnableWorkspace = { record, wsId -> viewModel.enableForWorkspace(record, wsId) },
                            onDisableWorkspace = { record, wsId -> viewModel.disableForWorkspace(record, wsId) },
                            onArchive = { viewModel.archivePackage(it) },
                            onUninstall = { viewModel.uninstallPackage(it) },
                        )
                    }
                    PluginDestination.MARKETPLACE -> {
                        PluginMarketplaceTab(
                            source = uiState.marketplaceSource,
                            urlInput = uiState.marketplaceUrlInput,
                            entries = uiState.marketplaceEntries,
                            fetchResult = uiState.marketplaceFetchResult,
                            isLoading = uiState.isMarketplaceLoading,
                            installState = uiState.installState,
                            searchQuery = uiState.searchQuery,
                            onUrlInputChange = { viewModel.setMarketplaceUrlInput(it) },
                            onConfigureSource = { label, url -> viewModel.configureMarketplaceSource(label, url) },
                            onRefresh = { viewModel.fetchMarketplace(allowCache = false) },
                            onNavigateToImport = { viewModel.selectDestination(PluginDestination.IMPORT) },
                            onInstall = { entry, trust -> viewModel.startInstallFromMarketplace(entry, trust) },
                        )
                    }
                    PluginDestination.IMPORT -> {
                        PluginImportTab(
                            importPath = uiState.importPathInput,
                            quarantinedPath = uiState.importQuarantinedPath,
                            inspection = uiState.importInspection,
                            isLegacyUnsupported = uiState.isLegacyUnsupportedFormat,
                            legacyFormatName = uiState.legacyFormatName,
                            installState = uiState.installState,
                            onImportPathChange = { viewModel.setImportPathInput(it) },
                            onInspectPath = { viewModel.inspectImportPath(it) },
                            onDismissLegacyWarning = { viewModel.dismissLegacyFormatWarning() },
                            onConfirmInstall = { viewModel.confirmImportInstall(it) },
                        )
                    }
                    PluginDestination.AUTHOR_TOOLS -> {
                        PluginAuthorToolsTab(
                            projectPath = uiState.authorProjectPathInput,
                            trustCheck = uiState.authorTrustCheck,
                            selectedTask = uiState.authorSelectedTask,
                            taskResult = uiState.authorTaskResult,
                            isRunningTask = uiState.isAuthorTaskRunning,
                            onProjectPathChange = { viewModel.setAuthorProjectPathInput(it) },
                            onCheckProject = { viewModel.checkAuthorProject(it) },
                            onApproveProject = { viewModel.approveAuthorProject() },
                            onRevokeProject = { viewModel.revokeAuthorProject() },
                            onSelectTask = { viewModel.selectAuthorTask(it) },
                            onRunTask = { viewModel.runAuthorTask(it) },
                        )
                    }
                }
            }
        }

        if (uiState.isDetailsOpen && uiState.detailsRecord != null) {
            val rec = uiState.detailsRecord!!
            PluginDetailsDialog(
                record = rec,
                references = uiState.packageReferences[rec.identity.key] ?: emptyList(),
                onDismiss = { viewModel.closeDetails() },
            )
        }
    }
}

@Composable
private fun PluginManagerTopBar(activeWorkspaceId: String?, onNavigateBack: () -> Unit, onOpenSettings: () -> Unit) {
    GlassCard(modifier = Modifier.fillMaxWidth().padding(Spacing.Small)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlassButton(onClick = onNavigateBack, variant = GlassButtonVariant.Standard) {
                    Text("← Back")
                }
                Text(
                    text = "Plugin Manager",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = GlassTheme.colors.onGlass,
                )
                activeWorkspaceId?.let { wsId ->
                    GlassChip(label = "Workspace: $wsId")
                }
            }

            GlassButton(onClick = onOpenSettings, variant = GlassButtonVariant.Standard) {
                Text("Settings")
            }
        }
    }
}

@Composable
private fun PluginStatusBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    GlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) GlassTheme.colors.glassHighlight else GlassTheme.colors.glassAccent,
            )
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Text) {
                Text("Dismiss")
            }
        }
    }
}

@Composable
private fun PluginNavigationControls(
    currentDestination: PluginDestination,
    searchQuery: String,
    onSelectDestination: (PluginDestination) -> Unit,
    onSearchChange: (String) -> Unit,
) {
    val tabs = listOf(
        GlassTab(id = PluginDestination.INSTALLED.id, title = PluginDestination.INSTALLED.title),
        GlassTab(id = PluginDestination.MARKETPLACE.id, title = PluginDestination.MARKETPLACE.title),
        GlassTab(id = PluginDestination.IMPORT.id, title = PluginDestination.IMPORT.title),
        GlassTab(id = PluginDestination.AUTHOR_TOOLS.id, title = PluginDestination.AUTHOR_TOOLS.title),
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassTabBar(
            tabs = tabs,
            selectedId = currentDestination.id,
            onTabSelected = { id -> onSelectDestination(PluginDestination.fromId(id)) },
        )

        Spacer(modifier = Modifier.weight(1f))

        GlassTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Search plugins...",
        )
    }
}
