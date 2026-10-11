/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ReleaseVersion
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.GroupStatus
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.sameIdentityAs
import org.ide.lti.core.model.setup.ToolRef

public enum class PrimaryButtonAction {
    Use,
    InstallAndUse,
    Repair,
    Disabled,
}

public enum class RefType {
    Tag,
    Branch,
    Commit,
}

/**
 * The one thing an install is built from: which group, which repository, which ref, and the commit (with its
 * submodules) that exact pair resolved to. Anything that changes any of them drops it.
 */
public data class ResolvedSelection(
    val group: ToolGroupId,
    val repoUrl: String,
    val ref: ToolRef,
    val check: ResolvedSourceCheck,
) {
    val source: ResolvedSource.Git get() = ResolvedSource.Git(repoUrl, ref, check.commit, check.submodules)
}

/** Why the last change to [group] did not happen. */
public data class ToolActionError(val group: ToolGroupId, val message: String)

@Suppress("TooManyFunctions")
public class ToolsPageState(public val manager: ToolManager, private val scope: CoroutineScope) {
    private val _selectedGroup = MutableStateFlow(ToolGroupCatalog.ANDROID_TOOLS.id)
    public val selectedGroup: StateFlow<ToolGroupId> = _selectedGroup.asStateFlow()

    private val _resolvedSelection = MutableStateFlow<ResolvedSelection?>(null)
    public val resolvedSelection: StateFlow<ResolvedSelection?> = _resolvedSelection.asStateFlow()

    private val _selectedVersionId = MutableStateFlow<String?>(null)
    public val selectedVersionId: StateFlow<String?> = _selectedVersionId.asStateFlow()

    /**
     * The group as the inventory shows it, with the operation the manager is running on it right now: the
     * inventory comes from the disk and never knows about a build in progress.
     */
    private fun liveGroupState(state: org.ide.lti.core.domain.tools.ToolsState, group: ToolGroupId): GroupState? {
        val base = state.inventory?.groups?.get(group)
        val live = state.activeOperations[group] ?: return base
        return (base ?: GroupState(group, null, null, emptyList())).copy(operation = live)
    }

    private fun computeRows(state: org.ide.lti.core.domain.tools.ToolsState): List<ToolRowPresentation> =
        ToolGroupCatalog.allGroups.map { g ->
            val gState = liveGroupState(state, g.id) ?: GroupState(g.id, null, null, emptyList())
            ToolsPresentationMapper.mapRow(gState, isConnected = state.isConnected)
        }

    public val rows: StateFlow<List<ToolRowPresentation>> =
        manager.state.map { s ->
            computeRows(s)
        }.stateIn(scope, SharingStarted.Eagerly, computeRows(manager.state.value))

    public val selectedGroupState: StateFlow<GroupState?> =
        combine(manager.state, selectedGroup) { s, g ->
            liveGroupState(s, g)
        }.stateIn(scope, SharingStarted.Eagerly, liveGroupState(manager.state.value, _selectedGroup.value))

    private val _primaryButtonAction = MutableStateFlow(PrimaryButtonAction.Disabled)
    public val primaryButtonAction: StateFlow<PrimaryButtonAction> = _primaryButtonAction.asStateFlow()

    private val _primaryButtonDisabledReason = MutableStateFlow<String?>(null)
    public val primaryButtonDisabledReason: StateFlow<String?> = _primaryButtonDisabledReason.asStateFlow()

    init {
        recomputePrimaryAction()
        scope.launch {
            combine(manager.state, _selectedGroup, _selectedVersionId, manager.romJobActive) { _, _, _, _ ->
                recomputePrimaryAction()
            }.collect()
        }
    }

    public fun isGroupInstalling(group: ToolGroupId): Boolean {
        val s = manager.state.value
        val gState = s.inventory?.groups?.get(group)
        val hasActiveOp = s.activeOperations.containsKey(group)
        val hasRunningStateOp = gState?.operation?.let { it.result == null } == true
        return hasActiveOp || hasRunningStateOp || gState?.status == GroupStatus.Installing
    }

    private fun recomputePrimaryAction() {
        val s = manager.state.value
        val g = _selectedGroup.value
        val vId = _selectedVersionId.value
        val gState = s.inventory?.groups?.get(g)

        if (isGroupInstalling(g)) {
            _primaryButtonAction.value = PrimaryButtonAction.Disabled
            _primaryButtonDisabledReason.value = "Another change is running for this group"
            return
        }

        val resolved = _resolvedSelection.value?.takeIf { it.group == g }?.source
        val action = chooseAction(gState, vId, resolved)

        if (manager.isRomJobRunning && action != PrimaryButtonAction.Use) {
            _primaryButtonAction.value = PrimaryButtonAction.Disabled
            _primaryButtonDisabledReason.value = "A ROM job is running"
            return
        }

        _primaryButtonDisabledReason.value = null
        _primaryButtonAction.value = action
    }

    /**
     * A resolved ref names code, not a folder: a fork's "v1" is not the "v1" already installed from upstream, so
     * with something resolved an installed version is only a candidate for "Use" if it is that same code.
     */
    private fun chooseAction(gState: GroupState?, vId: String?, resolved: ResolvedSource?): PrimaryButtonAction {
        val usable = gState != null && vId != null && (listOfNotNull(gState.current) + gState.installed).any {
            it.id == vId && it.intact && (resolved == null || it.source.sameIdentityAs(resolved))
        }
        return when {
            gState == null -> PrimaryButtonAction.Disabled
            // Repair is for the version in use; an explicitly chosen other version is installed or used instead.
            gState.status == GroupStatus.NeedsRepair && (vId == null || vId == gState.current?.id) ->
                PrimaryButtonAction.Repair
            usable -> PrimaryButtonAction.Use
            else -> PrimaryButtonAction.InstallAndUse
        }
    }

    private val _isInspectorOpen = MutableStateFlow(true)
    public val isInspectorOpen: StateFlow<Boolean> = _isInspectorOpen.asStateFlow()

    public fun openInspector() {
        _isInspectorOpen.value = true
    }

    public fun closeInspector() {
        _isInspectorOpen.value = false
    }

    public fun toggleInspector() {
        _isInspectorOpen.value = !_isInspectorOpen.value
    }

    public fun selectPreviousGroup() {
        val all = ToolGroupCatalog.allGroups
        val currentIndex = all.indexOfFirst { it.id == _selectedGroup.value }
        if (currentIndex > 0) {
            selectGroup(all[currentIndex - 1].id)
        }
    }

    public fun selectNextGroup() {
        val all = ToolGroupCatalog.allGroups
        val currentIndex = all.indexOfFirst { it.id == _selectedGroup.value }
        if (currentIndex in 0 until all.size - 1) {
            selectGroup(all[currentIndex + 1].id)
        }
    }

    public fun selectGroup(group: ToolGroupId) {
        _selectedGroup.value = group
        _isInspectorOpen.value = true
        val currentVer = manager.state.value.inventory?.groups?.get(group)?.current?.id
        _selectedVersionId.value = currentVer
        // The repository typed for one tool is not another tool's repository.
        _customRepoUrl.value = null
        invalidateResolution()
        _availableRefs.value = null
        _actionError.value = null
        recomputePrimaryAction()
    }

    /** Drops the resolved commit and every request still in flight for it. */
    private fun invalidateResolution() {
        refsGeneration++
        resolveGeneration++
        _isLoadingRefs.value = false
        _isResolving.value = false
        _refsError.value = null
        _resolvedSelection.value = null
        _selectedRef.value = null
        _resolvedCheck.value = null
        _resolveError.value = null
        val g = _selectedGroup.value
        _selectedVersionId.value = manager.state.value.inventory?.groups?.get(g)?.current?.id
        recomputePrimaryAction()
    }

    public fun selectInstalledVersion(versionId: String) {
        _selectedVersionId.value = versionId
        recomputePrimaryAction()
    }

    public fun selectTargetVersion(versionId: String?) {
        _selectedVersionId.value = versionId
        recomputePrimaryAction()
    }

    public suspend fun refresh() {
        manager.refresh()
        recomputePrimaryAction()
    }

    public fun canUseVersion(versionId: String): Boolean = useDisabledReason(versionId) == null

    @Suppress("UnusedParameter") // same shape as removeDisabledReason; a use is blocked only per group
    public fun useDisabledReason(versionId: String): String? {
        if (isGroupInstalling(selectedGroup.value)) return "Another change is running for this group"
        return null
    }

    public fun canRemoveVersion(versionId: String): Boolean = removeDisabledReason(versionId) == null

    @Suppress("ReturnCount")
    public fun removeDisabledReason(versionId: String): String? {
        if (isGroupInstalling(selectedGroup.value)) return "Another change is running for this group"
        val gState = manager.state.value.inventory?.groups?.get(selectedGroup.value) ?: return null
        if (versionId == gState.current?.id) return "Version is currently in use"
        if (versionId == gState.selection?.id) return "Version is selected as your pick"
        if (manager.isRomJobRunning) return "A ROM job is running"
        return null
    }

    public fun canRepair(): Boolean = repairDisabledReason() == null

    @Suppress("ReturnCount")
    public fun repairDisabledReason(): String? {
        if (isGroupInstalling(selectedGroup.value)) return "Another change is running for this group"
        if (manager.isRomJobRunning) return "A ROM job is running"
        return null
    }

    public val canUseStudioVersion: Boolean
        get() {
            val gState = manager.state.value.inventory?.groups?.get(selectedGroup.value) ?: return false
            if (gState.selection == null) return false
            return !isGroupInstalling(selectedGroup.value)
        }

    @Suppress("ReturnCount")
    public fun useStudioVersionDisabledReason(): String? {
        val gState = manager.state.value.inventory?.groups?.get(selectedGroup.value) ?: return null
        if (gState.selection == null) return null
        if (isGroupInstalling(selectedGroup.value)) return "Another change is running for this group"
        return null
    }

    private val _actionError = MutableStateFlow<ToolActionError?>(null)
    public val actionError: StateFlow<ToolActionError?> = _actionError.asStateFlow()

    public fun dismissActionError() {
        _actionError.value = null
    }

    /** The group is captured now: the selection may move on before the call runs. */
    private fun launchChange(group: ToolGroupId, change: suspend () -> OperationResult) {
        _actionError.value = null
        scope.launch {
            val message =
                when (val result = change()) {
                    is OperationResult.Failed -> ToolsPresentationMapper.mapError(result.error).message
                    is OperationResult.Interrupted -> result.reason
                    is OperationResult.TrustRequired -> "Trust ${result.url} before using it"
                    else -> null
                }
            if (message != null) _actionError.value = ToolActionError(group, message)
        }
    }

    public fun use(versionId: String) {
        val group = selectedGroup.value
        launchChange(group) { manager.use(group, versionId) }
    }

    public fun followStudio() {
        val group = selectedGroup.value
        launchChange(group) { manager.followStudio(group) }
    }

    public fun remove(versionId: String) {
        val group = selectedGroup.value
        launchChange(group) { manager.remove(group, versionId) }
    }

    public fun repair() {
        val group = selectedGroup.value
        launchChange(group) { manager.repair(group) }
    }

    public fun installAndUse(source: ResolvedSource) {
        val group = selectedGroup.value
        launchChange(group) { manager.installAndUse(group, source) }
    }

    /**
     * Installs exactly what was chosen: the resolved commit of a git group, or the curated release picked in a
     * release group. Nothing when nothing valid is chosen for the selected group.
     */
    public fun installResolved() {
        val source: ResolvedSource? =
            if (isReleaseGroup) {
                chosenRelease()?.let { ResolvedSource.Release(it.version, it.url, it.sha256, it.archiveMember) }
            } else {
                _resolvedSelection.value?.takeIf { it.group == selectedGroup.value }?.source
            }
        if (source != null) installAndUse(source)
    }

    private fun chosenRelease(): ReleaseVersion? = curatedReleases.firstOrNull {
        it.version == _selectedVersionId.value
    }

    /** Whether "Install & use" has something valid to install: a clean resolution, or a picked curated release. */
    public fun canInstallSelected(): Boolean = if (isReleaseGroup) {
        chosenRelease() != null
    } else {
        _resolvedSelection.value?.let { it.group == selectedGroup.value && it.check.layoutMissing.isEmpty() } == true
    }

    public fun cancelActiveOperation() {
        val group = selectedGroup.value // the one clicked, not whichever is selected when the coroutine runs
        scope.launch {
            manager.cancel(group)
        }
    }

    public suspend fun refs(repoUrl: String): Result<RefListing> = manager.refs(selectedGroup.value, repoUrl)

    public suspend fun resolve(repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
        manager.resolve(selectedGroup.value, repoUrl, ref)

    public suspend fun isTrusted(repoUrl: String): Boolean = manager.isTrusted(repoUrl)

    public suspend fun recordTrust(repoUrl: String): Result<Unit> = manager.recordTrust(repoUrl)

    private val _selectedRefType = MutableStateFlow(RefType.Tag)
    public val selectedRefType: StateFlow<RefType> = _selectedRefType.asStateFlow()

    public fun selectRefType(type: RefType) {
        if (type == _selectedRefType.value) return
        _selectedRefType.value = type
        // What was resolved belongs to the other kind of ref.
        invalidateResolution()
    }

    /** The branch or commit text changed: the commit resolved for the old text must not be installed for the new. */
    public fun refInputChanged() {
        if (_resolvedSelection.value != null || _isResolving.value || _resolveError.value != null) {
            invalidateResolution()
        }
    }

    private val _availableRefs = MutableStateFlow<RefListing?>(null)
    public val availableRefs: StateFlow<RefListing?> = _availableRefs.asStateFlow()

    private val _isLoadingRefs = MutableStateFlow(false)
    public val isLoadingRefs: StateFlow<Boolean> = _isLoadingRefs.asStateFlow()

    private val _customRepoUrl = MutableStateFlow<String?>(null)
    public val customRepoUrl: StateFlow<String?> = _customRepoUrl.asStateFlow()

    public fun setCustomRepoUrl(url: String?) {
        if (url == _customRepoUrl.value) return
        _customRepoUrl.value = url
        invalidateResolution()
        // The tags listed belong to the old repository.
        _availableRefs.value = null
        _refsError.value = null
        refsReload?.cancel()
        if (_selectedRefType.value != RefType.Tag) return
        if (url.isNullOrBlank()) {
            loadRefs()
        } else {
            // Typing a URL: wait for a pause instead of asking the network after every character.
            refsReload =
                scope.launch {
                    delay(REFS_RELOAD_DEBOUNCE_MS)
                    loadRefs()
                }
        }
    }

    private var refsReload: Job? = null

    private val _refsError = MutableStateFlow<String?>(null)

    /** Why the tags could not be listed, for a message and a retry; null when they were (or none were asked for). */
    public val refsError: StateFlow<String?> = _refsError.asStateFlow()

    private val _selectedRef = MutableStateFlow<ToolRef?>(null)
    public val selectedRef: StateFlow<ToolRef?> = _selectedRef.asStateFlow()

    private var refsGeneration = 0
    private var resolveGeneration = 0

    private val _resolvedCheck = MutableStateFlow<ResolvedSourceCheck?>(null)
    public val resolvedCheck: StateFlow<ResolvedSourceCheck?> = _resolvedCheck.asStateFlow()

    private val _isResolving = MutableStateFlow(false)
    public val isResolving: StateFlow<Boolean> = _isResolving.asStateFlow()

    private val _resolveError = MutableStateFlow<String?>(null)
    public val resolveError: StateFlow<String?> = _resolveError.asStateFlow()

    private val _showTrustDialog = MutableStateFlow(false)
    public val showTrustDialog: StateFlow<Boolean> = _showTrustDialog.asStateFlow()

    private val _untrustedRepoUrl = MutableStateFlow<String?>(null)
    public val untrustedRepoUrl: StateFlow<String?> = _untrustedRepoUrl.asStateFlow()

    private class PendingTrust(val group: ToolGroupId, val url: String, val action: suspend () -> Unit)

    private var pendingTrust: PendingTrust? = null

    public fun dismissTrustDialog() {
        pendingTrust = null
        _showTrustDialog.value = false
        _untrustedRepoUrl.value = null
    }

    /** Trusts the repository, then runs the action the user asked for (if they are still on that group). */
    public fun confirmTrustAndProceed() {
        val pending = pendingTrust
        pendingTrust = null
        _showTrustDialog.value = false
        _untrustedRepoUrl.value = null
        scope.launch {
            if (pending == null) return@launch
            val saved = recordTrust(pending.url)
            if (saved.isFailure) {
                // Going on would only come straight back to "trust required".
                val reason = saved.exceptionOrNull()?.message ?: "unknown error"
                _actionError.value = ToolActionError(pending.group, "Couldn't save trust for ${pending.url}: $reason")
                return@launch
            }
            if (pending.group == selectedGroup.value) pending.action()
        }
    }

    public suspend fun requestWithTrustCheck(url: String, onTrusted: suspend () -> Unit) {
        val group = selectedGroup.value
        val groupDef = ToolGroupCatalog.group(group)
        val recommended = (groupDef?.source as? ToolSource.Git)?.recommendedUrl
        if (url != recommended && !isTrusted(url)) {
            pendingTrust = PendingTrust(group, url, onTrusted)
            _untrustedRepoUrl.value = url
            _showTrustDialog.value = true
        } else {
            onTrusted()
        }
    }

    public val isReleaseGroup: Boolean
        get() {
            val g = ToolGroupCatalog.group(selectedGroup.value) ?: return false
            return g.source is ToolSource.Release
        }

    public val curatedReleases: List<ReleaseVersion>
        get() {
            val g = ToolGroupCatalog.group(selectedGroup.value) ?: return emptyList()
            return (g.recipe as? RecipeConfig.Release)?.versions ?: emptyList()
        }

    public val recommendedUrl: String?
        get() {
            val g = ToolGroupCatalog.group(selectedGroup.value) ?: return null
            return (g.source as? ToolSource.Git)?.recommendedUrl
        }

    public fun loadRefs(repoUrl: String? = null) {
        val group = _selectedGroup.value
        val targetUrl = repoUrl ?: customRepoUrl.value ?: recommendedUrl ?: ""
        val generation = ++refsGeneration
        _isLoadingRefs.value = true
        _refsError.value = null
        scope.launch {
            val result = manager.refs(group, targetUrl)
            if (generation != refsGeneration) return@launch // a newer request or a group change replaced this one
            _isLoadingRefs.value = false
            if (result.isSuccess) {
                _availableRefs.value = result.getOrThrow()
            } else {
                _availableRefs.value = null
                _refsError.value = result.exceptionOrNull()?.message ?: "Could not reach the repository"
            }
        }
    }

    /** Resolves [ref]; the result is kept only if the group, repository and ref are still the ones asked for. */
    public fun resolveRef(ref: ToolRef, repoUrl: String? = null) {
        val group = _selectedGroup.value
        val targetUrl = repoUrl ?: customRepoUrl.value ?: recommendedUrl ?: ""
        val generation = ++resolveGeneration
        _resolvedSelection.value = null
        _selectedRef.value = null
        _resolvedCheck.value = null
        _resolveError.value = null
        _isResolving.value = true
        scope.launch {
            val result = manager.resolve(group, targetUrl, ref)
            if (generation != resolveGeneration) return@launch
            _isResolving.value = false
            if (result.isSuccess) {
                val check = result.getOrThrow()
                _resolvedSelection.value = ResolvedSelection(group, targetUrl, ref, check)
                _resolvedCheck.value = check
                _selectedRef.value = ref
                val versionId =
                    when (ref) {
                        is ToolRef.Tag -> ref.name
                        is ToolRef.Branch -> ref.name
                        is ToolRef.Commit -> ref.sha.take(7)
                        is ToolRef.ReleaseVersion -> ref.version
                    }
                selectTargetVersion(versionId)
            } else {
                _resolveError.value = result.exceptionOrNull()?.message ?: "Failed to resolve reference"
            }
        }
    }
}

private const val REFS_RELOAD_DEBOUNCE_MS = 600L
