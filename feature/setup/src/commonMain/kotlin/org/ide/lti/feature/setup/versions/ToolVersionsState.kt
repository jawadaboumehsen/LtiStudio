/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.RestoreCoordinatorPort
import org.ide.lti.core.domain.setup.RestoreOutcome
import org.ide.lti.core.domain.setup.RevertingGroup
import org.ide.lti.core.domain.setup.SetupOutcome
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.UpdateAvailability
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.InstalledGroupArtifact
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.domain.setup.ports.ResolveOutcome
import org.ide.lti.core.domain.setup.ports.SourceResolverPort
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort
import org.ide.lti.core.model.setup.DistroToolSelections
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.core.model.setup.ToolSelection

public sealed interface ResolutionState {
    public data object Idle : ResolutionState
    public data object Resolving : ResolutionState
    public data class Resolved(val input: ResolvedInput, val compatibility: CompatibilityResult) : ResolutionState
    public data class Error(val reason: String, val canRetry: Boolean = true) : ResolutionState
}

public data class ToolGroupDraft(
    val group: ToolGroupId,
    val repoUrl: String? = null,
    val ref: ToolRef,
    val refQuery: String = "",
    val isAdvanced: Boolean = false,
)

public data class ReviewGroupChange(
    val groupId: ToolGroupId,
    val currentLabel: String,
    val currentCommit: String?,
    val newLabel: String,
    val newCommit: String?,
    val affectedTools: List<String>,
)

public data class ReviewSwitchModel(
    val changedGroups: List<ReviewGroupChange>,
    val unchangedGroups: List<ToolGroupId>,
    val requiresTrustConfirmation: Boolean,
    val untrustedRepoUrl: String?,
)

public data class RestorePreviousModel(
    val activeInstallId: String,
    val previousInstallId: String,
    val revertingGroups: List<RevertingGroup>,
)

public class ToolVersionsState(
    private val scope: CoroutineScope,
    private val repository: ToolchainSelectionRepository,
    private val sourceResolver: SourceResolverPort? = null,
    private val installationPort: ToolchainInstallationPort? = null,
    private val restoreCoordinator: RestoreCoordinatorPort? = null,
    private val activeDistroProvider: () -> String = { "Ubuntu" },
    private val isBuildingProvider: () -> Boolean = { false },
) {
    private val _rows = MutableStateFlow<List<ToolGroupRowModel>>(emptyList())
    public val rows: StateFlow<List<ToolGroupRowModel>> = _rows.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<ToolGroupId?>(null)
    public val selectedGroupId: StateFlow<ToolGroupId?> = _selectedGroupId.asStateFlow()

    private val _draft = MutableStateFlow<ToolGroupDraft?>(null)
    public val draft: StateFlow<ToolGroupDraft?> = _draft.asStateFlow()

    private val _availableRefs = MutableStateFlow(RefListing())
    public val availableRefs: StateFlow<RefListing> = _availableRefs.asStateFlow()

    private val _isLoadingRefs = MutableStateFlow(false)
    public val isLoadingRefs: StateFlow<Boolean> = _isLoadingRefs.asStateFlow()

    private val _resolutionState = MutableStateFlow<ResolutionState>(ResolutionState.Idle)
    public val resolutionState: StateFlow<ResolutionState> = _resolutionState.asStateFlow()

    private val _isReviewOpen = MutableStateFlow(false)
    public val isReviewOpen: StateFlow<Boolean> = _isReviewOpen.asStateFlow()

    private val _reviewModel = MutableStateFlow<ReviewSwitchModel?>(null)
    public val reviewModel: StateFlow<ReviewSwitchModel?> = _reviewModel.asStateFlow()

    private val _isRestoreDialogOpen = MutableStateFlow(false)
    public val isRestoreDialogOpen: StateFlow<Boolean> = _isRestoreDialogOpen.asStateFlow()

    private val _restoreModel = MutableStateFlow<RestorePreviousModel?>(null)
    public val restoreModel: StateFlow<RestorePreviousModel?> = _restoreModel.asStateFlow()

    private var cachedSelections = DistroToolSelections()
    private var installedGroups: Map<String, InstalledGroupArtifact> = emptyMap()
    private val updateAvailabilities = mutableMapOf<ToolGroupId, Boolean>()
    private var refsJob: Job? = null
    private var resolveJob: Job? = null

    init {
        refresh()
    }

    public fun refresh(distro: String = activeDistroProvider()) {
        scope.launch {
            cachedSelections = repository.desiredSelections(distro)
            val installState = try {
                installationPort?.state()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                null
            }
            installedGroups = installState?.activeGroups.orEmpty()
            _rows.value = computeRows()
        }
    }

    private fun computeRows(): List<ToolGroupRowModel> = ToolGroupCatalog.DEFAULT_GROUPS.map { group ->
        val defaultLabel = defaultLabelFor(group)
        val desired = cachedSelections.desired[group.id.value]
        val desiredVersion = desired?.ref?.let { refToLabel(it) } ?: defaultLabel
        val hasUpdate = updateAvailabilities[group.id] ?: false
        val installedArt = installedGroups[group.id.value]
        val installedVersion = installedArt?.version ?: defaultLabel
        val isHealthy = installedArt?.isHealthy ?: true

        ToolVersionsPresentationMapper.mapGroupRow(
            groupId = group.id.value,
            installedVersion = installedVersion,
            desiredVersion = desiredVersion,
            isHealthy = isHealthy,
            isBuilding = isBuildingProvider(),
            isUnsupported = false,
            hasUpdateAvailable = hasUpdate,
        )
    }

    public fun startEdit(groupId: ToolGroupId) {
        val group = ToolGroupCatalog.group(groupId) ?: return
        _selectedGroupId.value = groupId
        val existingDesired = cachedSelections.desired[groupId.value]
        val initialRef = existingDesired?.ref ?: defaultRefFor(group)
        val initialUrl = existingDesired?.repoUrl
        val newDraft = ToolGroupDraft(group = groupId, repoUrl = initialUrl, ref = initialRef)
        _draft.value = newDraft
        fetchRefs(groupId, initialUrl)
        resolveDraft(newDraft)
    }

    public fun updateDraftRef(ref: ToolRef) {
        val current = _draft.value ?: return
        val updated = current.copy(ref = ref)
        _draft.value = updated
        resolveDraft(updated)
    }

    public fun updateDraftRepoUrl(url: String?) {
        val current = _draft.value ?: return
        val updated = current.copy(repoUrl = url)
        _draft.value = updated
        fetchRefs(current.group, url)
        resolveDraft(updated)
    }

    public fun updateDraftQuery(query: String) {
        _draft.value = _draft.value?.copy(refQuery = query)
    }

    public fun updateDraftAdvanced(isAdvanced: Boolean) {
        _draft.value = _draft.value?.copy(isAdvanced = isAdvanced)
    }

    public fun cancelEdit() {
        refsJob?.cancel()
        resolveJob?.cancel()
        _selectedGroupId.value = null
        _draft.value = null
        _isLoadingRefs.value = false
        _resolutionState.value = ResolutionState.Idle
        _availableRefs.value = RefListing()
    }

    public suspend fun saveDraft(distro: String = activeDistroProvider()): Result<Long> {
        val current = _draft.value ?: return Result.failure(IllegalStateException("No draft"))
        val updatedMap = cachedSelections.desired.mapKeys { ToolGroupId(it.key) }.toMutableMap()
        updatedMap[current.group] = ToolSelection(current.group.value, current.repoUrl, current.ref)

        val res = repository.saveSelections(distro, cachedSelections.revision, updatedMap)
        if (res.isSuccess) {
            cachedSelections = repository.desiredSelections(distro)
            cancelEdit()
            _rows.value = computeRows()
        }
        return res
    }

    public suspend fun checkForUpdates(distro: String = activeDistroProvider(), groupId: ToolGroupId) {
        val resolver = sourceResolver ?: return
        val selections = if (cachedSelections.desired.containsKey(groupId.value)) {
            cachedSelections
        } else {
            repository.desiredSelections(distro).also { cachedSelections = it }
        }
        val currentDesired = selections.desired[groupId.value]
        val ref = currentDesired?.ref
        if (ref is ToolRef.Branch) {
            val hex40Regex = Regex("^[0-9a-f]{40}$")
            val gitSource = ToolGroupCatalog.group(groupId)?.source as? ToolSource.Git
            val fallbackSha = gitSource?.recommendedCommit?.takeIf { it.matches(hex40Regex) } ?: "0".repeat(40)
            val installedCommit = installedGroups[groupId.value]?.commit
                ?.takeIf { it.matches(hex40Regex) }
                ?: fallbackSha
            val frozen = ResolvedInput.Git(
                group = groupId,
                repoUrl = currentDesired.repoUrl,
                ref = ref,
                commit = installedCommit,
                resolvedAt = 0L,
            )
            if (resolver.updateAvailability(frozen) is UpdateAvailability.UpdateAvailable) {
                updateAvailabilities[groupId] = true
                _rows.value = computeRows()
            }
        }
    }

    public fun openReview() {
        val changed = mutableListOf<ReviewGroupChange>()
        val unchanged = mutableListOf<ToolGroupId>()

        for (group in ToolGroupCatalog.DEFAULT_GROUPS) {
            val defaultLabel = defaultLabelFor(group)
            val desired = cachedSelections.desired[group.id.value]
            val installed = installedGroups[group.id.value]

            val currentLabel = installed?.version ?: defaultLabel
            val currentCommit = installed?.commit
            val currentRepoUrl = installed?.repoUrl

            val desiredLabel = desired?.ref?.let { refToLabel(it) } ?: defaultLabel
            val desiredCommit = (desired?.ref as? ToolRef.Commit)?.sha
            val desiredRepoUrl = desired?.repoUrl

            val isChanged = if (desired != null) {
                desiredLabel != currentLabel ||
                    desiredRepoUrl != currentRepoUrl ||
                    (desiredCommit != null && currentCommit != null && desiredCommit != currentCommit)
            } else {
                false
            }

            if (isChanged) {
                changed.add(
                    ReviewGroupChange(
                        groupId = group.id,
                        currentLabel = currentLabel,
                        currentCommit = currentCommit,
                        newLabel = desiredLabel,
                        newCommit = desiredCommit,
                        affectedTools = group.outputs.map { it.toolId },
                    ),
                )
            } else {
                unchanged.add(group.id)
            }
        }

        val untrustedUrl = changed.firstNotNullOfOrNull { change ->
            val selection = cachedSelections.desired[change.groupId.value]
            selection?.repoUrl
        }

        _reviewModel.value = ReviewSwitchModel(
            changedGroups = changed,
            unchangedGroups = unchanged,
            requiresTrustConfirmation = untrustedUrl != null,
            untrustedRepoUrl = untrustedUrl,
        )
        _isReviewOpen.value = true
    }

    public var onBuildAndSwitch: (suspend () -> SetupOutcome)? = null

    public suspend fun confirmBuildAndSwitch(): SetupOutcome? {
        closeReview()
        val outcome = onBuildAndSwitch?.invoke()
        refresh()
        return outcome
    }

    public fun closeReview() {
        _isReviewOpen.value = false
        _reviewModel.value = null
    }

    public suspend fun openRestoreDialog(): Boolean {
        val state = installationPort?.state()
        val active = state?.activeInstallId?.value
        val prev = state?.previousInstallId?.value
        if (active == null || prev == null) return false

        val revertSet = restoreCoordinator?.getRevertSet().orEmpty()
        _restoreModel.value = RestorePreviousModel(active, prev, revertSet)
        _isRestoreDialogOpen.value = true
        return true
    }

    public fun closeRestoreDialog() {
        _isRestoreDialogOpen.value = false
        _restoreModel.value = null
    }

    public suspend fun confirmRestore(distro: String = activeDistroProvider()): RestoreOutcome {
        val outcome = if (restoreCoordinator != null) {
            restoreCoordinator.restore(distro, cachedSelections.revision)
        } else {
            executeDirectRestore()
        }

        if (outcome is RestoreOutcome.Succeeded) {
            refresh(distro)
            closeRestoreDialog()
        }
        return outcome
    }

    private suspend fun executeDirectRestore(): RestoreOutcome {
        val port = installationPort
        val state = port?.state()
        val active = state?.activeInstallId
        val prev = state?.previousInstallId
        if (port == null || active == null || prev == null) {
            return RestoreOutcome.Failed("No installation port available or no active/previous install")
        }

        val req = ActivationRequest(
            requestId = java.util.UUID.randomUUID().toString(),
            expectedActiveInstallId = active,
            targetInstallId = prev,
        )
        return when (val actOutcome = port.activate(req)) {
            is ActivationOutcome.Committed -> RestoreOutcome.Succeeded(
                activeInstallId = prev,
                revertSet = emptyList(),
                desiredUpdated = false,
            )
            else -> RestoreOutcome.Failed("Activation failed: $actOutcome")
        }
    }

    public suspend fun resetToRecommended(
        groupId: ToolGroupId,
        distro: String = activeDistroProvider(),
    ): Result<Long> {
        val group = ToolGroupCatalog.group(groupId)
            ?: return Result.failure(IllegalArgumentException("Unknown tool group $groupId"))

        val defaultRef = defaultRefFor(group)
        val updatedMap = cachedSelections.desired.mapKeys { ToolGroupId(it.key) }.toMutableMap()
        updatedMap[groupId] = ToolSelection(group = groupId.value, repoUrl = null, ref = defaultRef)

        val res = repository.saveSelections(distro, cachedSelections.revision, updatedMap)
        if (res.isSuccess) {
            cachedSelections = repository.desiredSelections(distro)
            if (_selectedGroupId.value == groupId) {
                _draft.value = ToolGroupDraft(group = groupId, repoUrl = null, ref = defaultRef)
            }
            _rows.value = computeRows()
        }
        return res
    }

    private fun fetchRefs(groupId: ToolGroupId, repoUrl: String?) {
        refsJob?.cancel()
        val resolver = sourceResolver ?: return
        _isLoadingRefs.value = true
        refsJob = scope.launch {
            try {
                val listing = resolver.listRefs(groupId, repoUrl)
                _availableRefs.value = listing
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _availableRefs.value = RefListing()
            } finally {
                _isLoadingRefs.value = false
            }
        }
    }

    private fun resolveDraft(currentDraft: ToolGroupDraft) {
        resolveJob?.cancel()
        val resolver = sourceResolver ?: run {
            _resolutionState.value = ResolutionState.Idle
            return
        }
        _resolutionState.value = ResolutionState.Resolving
        resolveJob = scope.launch {
            try {
                val selection = ToolSelection(
                    group = currentDraft.group.value,
                    repoUrl = currentDraft.repoUrl,
                    ref = currentDraft.ref,
                )
                val outcome = resolver.resolve(selection)
                _resolutionState.value = mapResolveOutcome(outcome, resolver, currentDraft)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _resolutionState.value = ResolutionState.Error(e.message ?: "Resolution failed")
            }
        }
    }
}

private suspend fun mapResolveOutcome(
    outcome: ResolveOutcome,
    resolver: SourceResolverPort,
    currentDraft: ToolGroupDraft,
): ResolutionState = when (outcome) {
    is ResolveOutcome.Resolved -> {
        val input = outcome.input
        val compatibility = if (input is ResolvedInput.Git) {
            resolver.checkLayout(input)
        } else {
            CompatibilityResult.LayoutCompatible
        }
        ResolutionState.Resolved(input, compatibility)
    }
    is ResolveOutcome.NotFound -> ResolutionState.Error("Reference not found: ${currentDraft.ref}")
    is ResolveOutcome.AccessDenied -> ResolutionState.Error("Access denied: authentication required")
    is ResolveOutcome.Unreachable -> ResolutionState.Error("Repository unreachable: ${outcome.reason}")
}

private fun defaultLabelFor(group: ToolGroup): String = when (val source = group.source) {
    is ToolSource.Git -> source.recommendedLabel
    is ToolSource.Release -> {
        val releaseRecipe = group.recipe as? RecipeConfig.Release
        releaseRecipe?.versions?.firstOrNull()?.version ?: "default"
    }
}

private fun defaultRefFor(group: ToolGroup): ToolRef = when (val source = group.source) {
    is ToolSource.Git -> ToolRef.Tag(source.recommendedLabel)
    is ToolSource.Release -> {
        val releaseRecipe = group.recipe as? RecipeConfig.Release
        val version = releaseRecipe?.versions?.firstOrNull()?.version ?: "default"
        ToolRef.ReleaseVersion(version)
    }
}

private fun refToLabel(ref: ToolRef): String = when (ref) {
    is ToolRef.Tag -> ref.name
    is ToolRef.Branch -> ref.name
    is ToolRef.Commit -> ref.sha.take(7)
    is ToolRef.ReleaseVersion -> ref.version
}
