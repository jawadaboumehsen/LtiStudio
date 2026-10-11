/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import kotlinx.coroutines.flow.flow
import org.ide.lti.core.data.setup.execute.PlanExecutor
import org.ide.lti.core.data.setup.execute.handlers.ComponentToolsHandler
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.Selection
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef

/** Store boundary for orchestration tests; recipes and transport are tested independently. */
internal class RecordingComponentStore : ToolStorePort {
    val requests = mutableListOf<InstallRequest>()
    val groups = mutableMapOf<ToolGroupId, GroupState>()
    var result: (InstallRequest) -> OperationResult = {
        OperationResult.Installed(org.ide.lti.core.data.tools.KotlinStoreLayout.computeVersionId(it.source))
    }

    /** Called for each finished install: a test uses it to let the fake service offer the new tools. */
    var onInstalled: (InstallRequest) -> Unit = {}
    var versionTransform: (InstalledVersion) -> InstalledVersion = { it }
    var statusResult: (ToolGroupCatalog) -> Result<ToolInventory> = { Result.success(ToolInventory(groups.toMap())) }

    override suspend fun init() = Result.success(InitInfo())

    override suspend fun status(catalog: ToolGroupCatalog) = statusResult(catalog)

    override suspend fun refs(group: ToolGroupId, repoUrl: String) =
        Result.success(RefListing(emptyList(), emptyList()))

    override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef) =
        Result.success(ResolvedSourceCheck("1".repeat(40)))

    override fun install(request: InstallRequest) = flow {
        requests += request
        emit(InstallEvent.Started("component-${request.opId}"))
        emit(InstallEvent.Output("Building ${request.group.value}"))
        val outcome = result(request)
        if (outcome is OperationResult.Installed) {
            onInstalled(request)
            val version =
                InstalledVersion(
                    request.group,
                    outcome.versionId,
                    request.label,
                    request.source,
                    RecipeSummary("test", 1),
                    request.outputs.map { InstalledOutput(it.file, 1, "755") },
                    "2026-10-03",
                    true,
                )
            val installed = versionTransform(version)
            val sel = Selection(installed.id, installed.source)
            groups[request.group] = GroupState(request.group, installed, sel, listOf(installed))
        }
        emit(InstallEvent.Finished(outcome))
    }

    fun seedRecommended() {
        for ((group, input) in SetupPlanFactory.defaultCatalogInputs()) {
            val definition = requireNotNull(ToolGroupCatalog.group(group))
            val source =
                when (input) {
                    is org.ide.lti.core.domain.setup.ResolvedInput.Git ->
                        ResolvedSource.Git(
                            requireNotNull(input.repoUrl),
                            input.ref,
                            input.commit,
                            input.submoduleCommits,
                        )
                    is org.ide.lti.core.domain.setup.ResolvedInput.Release ->
                        ResolvedSource.Release(input.version, input.url, input.sha256, input.archiveMember)
                }
            val versionId = org.ide.lti.core.data.tools.KotlinStoreLayout.computeVersionId(source)
            val version =
                InstalledVersion(
                    group,
                    versionId,
                    group.value,
                    source,
                    RecipeSummary("test", 1),
                    definition.outputs.map { InstalledOutput(it.file, 1, "755") },
                    "2026-10-03",
                    true,
                )
            val sel = Selection(version.id, version.source)
            groups[group] = GroupState(group, version, sel, listOf(version))
        }
    }

    override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> {
        val existing = groups[group]
        if (existing != null) {
            val ver = existing.installed.firstOrNull { it.id == versionId } ?: existing.current
            groups[group] =
                existing.copy(
                    current = ver,
                    selection = ver?.let { Selection(it.id, it.source) },
                )
        }
        return Result.success(GroupLinks(versionId, null))
    }

    override suspend fun remove(group: ToolGroupId, versionId: String) = Result.success(Unit)

    override suspend fun paths(catalog: ToolGroupCatalog) = Result.success(PinnedTools(emptyMap(), emptyMap()))
}

internal fun componentTestPlanExecutor(store: RecordingComponentStore = RecordingComponentStore()): PlanExecutor =
    PlanExecutor(componentToolsHandler = ComponentToolsHandler(ToolManager(store)))

internal fun componentTestPlanExecutor(manager: ToolManager): PlanExecutor =
    PlanExecutor(componentToolsHandler = ComponentToolsHandler(manager))
