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

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.model.setup.ToolRef

public data class InitInfo(val legacyBinMovedTo: String? = null, val deletedPaths: List<String> = emptyList())

public data class TagRefInfo(val name: String, val commit: String)

public data class BranchRefInfo(val name: String, val commit: String)

public data class RefListing(val tags: List<TagRefInfo>, val branches: List<BranchRefInfo>)

public data class ResolvedSourceCheck(
    val commit: String,
    val submodules: Map<String, String> = emptyMap(),
    val layoutMissing: List<String> = emptyList(),
)

public data class InstallRequest(
    val opId: String,
    val group: ToolGroupId,
    val label: String,
    val source: ResolvedSource,
    val recipe: RecipeConfig,
    val outputs: List<ToolOutput>,
    val supportFiles: List<String> = emptyList(),
    val packages: List<String> = emptyList(),
    val select: Boolean = true,
    val forceNew: Boolean = false,
    val choice: ChoiceUpdate = ChoiceUpdate.Keep,
)

public sealed interface InstallEvent {
    public data class Started(val runId: String) : InstallEvent

    /** The install moved on to [phase]; sent by the store as it really gets there. */
    public data class Phase(val phase: OperationPhase) : InstallEvent

    public data class Output(val text: String, val isError: Boolean = false) : InstallEvent

    public data class Finished(val result: OperationResult) : InstallEvent
}

public data class GroupLinks(val current: String?, val selection: String? = null)

public data class DurableOperationResult(val versionId: String?, val opId: String?, val reused: Boolean)

public const val DEFAULT_CANCEL_BUDGET_MS: Long = 10_000L

public interface ToolStorePort {
    public suspend fun init(): Result<InitInfo>

    public suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory>

    public suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing>

    public suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck>

    public fun install(request: InstallRequest): Flow<InstallEvent>

    public suspend fun result(opId: String): Result<DurableOperationResult?> = Result.success(null)

    /** Cancels the durable server run currently building [group], when there is one. */
    public suspend fun cancel(group: ToolGroupId, budgetMs: Long = DEFAULT_CANCEL_BUDGET_MS): Result<Unit> =
        Result.failure(UnsupportedOperationException("Tool operation cancellation is not supported"))

    /** Cancels every running operation within one overall [budgetMs]. */
    public suspend fun cancelAll(budgetMs: Long = DEFAULT_CANCEL_BUDGET_MS): List<Result<Unit>> = emptyList()

    public suspend fun use(
        group: ToolGroupId,
        versionId: String,
        choice: ChoiceUpdate = ChoiceUpdate.Record,
    ): Result<GroupLinks>

    public suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit>

    public suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools>

    public companion object {
        public val NoOp: ToolStorePort =
            object : ToolStorePort {
                override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

                override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = Result.success(
                    ToolInventory(emptyMap()),
                )

                override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> = Result.success(
                    RefListing(emptyList(), emptyList()),
                )

                override suspend fun resolve(
                    group: ToolGroupId,
                    repoUrl: String,
                    ref: ToolRef,
                ): Result<ResolvedSourceCheck> = Result.success(
                    ResolvedSourceCheck(""),
                )

                override fun install(request: InstallRequest): Flow<InstallEvent> = kotlinx.coroutines.flow.emptyFlow()

                override suspend fun use(
                    group: ToolGroupId,
                    versionId: String,
                    choice: ChoiceUpdate,
                ): Result<GroupLinks> = Result.success(
                    GroupLinks(null, null),
                )

                override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

                override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(
                    PinnedTools(emptyMap(), emptyMap()),
                )
            }
    }
}
