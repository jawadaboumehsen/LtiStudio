/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.tools

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.model.setup.ToolRef
import java.util.UUID

public data class ToolsState(
    val inventory: ToolInventory? = null,
    val activeOperations: Map<ToolGroupId, ToolOperation> = emptyMap(),
    val isConnected: Boolean = true,
    /** Which refresh produced [inventory]; a slower, older one never replaces a newer one. */
    val inventoryGeneration: Long = 0L,
)

@Suppress("TooManyFunctions")
public class ToolManager(
    private val port: ToolStorePort,
    private val catalog: ToolGroupCatalog = ToolGroupCatalog,
    private val selectionRepository: org.ide.lti.core.domain.repository.setup.ToolchainSelectionRepository? = null,
    private val usageCoordinator: ToolUsageCoordinator? = null,
) {
    private val _state = MutableStateFlow(ToolsState())
    public val state: StateFlow<ToolsState> = _state.asStateFlow()

    public val isRomJobRunning: Boolean
        get() = usageCoordinator?.isJobActive == true

    /** [isRomJobRunning] as a flow, for screens that must react when a job starts or ends. */
    public val romJobActive: StateFlow<Boolean> = usageCoordinator?.jobActive ?: MutableStateFlow(false)

    private val refreshCounter = MutableStateFlow(0L)

    /**
     * Reads the store and publishes the result - unless a refresh that started later has already published: a
     * slow read that began before a version switch must not bring the old "In use" back after it.
     */
    public suspend fun refresh(): Result<ToolInventory> {
        val generation = refreshCounter.updateAndGet { it + 1 }
        val res = port.status(catalog)
        _state.update { current ->
            when {
                generation <= current.inventoryGeneration -> current
                res.isSuccess ->
                    current.copy(inventory = res.getOrThrow(), isConnected = true, inventoryGeneration = generation)
                else -> current.copy(isConnected = false, inventoryGeneration = generation)
            }
        }
        return res
    }

    public suspend fun portInit(): Result<InitInfo> =
        usageCoordinator?.withToolMutationAdmission { port.init().getOrThrow() } ?: port.init()

    @Suppress("CyclomaticComplexMethod", "LoopWithTooManyJumpStatements", "ReturnCount")
    public suspend fun prepareMissing(
        targets: Map<ToolGroupId, ResolvedSource>? = null,
        onEvent: (ToolGroupId, InstallEvent) -> Unit = { _, _ -> },
    ): SetupToolsSummary {
        val initResult = port.init()
        if (initResult.isFailure) {
            val reason = initResult.exceptionOrNull()?.message ?: "Could not initialize tool storage"
            val error = ToolError.ConnectionLost(reason)
            return SetupToolsSummary(catalog.allGroups.associate { it.id to GroupSetupResult.NotAttempted(error) })
        }
        val initInfo = initResult.getOrThrow()
        val defaultGroupId = catalog.allGroups.firstOrNull()?.id ?: ToolGroupId("tools")
        for (deletedPath in initInfo.deletedPaths) {
            onEvent(defaultGroupId, InstallEvent.Output("[tools] Deleted old tool folder $deletedPath"))
        }
        val status = refresh()
        if (status.isFailure) {
            val ex = status.exceptionOrNull()
            val error =
                (ex as? ToolErrorException)?.toolError
                    ?: ToolError.Internal(
                        "Failed to inspect tools inventory: ${ex?.message ?: "Could not inspect tool storage"}",
                    )
            return SetupToolsSummary(catalog.allGroups.associate { it.id to GroupSetupResult.Failed(error) })
        }

        val groupResults = mutableMapOf<ToolGroupId, GroupSetupResult>()
        var sharedBlocker: ToolError? = null
        var isCancelled = false

        for (group in catalog.allGroups) {
            if (isCancelled) {
                groupResults[group.id] = GroupSetupResult.Cancelled
                continue
            }
            if (sharedBlocker != null) {
                groupResults[group.id] = GroupSetupResult.NotAttempted(sharedBlocker)
                continue
            }

            val groupState = state.value.inventory?.groups?.get(group.id)
            if (groupState?.currentUnknown == true) {
                val msg = "Active selection for ${group.id.value} is unknown or custom; configure explicitly."
                val err = ToolError.UnsupportedLayout(listOf(msg))
                groupResults[group.id] = GroupSetupResult.Failed(err)
                continue
            }

            val pick = groupState?.selection
            val current = groupState?.current
            val pinned = defaultSourceForGroup(group)

            val frozen = targets?.get(group.id)
            val targetSource: ResolvedSource
            val targetId: String?
            if (frozen != null) {
                // The confirmed plan decides, not whatever the selections look like now. A version already
                // known by that source keeps its folder name; anything else is built.
                targetSource = frozen
                targetId =
                    listOfNotNull(pick, current?.takeIf { !it.intact }?.let { Selection(it.id, it.source) })
                        .firstOrNull { it.source.sameIdentityAs(frozen) }?.id
            } else if (pick != null) {
                targetSource = pick.source
                targetId = pick.id
            } else if (current != null && !current.intact) {
                targetSource = current.source
                targetId = current.id
            } else {
                targetSource = pinned
                targetId = null
            }

            // A folder name alone proves nothing: a saved pick whose id exists but whose commit or repository
            // differs would otherwise be reported ready while the wrong code is installed. Both must match.
            val currentMatchesTarget =
                current != null && current.intact && current.matches(targetId, targetSource)

            if (currentMatchesTarget) {
                // Intact on disk is not enough: setup also reports tools the service does not offer, and
                // switching to the version already in use is what registers them again.
                val registered = port.use(group.id, current.id, choice = ChoiceUpdate.Keep)
                groupResults[group.id] =
                    if (registered.isSuccess) {
                        GroupSetupResult.Reused(current.id)
                    } else {
                        val cause = registered.exceptionOrNull()
                        GroupSetupResult.Failed(
                            (cause as? ToolErrorException)?.toolError
                                ?: ToolError.Internal(cause?.message ?: "Could not register ${group.id.value}"),
                        )
                    }
                continue
            }

            val intactTarget =
                groupState?.installed?.firstOrNull { it.intact && it.matches(targetId, targetSource) }
            if (intactTarget != null) {
                val useRes = port.use(group.id, intactTarget.id, choice = ChoiceUpdate.Keep)
                if (useRes.isSuccess) {
                    groupResults[group.id] = GroupSetupResult.Reused(intactTarget.id)
                } else {
                    val msg = useRes.exceptionOrNull()?.message ?: "Failed to switch to intact target"
                    groupResults[group.id] = GroupSetupResult.Failed(ToolError.Internal(msg))
                }
                continue
            }

            val damagedTarget =
                groupState?.installed?.firstOrNull { it.matches(targetId, targetSource) }
            val forceNew = damagedTarget != null && !damagedTarget.intact

            // Setup builds what a saved pick names, so it passes the same checks as an install the user starts.
            val rejection = rejectedSource(group, targetSource)
            val opResult =
                rejection ?: mutateTools {
                    installInternal(
                        group = group,
                        source = targetSource,
                        forceNew = forceNew,
                        select = true,
                        choice = ChoiceUpdate.Keep,
                    ) { onEvent(group.id, it) }
                }
            when (opResult) {
                is OperationResult.Installed -> groupResults[group.id] = GroupSetupResult.Installed(opResult.versionId)
                is OperationResult.Reused -> groupResults[group.id] = GroupSetupResult.Reused(opResult.versionId)
                is OperationResult.Used -> groupResults[group.id] = GroupSetupResult.Installed(opResult.versionId)
                is OperationResult.Failed -> {
                    groupResults[group.id] = GroupSetupResult.Failed(opResult.error)
                    if (isSharedBlocker(opResult.error)) {
                        sharedBlocker = opResult.error
                    }
                }
                OperationResult.Cancelled -> {
                    groupResults[group.id] = GroupSetupResult.Cancelled
                    isCancelled = true
                }
                is OperationResult.Interrupted -> {
                    val err = ToolError.Internal(opResult.reason)
                    groupResults[group.id] = GroupSetupResult.Failed(err)
                }
                is OperationResult.TrustRequired -> {
                    val err = ToolError.Internal("Trust required for repository: ${opResult.url}")
                    groupResults[group.id] = GroupSetupResult.Failed(err)
                }
            }
        }

        withContext(NonCancellable) {
            refresh()
        }
        return SetupToolsSummary(groupResults)
    }

    public suspend fun retryFailed(onEvent: (ToolGroupId, InstallEvent) -> Unit = { _, _ -> }): SetupToolsSummary =
        prepareMissing(onEvent = onEvent)

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    public suspend fun installAndUse(
        group: ToolGroupId,
        source: ResolvedSource,
        forceNew: Boolean = false,
        onEvent: (InstallEvent) -> Unit = {},
    ): OperationResult {
        val groupDef =
            catalog.group(group)
                ?: return OperationResult.Failed(ToolError.NotFound("Group not found"), "Group not found")
        if (!state.value.isConnected) {
            return OperationResult.Failed(ToolError.ConnectionLost("Not connected to build service"), "Disconnected")
        }
        if (state.value.activeOperations.containsKey(group)) {
            return OperationResult.Failed(ToolError.Busy("Operation in progress for group ${group.value}"), "Busy")
        }

        rejectedSource(groupDef, source)?.let { return it }

        val res =
            mutateTools {
                installInternal(
                    group = groupDef,
                    source = source,
                    forceNew = forceNew,
                    select = true,
                    choice = ChoiceUpdate.Record,
                    onEvent = onEvent,
                )
            }
        refresh()
        return res
    }

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    public suspend fun repair(
        group: ToolGroupId,
        source: ResolvedSource? = null,
        onEvent: (InstallEvent) -> Unit = {},
    ): OperationResult {
        if (!state.value.isConnected) {
            return OperationResult.Failed(ToolError.ConnectionLost("Not connected to build service"), "Disconnected")
        }
        if (state.value.activeOperations.containsKey(group)) {
            return OperationResult.Failed(ToolError.Busy("Operation in progress for group ${group.value}"), "Busy")
        }
        val groupDef =
            catalog.group(group)
                ?: return OperationResult.Failed(ToolError.NotFound("Group not found"), "Group not found")
        if (state.value.inventory == null) {
            refresh()
        }
        val groupState = state.value.inventory?.groups?.get(group)
        val pinned = defaultSourceForGroup(groupDef)
        val currentSrc = groupState?.current?.source
        val isSameAsPinned =
            when {
                currentSrc is ResolvedSource.Git && pinned is ResolvedSource.Git ->
                    currentSrc.repoUrl == pinned.repoUrl && currentSrc.commit == pinned.commit &&
                        currentSrc.submodules == pinned.submodules
                currentSrc is ResolvedSource.Release && pinned is ResolvedSource.Release ->
                    currentSrc.url == pinned.url && currentSrc.sha256 == pinned.sha256
                else -> false
            }
        val resolvedSource =
            source
                ?: groupState?.selection?.source
                ?: if (isSameAsPinned) pinned else (currentSrc ?: pinned)

        rejectedSource(groupDef, resolvedSource)?.let { return it }

        val res =
            mutateTools {
                installInternal(
                    group = groupDef,
                    source = resolvedSource,
                    forceNew = true,
                    select = true,
                    choice = ChoiceUpdate.Keep,
                    onEvent = onEvent,
                )
            }
        refresh()
        return res
    }

    public suspend fun use(
        group: ToolGroupId,
        versionId: String,
        choice: ChoiceUpdate = ChoiceUpdate.Record,
    ): OperationResult {
        // A switch is quick but not instant (it re-registers the tools); while it runs the page shows it and
        // refuses another change to the group, exactly as for an install. It has no percentage to report.
        val switching =
            ToolOperation(
                opId = "use-${group.value}",
                group = group,
                kind = OperationKind.Use,
                phase = OperationPhase.Switching,
            )
        var claimed = false
        _state.update { s ->
            claimed = group !in s.activeOperations
            if (claimed) s.copy(activeOperations = s.activeOperations + (group to switching)) else s
        }
        if (!claimed) {
            return OperationResult.Failed(ToolError.Busy("Operation in progress for group ${group.value}"), "Busy")
        }
        val portRes =
            try {
                port.use(group, versionId, choice)
            } finally {
                _state.update { s -> s.copy(activeOperations = s.activeOperations - group) }
            }
        val res =
            if (portRes.isSuccess) {
                OperationResult.Used(versionId)
            } else {
                val ex = portRes.exceptionOrNull()
                val err =
                    (ex as? ToolErrorException)?.toolError
                        ?: ToolError.Internal(ex?.message ?: "Failed to switch version")
                OperationResult.Failed(err, "Use failed")
            }
        refresh()
        return res
    }

    @Suppress("ReturnCount")
    public suspend fun followStudio(group: ToolGroupId, onEvent: (InstallEvent) -> Unit = {}): OperationResult {
        if (state.value.activeOperations.containsKey(group)) {
            return OperationResult.Failed(ToolError.Busy("Operation in progress for group ${group.value}"), "Busy")
        }
        val groupDef =
            catalog.group(group)
                ?: return OperationResult.Failed(ToolError.NotFound("Group not found"), "Group not found")
        val pinnedSource = defaultSourceForGroup(groupDef)
        val inv = state.value.inventory ?: refresh().getOrNull()
        val installedPinned =
            inv?.groups?.get(group)?.installed?.firstOrNull {
                it.intact && it.source == pinnedSource
            }
        return if (installedPinned != null) {
            use(group, installedPinned.id, choice = ChoiceUpdate.Clear)
        } else {
            val res =
                mutateTools {
                    installInternal(
                        group = groupDef,
                        source = pinnedSource,
                        forceNew = false,
                        select = true,
                        choice = ChoiceUpdate.Clear,
                        onEvent = onEvent,
                    )
                }
            refresh()
            res
        }
    }

    public suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> = port.refs(group, repoUrl)

    public suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
        port.resolve(group, repoUrl, ref)

    public suspend fun isTrusted(repoUrl: String): Boolean = selectionRepository?.isTrusted(repoUrl) ?: true

    /** Whether the trust was saved. Callers must not go on as if it were when it was not. */
    public suspend fun recordTrust(repoUrl: String): Result<Unit> =
        selectionRepository?.recordTrust(repoUrl) ?: Result.success(Unit)

    @Suppress("ReturnCount")
    public suspend fun remove(group: ToolGroupId, versionId: String): OperationResult {
        if (state.value.activeOperations.containsKey(group)) {
            return OperationResult.Failed(ToolError.Busy("Operation in progress for group ${group.value}"), "Busy")
        }
        val admitted = usageCoordinator?.withToolMutationAdmission { port.remove(group, versionId) }
        if (admitted != null && admitted.isFailure) {
            val message = admitted.exceptionOrNull()?.message ?: "A ROM job is running"
            return OperationResult.Failed(ToolError.InUse(message), "Remove blocked")
        }
        val res = admitted?.getOrThrow() ?: port.remove(group, versionId)
        refresh()
        return if (res.isSuccess) {
            OperationResult.Used(versionId)
        } else {
            val ex = res.exceptionOrNull()
            val err =
                (ex as? ToolErrorException)?.toolError
                    ?: ToolError.InUse(ex?.message ?: "Cannot remove active version")
            OperationResult.Failed(err, "Remove failed")
        }
    }

    public suspend fun cancel(group: ToolGroupId): Result<Unit> {
        val result = port.cancel(group)
        if (result.isSuccess) {
            _state.update { it.copy(activeOperations = it.activeOperations - group) }
        }
        return result
    }

    public suspend fun cancelAll(budgetMs: Long = DEFAULT_CANCEL_BUDGET_MS): List<Result<Unit>> {
        val portResults = port.cancelAll(budgetMs)
        _state.update { it.copy(activeOperations = emptyMap()) }
        return portResults
    }

    private suspend fun mutateTools(block: suspend () -> OperationResult): OperationResult {
        val result =
            usageCoordinator?.withToolMutationAdmission(block)
                ?: return block()
        return result.getOrElse { mutationBlocked(it) }
    }

    private fun mutationBlocked(cause: Throwable?): OperationResult {
        val message = cause?.message ?: "Tool changes are unavailable while a ROM job is running"
        val error =
            if (message.contains("ROM job", ignoreCase = true)) {
                ToolError.InUse(message)
            } else {
                ToolError.Internal(message)
            }
        return OperationResult.Failed(error, message)
    }

    private fun isSharedBlocker(error: ToolError): Boolean = when (error) {
        is ToolError.MissingPackages,
        is ToolError.DiskFull,
        is ToolError.ConnectionLost,
        -> true
        else -> false
    }

    public fun targetSourceForGroup(group: ToolGroup): ResolvedSource {
        val groupState = state.value.inventory?.groups?.get(group.id)
        val pick = groupState?.selection
        val current = groupState?.current
        return pick?.source ?: (
            if (current != null &&
                !current.intact
            ) {
                current.source
            } else {
                defaultSourceForGroup(group)
            }
            )
    }

    public fun defaultSourceForGroup(group: ToolGroup): ResolvedSource = when (val s = group.source) {
        is ToolSource.Git ->
            ResolvedSource.Git(
                repoUrl = s.recommendedUrl,
                ref = s.pinnedRef,
                commit = s.pinnedCommit,
            )
        ToolSource.Release -> {
            val relConfig = group.recipe as? RecipeConfig.Release
            val firstVer = relConfig?.versions?.firstOrNull()
            ResolvedSource.Release(
                version = firstVer?.version.orEmpty(),
                url = firstVer?.url.orEmpty(),
                sha256 = firstVer?.sha256.orEmpty(),
                member = firstVer?.archiveMember.orEmpty(),
            )
        }
    }

    /**
     * Why [source] may not be built for [group] right now, or null. The one place for the rules - the source
     * must fit the group, a release must be a curated one, a repository other than the group's own must be
     * trusted - used by every way of installing, including the automatic setup.
     */
    @Suppress("ReturnCount")
    private suspend fun rejectedSource(group: ToolGroup, source: ResolvedSource): OperationResult? {
        if (source is ResolvedSource.Git && !source.hasImmutableCommits()) {
            return OperationResult.Failed(
                ToolError.Internal("A git source must name 40-character commits, not branches or tags"),
                "Source is not immutable",
            )
        }
        when {
            source is ResolvedSource.Git -> {
                if (group.source !is ToolSource.Git) {
                    return OperationResult.Failed(
                        ToolError.Internal("Source type does not match group configuration"),
                        "Invalid source for group",
                    )
                }
                val recommended = (group.source as? ToolSource.Git)?.recommendedUrl
                if (recommended != null && source.repoUrl != recommended &&
                    selectionRepository != null && !isTrusted(source.repoUrl)
                ) {
                    return OperationResult.TrustRequired(source.repoUrl)
                }
            }
            group.source is ToolSource.Git ->
                return OperationResult.Failed(
                    ToolError.Internal("Git groups accept only git sources"),
                    "Invalid source for git group",
                )
            source is ResolvedSource.Release -> {
                val curated = (group.recipe as? RecipeConfig.Release)?.versions.orEmpty()
                // The whole identity, including which archive entry is taken: another member of the same
                // checksummed archive is a different program.
                if (curated.none {
                        it.version == source.version && it.url == source.url && it.sha256 == source.sha256 &&
                            it.archiveMember == source.member
                    }
                ) {
                    return OperationResult.Failed(
                        ToolError.NotFound("Release '${source.version}' is not curated for ${group.id.value}"),
                        "Unknown release",
                    )
                }
            }
        }
        return null
    }

    private fun InstalledVersion.matches(id: String?, source: ResolvedSource): Boolean =
        (id == null || this.id == id) && this.source.sameIdentityAs(source)

    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private suspend fun installInternal(
        group: ToolGroup,
        source: ResolvedSource,
        forceNew: Boolean,
        select: Boolean,
        choice: ChoiceUpdate = ChoiceUpdate.Keep,
        onEvent: (InstallEvent) -> Unit = {},
    ): OperationResult {
        val opId = "op-${UUID.randomUUID().toString().take(8)}"
        val label =
            when (source) {
                is ResolvedSource.Git -> {
                    when (val ref = source.ref) {
                        is ToolRef.Tag -> ref.name
                        is ToolRef.Branch -> "${ref.name}-${source.commit.take(7)}"
                        is ToolRef.Commit -> source.commit.take(12)
                        is ToolRef.ReleaseVersion -> ref.version
                    }
                }
                is ResolvedSource.Release -> source.version
            }
        val request =
            InstallRequest(
                opId = opId,
                group = group.id,
                label = label,
                source = source,
                recipe = group.recipe,
                outputs = group.outputs,
                supportFiles = (group.supportFiles + group.outputs.flatMap { it.supportFiles }).distinct(),
                packages = group.packages,
                select = select,
                forceNew = forceNew,
                choice = choice,
            )

        var finalResult: OperationResult = OperationResult.Failed(ToolError.Internal("No event received"), "No events")
        val initialOp =
            ToolOperation(
                opId = opId,
                group = group.id,
                kind = if (forceNew) OperationKind.Repair else OperationKind.Install,
                source = source,
                phase = OperationPhase.Fetching,
                serverRunId = opId,
                percent = 0,
            )
        // Claimed atomically: auto-tools reaching a group the user is already installing (or a double click) used
        // to fail in the store and then, in `finally`, erase the running operation from the UI state.
        var claimed = false
        _state.update { s ->
            claimed = group.id !in s.activeOperations
            if (claimed) s.copy(activeOperations = s.activeOperations + (group.id to initialOp)) else s
        }
        if (!claimed) {
            val err = ToolError.Busy("Operation in progress for group ${group.id.value}")
            return OperationResult.Failed(err, err.message)
        }
        try {
            port.install(request).collect { ev ->
                onEvent(ev)
                when (ev) {
                    is InstallEvent.Started -> {
                        val op =
                            ToolOperation(
                                opId = opId,
                                group = group.id,
                                kind = if (forceNew) OperationKind.Repair else OperationKind.Install,
                                source = source,
                                phase = OperationPhase.Building,
                                serverRunId = ev.runId,
                                percent = 0,
                            )
                        _state.update { s -> s.copy(activeOperations = s.activeOperations + (group.id to op)) }
                    }
                    is InstallEvent.Phase -> {
                        _state.update { state ->
                            val operation = state.activeOperations[group.id] ?: return@update state
                            val moved = OperationProgress.enter(operation, ev.phase)
                            state.copy(activeOperations = state.activeOperations + (group.id to moved))
                        }
                    }
                    is InstallEvent.Output -> {
                        _state.update { state ->
                            val operation = state.activeOperations[group.id] ?: return@update state
                            val newLines = ev.text.lineSequence().filter { it.isNotBlank() }.toList()
                            val lines = (operation.outputTail + newLines).takeLast(MAX_OPERATION_LOG_LINES)
                            val updatedOp = OperationProgress.onOutput(operation.copy(outputTail = lines), ev.text)
                            state.copy(activeOperations = state.activeOperations + (group.id to updatedOp))
                        }
                    }
                    is InstallEvent.Finished -> {
                        finalResult = ev.result
                        _state.update { s -> s.copy(activeOperations = s.activeOperations - group.id) }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            return OperationResult.Cancelled
        } catch (t: Throwable) {
            val msg = t.message
            val isConn =
                msg != null && (
                    msg.contains("Connection lost", ignoreCase = true) ||
                        msg.contains("Connection refused", ignoreCase = true) ||
                        msg.contains("Stream closed", ignoreCase = true) ||
                        msg.contains("Failed to execute command on remote daemon", ignoreCase = true) ||
                        msg.contains("Broken pipe", ignoreCase = true)
                    )
            val err =
                if (isConn) {
                    ToolError.ConnectionLost(msg ?: "Connection lost")
                } else {
                    ToolError.Internal(msg ?: "Installation failed unexpectedly")
                }
            return OperationResult.Failed(err, err.message)
        } finally {
            _state.update { s -> s.copy(activeOperations = s.activeOperations - group.id) }
        }
        return finalResult
    }

    private companion object {
        const val MAX_OPERATION_LOG_LINES: Int = 200
    }
}

private val COMMIT_SHA = Regex("[0-9a-f]{40}")

/** A commit is a 40-hex id; anything else (a branch name, "main") can change under the version that names it. */
internal fun ResolvedSource.Git.hasImmutableCommits(): Boolean =
    COMMIT_SHA.matches(commit) && submodules.values.all { COMMIT_SHA.matches(it) }
