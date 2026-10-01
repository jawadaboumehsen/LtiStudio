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

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.plugin.AuthorProject
import org.ide.lti.core.domain.plugin.AuthorProjectTrustUseCase
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFailure
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.IndexSource
import org.ide.lti.core.domain.plugin.InstallPluginUseCase
import org.ide.lti.core.domain.plugin.InstallRequest
import org.ide.lti.core.domain.plugin.InstallState
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.LifecycleOutcome
import org.ide.lti.core.domain.plugin.MarketplaceIndexPort
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.PluginLifecycleUseCase
import org.ide.lti.core.domain.plugin.TrustCheck
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.domain.workspace.WorkspaceManager

public enum class PluginDestination(public val id: String, public val title: String) {
    INSTALLED("installed", "Installed"),
    MARKETPLACE("marketplace", "Marketplace"),
    IMPORT("import", "Import"),
    AUTHOR_TOOLS("author_tools", "Author Tools"),
    ;

    public companion object {
        public fun fromId(id: String): PluginDestination =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: INSTALLED
    }
}

public data class PluginManagerUiState(
    val destination: PluginDestination = PluginDestination.INSTALLED,
    val activeWorkspaceId: String? = null,
    val searchQuery: String = "",
    val installedPackages: List<InstalledRecord> = emptyList(),
    val packageReferences: Map<String, List<String>> = emptyMap(),
    val selectedPackageKey: String? = null,
    val detailsRecord: InstalledRecord? = null,
    val isDetailsOpen: Boolean = false,
    val marketplaceSource: IndexSource? = null,
    val marketplaceUrlInput: String = "",
    val marketplaceEntries: List<IndexEntry> = emptyList(),
    val marketplaceFetchResult: IndexFetchResult? = null,
    val isMarketplaceLoading: Boolean = false,
    val installingPackageLabel: String? = null,
    val installState: InstallState? = null,
    val importPathInput: String = "",
    val importQuarantinedPath: String? = null,
    val importInspection: PackageInspection? = null,
    val isLegacyUnsupportedFormat: Boolean = false,
    val legacyFormatName: String? = null,
    val authorProjectPathInput: String = "",
    val authorTrustCheck: TrustCheck? = null,
    val authorSelectedTask: String = "testMod",
    val authorTaskResult: AuthorTaskResult? = null,
    val isAuthorTaskRunning: Boolean = false,
    val notificationMessage: String? = null,
    val errorMessage: String? = null,
)

@Suppress("TooManyFunctions") // unified state holder for installed, marketplace, import, and author tools
public class PluginManagerViewModel(
    private val installPluginUseCase: InstallPluginUseCase,
    private val pluginLifecycleUseCase: PluginLifecycleUseCase,
    private val authorProjectTrustUseCase: AuthorProjectTrustUseCase,
    private val marketplaceIndexPort: MarketplaceIndexPort,
    private val pluginInstallPort: PluginInstallPort,
    private val workspaceManager: WorkspaceManager? = null,
    initialDestination: String = "installed",
    initialWorkspaceId: String? = null,
    initialPackageId: String? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PluginManagerUiState(
            destination = PluginDestination.fromId(initialDestination),
            activeWorkspaceId = initialWorkspaceId,
            selectedPackageKey = initialPackageId,
        ),
    )
    public val uiState: StateFlow<PluginManagerUiState> = _uiState.asStateFlow()

    init {
        refreshInstalled()
        observeCurrentWorkspace()
    }

    private fun observeCurrentWorkspace() {
        if (workspaceManager != null) {
            viewModelScope.launch {
                workspaceManager.currentWorkspace.collect { ws ->
                    if (ws != null && _uiState.value.activeWorkspaceId == null) {
                        _uiState.update { it.copy(activeWorkspaceId = ws.id) }
                    }
                }
            }
        }
    }

    public fun selectDestination(destination: PluginDestination) {
        _uiState.update { it.copy(destination = destination, errorMessage = null, notificationMessage = null) }
    }

    public fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    public fun setActiveWorkspaceId(workspaceId: String) {
        _uiState.update { it.copy(activeWorkspaceId = workspaceId) }
    }

    public fun selectPackage(packageKey: String?) {
        _uiState.update { it.copy(selectedPackageKey = packageKey) }
    }

    public fun openDetails(record: InstalledRecord) {
        _uiState.update { it.copy(detailsRecord = record, isDetailsOpen = true) }
    }

    public fun closeDetails() {
        _uiState.update { it.copy(isDetailsOpen = false, detailsRecord = null) }
    }

    public fun dismissNotification() {
        _uiState.update { it.copy(notificationMessage = null, errorMessage = null) }
    }

    public fun refreshInstalled() {
        viewModelScope.launch {
            val records = pluginInstallPort.installed()
            val refs = computePackageReferences(records)
            _uiState.update {
                it.copy(installedPackages = records, packageReferences = refs)
            }
        }
    }

    public fun enableForWorkspace(record: InstalledRecord, workspaceId: String) {
        if (!pluginLifecycleUseCase.canEnable(record)) {
            val msg = "Package '${record.identity.key}' cannot be enabled (revoked or archived)"
            _uiState.update { it.copy(errorMessage = msg) }
            return
        }
        viewModelScope.launch {
            val updated = record.copy(enabledInWorkspaces = (record.enabledInWorkspaces + workspaceId).distinct())
            pluginInstallPort.updateRecord(updated)
            refreshInstalled()
            _uiState.update { it.copy(notificationMessage = "Enabled '${record.identity.key}' for workspace") }
        }
    }

    public fun disableForWorkspace(record: InstalledRecord, workspaceId: String) {
        viewModelScope.launch {
            val updated = record.copy(enabledInWorkspaces = record.enabledInWorkspaces - workspaceId)
            pluginInstallPort.updateRecord(updated)
            refreshInstalled()
            _uiState.update { it.copy(notificationMessage = "Disabled '${record.identity.key}' for workspace") }
        }
    }

    public fun archivePackage(record: InstalledRecord) {
        viewModelScope.launch {
            when (val outcome = pluginLifecycleUseCase.archive(record.identity)) {
                is LifecycleOutcome.Applied -> {
                    refreshInstalled()
                    _uiState.update { it.copy(notificationMessage = "Archived '${record.identity.key}'") }
                }
                is LifecycleOutcome.Refused -> {
                    val err = outcome.report.errors.firstOrNull()?.message ?: "Archive refused"
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    public fun uninstallPackage(record: InstalledRecord) {
        viewModelScope.launch {
            val references = _uiState.value.packageReferences[record.identity.key] ?: emptyList()
            when (val outcome = pluginLifecycleUseCase.uninstall(record.identity, references)) {
                is LifecycleOutcome.Applied -> {
                    refreshInstalled()
                    _uiState.update { it.copy(notificationMessage = "Uninstalled '${record.identity.key}'") }
                }
                is LifecycleOutcome.Refused -> {
                    val err = outcome.report.errors.firstOrNull()?.message ?: "Uninstall refused"
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    public fun setMarketplaceUrlInput(url: String) {
        _uiState.update { it.copy(marketplaceUrlInput = url) }
    }

    public fun configureMarketplaceSource(label: String, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://", ignoreCase = true)) {
            _uiState.update {
                it.copy(
                    marketplaceFetchResult = IndexFetchResult.Failed(IndexFailure.InsecureSource),
                    errorMessage = "Marketplace source must be an HTTPS URL",
                )
            }
            return
        }
        val source = IndexSource(label = label.ifBlank { "Custom Repository" }, url = trimmed)
        _uiState.update { it.copy(marketplaceSource = source, marketplaceUrlInput = trimmed) }
        fetchMarketplace(source = source, allowCache = true)
    }

    public fun fetchMarketplace(source: IndexSource? = _uiState.value.marketplaceSource, allowCache: Boolean = true) {
        if (source == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMarketplaceLoading = true, errorMessage = null) }
            val result = marketplaceIndexPort.fetch(source, allowCache)
            val entries = when (result) {
                is IndexFetchResult.Fetched -> result.doc.entries
                is IndexFetchResult.Cached -> result.doc.entries
                is IndexFetchResult.Failed -> emptyList()
            }
            _uiState.update {
                it.copy(
                    isMarketplaceLoading = false,
                    marketplaceFetchResult = result,
                    marketplaceEntries = entries,
                )
            }
        }
    }

    public fun startInstallFromMarketplace(entry: IndexEntry, trust: TrustDecision) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    installingPackageLabel = "${entry.publisher}:${entry.id}",
                    installState = InstallState.SourceSelected,
                )
            }
            val request = InstallRequest(
                sourceLabel = entry.packageUrl,
                expectedDigest = entry.contentDigest,
                trust = trust,
            )
            installPluginUseCase(request).collect { state ->
                _uiState.update { it.copy(installState = state) }
                if (state is InstallState.Installed) {
                    refreshInstalled()
                    val label = "${state.record.publisher}:${state.record.id}"
                    _uiState.update { it.copy(notificationMessage = "Installed '$label'") }
                } else if (state is InstallState.Failed) {
                    val err = state.report.errors.firstOrNull()?.message ?: "Installation failed"
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    public fun setImportPathInput(path: String) {
        _uiState.update {
            it.copy(
                importPathInput = path,
                importQuarantinedPath = null,
                importInspection = null,
                isLegacyUnsupportedFormat = false,
                legacyFormatName = null,
                installState = null,
            )
        }
    }

    public fun inspectImportPath(path: String) {
        val trimmed = path.trim()
        if (trimmed.isBlank()) return

        val lower = trimmed.lowercase()
        val isLegacy = lower.endsWith(".sh") ||
            lower.endsWith(".bash") ||
            lower.endsWith(".patch") ||
            lower.contains("customize.sh") ||
            (!lower.endsWith(".zip") && !lower.endsWith(".lti-mod.zip"))

        if (isLegacy) {
            val fileName = trimmed.substringAfterLast('/').substringAfterLast('\\')
            _uiState.update {
                it.copy(
                    isLegacyUnsupportedFormat = true,
                    legacyFormatName = fileName,
                    importInspection = null,
                    importQuarantinedPath = null,
                )
            }
            return
        }

        _uiState.update { it.copy(isLegacyUnsupportedFormat = false) }
        viewModelScope.launch {
            val quarantined = pluginInstallPort.quarantine(trimmed)
            if (quarantined == null) {
                _uiState.update { it.copy(errorMessage = "Could not access or quarantine package at: $trimmed") }
                return@launch
            }
            val inspection = pluginInstallPort.inspect(quarantined)
            _uiState.update {
                it.copy(
                    importQuarantinedPath = quarantined.toString(),
                    importInspection = inspection,
                )
            }
        }
    }

    public fun dismissLegacyFormatWarning() {
        _uiState.update { it.copy(isLegacyUnsupportedFormat = false, legacyFormatName = null) }
    }

    public fun confirmImportInstall(trust: TrustDecision) {
        val path = _uiState.value.importQuarantinedPath ?: _uiState.value.importPathInput
        if (path.isBlank()) return
        val inspection = _uiState.value.importInspection
        val expectedDigest = inspection?.identity?.contentDigest
        viewModelScope.launch {
            val label = inspection?.identity?.key ?: path
            _uiState.update {
                it.copy(installingPackageLabel = label, installState = InstallState.SourceSelected)
            }
            val request = InstallRequest(sourceLabel = path, expectedDigest = expectedDigest, trust = trust)
            installPluginUseCase(request).collect { state ->
                _uiState.update { it.copy(installState = state) }
                if (state is InstallState.Installed) {
                    refreshInstalled()
                    val pkgLabel = "${state.record.publisher}:${state.record.id}"
                    _uiState.update {
                        it.copy(
                            importQuarantinedPath = null,
                            importInspection = null,
                            notificationMessage = "Successfully imported & installed '$pkgLabel'",
                        )
                    }
                } else if (state is InstallState.Failed) {
                    val err = state.report.errors.firstOrNull()?.message ?: "Import install failed"
                    _uiState.update { it.copy(errorMessage = err) }
                }
            }
        }
    }

    public fun setAuthorProjectPathInput(path: String) {
        _uiState.update { it.copy(authorProjectPathInput = path) }
    }

    public fun checkAuthorProject(projectPath: String) {
        val trimmed = projectPath.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val check = authorProjectTrustUseCase.check(trimmed)
            _uiState.update {
                it.copy(authorProjectPathInput = trimmed, authorTrustCheck = check)
            }
        }
    }

    public fun approveAuthorProject(sdkVersion: String = "1.0.0", generatorEntry: String = "") {
        val path = _uiState.value.authorProjectPathInput
        if (path.isBlank()) return
        viewModelScope.launch {
            val project = AuthorProject(
                path = path,
                fingerprint = "",
                sdkVersion = sdkVersion,
                generatorEntry = generatorEntry,
            )
            authorProjectTrustUseCase.approve(project)
            val check = authorProjectTrustUseCase.check(path)
            _uiState.update {
                it.copy(authorTrustCheck = check, notificationMessage = "Author project approved for execution")
            }
        }
    }

    public fun revokeAuthorProject(projectPath: String = _uiState.value.authorProjectPathInput) {
        if (projectPath.isBlank()) return
        viewModelScope.launch {
            authorProjectTrustUseCase.revoke(projectPath)
            val check = authorProjectTrustUseCase.check(projectPath)
            _uiState.update {
                it.copy(authorTrustCheck = check, notificationMessage = "Author project trust revoked")
            }
        }
    }

    public fun selectAuthorTask(task: String) {
        _uiState.update {
            it.copy(
                authorSelectedTask = task,
                authorTaskResult = null,
            )
        }
    }

    public fun runAuthorTask(task: String = _uiState.value.authorSelectedTask, timeoutMs: Long = 60000L) {
        val path = _uiState.value.authorProjectPathInput
        if (path.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAuthorTaskRunning = true, authorTaskResult = null) }
            val project = AuthorProject(
                path = path,
                fingerprint = "",
                sdkVersion = "1.0.0",
                generatorEntry = "",
            )
            val result = authorProjectTrustUseCase.runTask(project, task, timeoutMs)
            _uiState.update {
                it.copy(isAuthorTaskRunning = false, authorTaskResult = result)
            }
        }
    }

    private fun computePackageReferences(records: List<InstalledRecord>): Map<String, List<String>> {
        val map = mutableMapOf<String, MutableList<String>>()
        for (record in records) {
            val refs = map.getOrPut(record.identity.key) { mutableListOf() }
            record.enabledInWorkspaces.forEach { wsId ->
                refs.add("Workspace '$wsId'")
            }
        }
        for (record in records) {
            for (dep in record.dependencies) {
                val depKey = "${dep.publisher}:${dep.id}"
                map.getOrPut(depKey) { mutableListOf() }.add("Package '${record.identity.key}'")
            }
        }
        return map
    }
}
