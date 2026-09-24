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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.layout.GlobalAppDestination
import org.ide.lti.core.designsystem.component.layout.GlobalAppTopBar
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.PluginThemeColors
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.feature.plugins.components.PluginSidebar
import org.koin.compose.viewmodel.koinViewModel

@Composable
public fun PluginManagerScreen(
    initialDestination: String = "installed",
    initialWorkspaceId: String? = null,
    initialPackageId: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToSetup: () -> Unit = {},
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

    val actions = remember(viewModel, onNavigateBack, onOpenSettings) {
        PluginManagerActions(
            onNavigateBack = onNavigateBack,
            onOpenSettings = onOpenSettings,
            onSelectDestination = { viewModel.selectDestination(it) },
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onOpenDetails = { viewModel.openDetails(it) },
            onCloseDetails = { viewModel.closeDetails() },
            onEnableWorkspace = { record, wsId -> viewModel.enableForWorkspace(record, wsId) },
            onDisableWorkspace = { record, wsId -> viewModel.disableForWorkspace(record, wsId) },
            onArchivePackage = { viewModel.archivePackage(it) },
            onUninstallPackage = { viewModel.uninstallPackage(it) },
            onMarketplaceUrlChange = { viewModel.setMarketplaceUrlInput(it) },
            onConfigureMarketplaceSource = { label, url -> viewModel.configureMarketplaceSource(label, url) },
            onRefreshMarketplace = { viewModel.fetchMarketplace(allowCache = false) },
            onInstallMarketplace = { entry, trust -> viewModel.startInstallFromMarketplace(entry, trust) },
            onImportPathChange = { viewModel.setImportPathInput(it) },
            onInspectImportPath = { viewModel.inspectImportPath(it) },
            onDismissLegacyWarning = { viewModel.dismissLegacyFormatWarning() },
            onConfirmImportInstall = { viewModel.confirmImportInstall(it) },
            onAuthorProjectPathChange = { viewModel.setAuthorProjectPathInput(it) },
            onCheckAuthorProject = { viewModel.checkAuthorProject(it) },
            onApproveAuthorProject = { viewModel.approveAuthorProject() },
            onRevokeAuthorProject = { viewModel.revokeAuthorProject() },
            onSelectAuthorTask = { viewModel.selectAuthorTask(it) },
            onRunAuthorTask = { viewModel.runAuthorTask(it) },
            onDismissNotification = { viewModel.dismissNotification() },
        )
    }

    PluginManagerContent(
        state = uiState,
        actions = actions,
        onNavigateToSetup = onNavigateToSetup,
        modifier = modifier,
    )
}

@Composable
public fun PluginManagerContent(
    state: PluginManagerUiState,
    actions: PluginManagerActions = PluginManagerActions(),
    onNavigateToSetup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val onSelectGlobalDestination = remember(onNavigateToSetup, actions) {
        { destination: GlobalAppDestination -> resolveGlobalDestination(destination, onNavigateToSetup, actions) }
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clip(GlassShapes.Panel)
                .glassOutlineBorder(
                    width = StrokeWidth.Hairline,
                    color = MaterialTheme.colorScheme.outline,
                    shape = GlassShapes.Panel,
                ),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
            ) {
                GlobalAppTopBar(
                    selectedDestination = GlobalAppDestination.Workspace,
                    onSelectDestination = onSelectGlobalDestination,
                    tagPrefix = "TopBar",
                )

                // Optional Notification or Error Status Banner
                state.notificationMessage?.let { msg ->
                    PluginStatusBanner(
                        message = msg,
                        isError = false,
                        onDismiss = actions.onDismissNotification,
                    )
                }

                state.errorMessage?.let { msg ->
                    PluginStatusBanner(
                        message = msg,
                        isError = true,
                        onDismiss = actions.onDismissNotification,
                    )
                }

                // Main IDE Content Area: Persistent Left Rail + Destination Content
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) {
                    // Persistent Sidebar Navigation Rail
                    PluginSidebar(
                        currentDestination = state.destination,
                        onSelectDestination = actions.onSelectDestination,
                        onOpenSettings = actions.onOpenSettings,
                    )

                    // Specular Vertical Divider
                    GlassVerticalDivider(specular = true)

                    // Destination Content Pane
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        when (state.destination) {
                            PluginDestination.INSTALLED -> {
                                PluginInstalledTab(
                                    records = state.installedPackages,
                                    references = state.packageReferences,
                                    searchQuery = state.searchQuery,
                                    activeWorkspaceId = state.activeWorkspaceId,
                                    onSearchChange = actions.onSearchQueryChange,
                                    onNavigateToImport = { actions.onSelectDestination(PluginDestination.IMPORT) },
                                    onOpenDetails = actions.onOpenDetails,
                                    onEnableWorkspace = actions.onEnableWorkspace,
                                    onDisableWorkspace = actions.onDisableWorkspace,
                                    onArchive = actions.onArchivePackage,
                                    onUninstall = actions.onUninstallPackage,
                                )
                            }
                            PluginDestination.MARKETPLACE -> {
                                PluginMarketplaceTab(
                                    source = state.marketplaceSource,
                                    urlInput = state.marketplaceUrlInput,
                                    entries = state.marketplaceEntries,
                                    fetchResult = state.marketplaceFetchResult,
                                    isLoading = state.isMarketplaceLoading,
                                    installState = state.installState,
                                    searchQuery = state.searchQuery,
                                    onSearchChange = actions.onSearchQueryChange,
                                    onUrlInputChange = actions.onMarketplaceUrlChange,
                                    onConfigureSource = actions.onConfigureMarketplaceSource,
                                    onRefresh = actions.onRefreshMarketplace,
                                    onNavigateToImport = { actions.onSelectDestination(PluginDestination.IMPORT) },
                                    onInstall = actions.onInstallMarketplace,
                                )
                            }
                            PluginDestination.IMPORT -> {
                                PluginImportTab(
                                    importPath = state.importPathInput,
                                    quarantinedPath = state.importQuarantinedPath,
                                    inspection = state.importInspection,
                                    isLegacyUnsupported = state.isLegacyUnsupportedFormat,
                                    legacyFormatName = state.legacyFormatName,
                                    installState = state.installState,
                                    onImportPathChange = actions.onImportPathChange,
                                    onInspectPath = actions.onInspectImportPath,
                                    onDismissLegacyWarning = actions.onDismissLegacyWarning,
                                    onConfirmInstall = actions.onConfirmImportInstall,
                                )
                            }
                            PluginDestination.AUTHOR_TOOLS -> {
                                PluginAuthorToolsTab(
                                    projectPath = state.authorProjectPathInput,
                                    trustCheck = state.authorTrustCheck,
                                    selectedTask = state.authorSelectedTask,
                                    taskResult = state.authorTaskResult,
                                    isRunningTask = state.isAuthorTaskRunning,
                                    onProjectPathChange = actions.onAuthorProjectPathChange,
                                    onCheckProject = actions.onCheckAuthorProject,
                                    onApproveProject = actions.onApproveAuthorProject,
                                    onRevokeProject = actions.onRevokeAuthorProject,
                                    onSelectTask = actions.onSelectAuthorTask,
                                    onRunTask = actions.onRunAuthorTask,
                                )
                            }
                        }
                    }
                }
            }

            // Details Dialog if open
            if (state.isDetailsOpen && state.detailsRecord != null) {
                val rec = state.detailsRecord
                PluginDetailsDialog(
                    record = rec,
                    references = state.packageReferences[rec.identity.key] ?: emptyList(),
                    onDismiss = actions.onCloseDetails,
                )
            }
        }
    }
}

private fun resolveGlobalDestination(
    destination: GlobalAppDestination,
    onNavigateToSetup: () -> Unit,
    actions: PluginManagerActions,
) {
    when (destination) {
        GlobalAppDestination.Setup -> onNavigateToSetup()
        GlobalAppDestination.Workspace -> actions.onNavigateBack()
        GlobalAppDestination.Settings -> actions.onOpenSettings()
    }
}

@Composable
private fun PluginStatusBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall)
            .clip(GlassShapes.MediumSmall)
            .background(PluginThemeColors.CardBackground)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = PluginThemeColors.CardBorder,
                shape = GlassShapes.MediumSmall,
            ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) GlassTheme.diagnosticColors.error else GlassTheme.diagnosticColors.success,
            )
            GlassButton(onClick = onDismiss, variant = GlassButtonVariant.Text) {
                Text("Dismiss")
            }
        }
    }
}
