/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.coroutines.flow.Flow
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.ToolRef

/** Explicit immutable tool selections for simulated builds; never rely on managed IDs. */
internal class PipelinePinnedToolStore(val pins: PinnedTools = defaultPins) : ToolStorePort {
    override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> = Result.success(pins)

    override suspend fun init(): Result<InitInfo> = error("Not used")

    override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = error("Not used")

    override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> = error("Not used")

    override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
        error("Not used")

    override fun install(request: InstallRequest): Flow<InstallEvent> = error("Not used")

    override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
        error("Not used")

    override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = error("Not used")

    companion object {
        val defaultPins: PinnedTools =
            ToolGroupCatalog.DEFAULT_GROUPS.flatMap { group ->
                group.outputs.flatMap { output ->
                    val path = "/tools/bin/${group.id.value}/test-version/${output.file}"
                    listOf(output.toolId to path) + output.aliases.keys.map { it to path }
                }
            }.toMap().let { paths -> PinnedTools(paths, paths.mapValues { "test-version" }) }
    }
}
