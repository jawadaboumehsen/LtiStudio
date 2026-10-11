/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.ReadOnlyCommands
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.data.tools.KotlinStoreLayout
import org.ide.lti.core.data.tools.ToolErrorException
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ToolchainSetupState
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.DurableOperationResult
import org.ide.lti.core.domain.tools.GroupLinks
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InitInfo
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.InstalledOutput
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.PinnedTools
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.RefListing
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ResolvedSourceCheck
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolInventory
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.core.domain.tools.ToolStorePort
import org.ide.lti.core.model.setup.SetupEnvironment
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolsStageTest {
    private class FakeToolStorePort : ToolStorePort {
        var statusResult: Result<ToolInventory> = Result.success(ToolInventory(emptyMap()))

        override suspend fun init(): Result<InitInfo> = Result.success(InitInfo())

        override suspend fun status(catalog: ToolGroupCatalog): Result<ToolInventory> = statusResult

        override suspend fun refs(group: ToolGroupId, repoUrl: String): Result<RefListing> =
            Result.success(RefListing(emptyList(), emptyList()))

        override suspend fun resolve(group: ToolGroupId, repoUrl: String, ref: ToolRef): Result<ResolvedSourceCheck> =
            Result.success(ResolvedSourceCheck(commit = "1".repeat(40)))

        override fun install(request: InstallRequest): Flow<InstallEvent> = emptyFlow()

        override suspend fun result(opId: String): Result<DurableOperationResult?> = Result.success(null)

        override suspend fun use(group: ToolGroupId, versionId: String, choice: ChoiceUpdate): Result<GroupLinks> =
            Result.success(GroupLinks(current = versionId))

        override suspend fun remove(group: ToolGroupId, versionId: String): Result<Unit> = Result.success(Unit)

        override suspend fun paths(catalog: ToolGroupCatalog): Result<PinnedTools> =
            Result.success(PinnedTools(paths = emptyMap(), versions = emptyMap()))
    }

    private fun createPinnedVersion(
        group: ToolGroup,
        intact: Boolean = true,
        overrideId: String? = null,
    ): InstalledVersion {
        val (versionId, source) =
            when (val s = group.source) {
                is ToolSource.Git -> {
                    val pinned = ResolvedSource.Git(s.recommendedUrl, s.pinnedRef, s.pinnedCommit)
                    KotlinStoreLayout.computeVersionId(pinned) to pinned
                }
                ToolSource.Release -> {
                    val rel = group.recipe as? RecipeConfig.Release
                    val firstVer = rel?.versions?.firstOrNull()
                    val pinned =
                        ResolvedSource.Release(
                            version = firstVer?.version.orEmpty(),
                            url = firstVer?.url.orEmpty(),
                            sha256 = firstVer?.sha256.orEmpty(),
                            member = firstVer?.archiveMember.orEmpty(),
                        )
                    KotlinStoreLayout.computeVersionId(pinned) to pinned
                }
            }
        val outputs = group.outputs.map { InstalledOutput(it.file, 1000L, "exec") }
        return InstalledVersion(
            group = group.id,
            id = overrideId ?: versionId,
            label = overrideId ?: versionId,
            source = source,
            recipe = RecipeSummary(group.recipe::class.simpleName ?: "recipe", 1),
            outputs = outputs,
            builtAt = "2026-10-06T00:00:00Z",
            intact = intact,
        )
    }

    private fun createTestContext(): Triple<CheckContext, CheckScope, MutableStateFlow<ToolchainSetupState>> {
        val cli = WslCliExecutor()
        val readOnlyCommands = ReadOnlyCommands(cli)
        val stateFlow = MutableStateFlow(ToolchainSetupState())
        val scope = CheckScope("Ubuntu", "test-check", stateFlow) { "test-check" }
        val env = SetupEnvironment("Ubuntu", 2, "ubuntu", "24.04", "user", "/home/user")
        val ctx =
            CheckContext(
                environment = env,
                readOnlyCommands = readOnlyCommands,
                userHome = "/home/user",
                workDir = "/home/user/LtiRomWorkDir",
                binDir = "/home/user/LtiRomTools/bin",
                extDir = "/home/user/LtiRomTools/external",
            )
        return Triple(ctx, scope, stateFlow)
    }

    @Test
    fun toolsStageReturnsSuccessWhenAllGroupsReady() = runTest {
        val groupStates =
            ToolGroupCatalog.allGroups.associate { group ->
                val installed = createPinnedVersion(group, intact = true)
                group.id to
                    GroupState(
                        group = group.id,
                        current = installed,
                        installed = listOf(installed),
                    )
            }
        val port = FakeToolStorePort()
        port.statusResult = Result.success(ToolInventory(groupStates))
        val manager = ToolManager(port)

        val (ctx, scope, stateFlow) = createTestContext()
        val stage = ToolsStage(toolManager = manager)
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Success, "Expected Success, got $outcome")
        assertEquals("${ToolGroupCatalog.allGroups.size} tool groups ready", outcome.description)

        // Verify tools-matrix paths are <home>/LtiRomTools/bin/<group>/current/<file>
        val matrix = stateFlow.value.toolsMatrix
        assertTrue(matrix.isNotEmpty(), "Tools matrix must be populated")
        for (item in matrix) {
            val group = ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == item.id } }
            assertNotNull(group, "Must find catalog group for ${item.id}")
            val output = group.outputs.first { it.toolId == item.id }
            val expectedPath = "/home/user/LtiRomTools/bin/${group.id.value}/current/${output.file}"
            assertEquals(expectedPath, item.path, "Path must follow spec 009 store layout")
        }
    }

    @Test
    fun toolsStageReturnsPendingWhenTargetMismatch() = runTest {
        val groupStates =
            ToolGroupCatalog.allGroups.associate { group ->
                val installed =
                    if (group.id.value == "apktool") {
                        createPinnedVersion(group, intact = true, overrideId = "v2.9.0-old")
                    } else {
                        createPinnedVersion(group, intact = true)
                    }
                group.id to
                    GroupState(
                        group = group.id,
                        current = installed,
                        installed = listOf(installed),
                    )
            }
        val port = FakeToolStorePort()
        port.statusResult = Result.success(ToolInventory(groupStates))
        val manager = ToolManager(port)

        val (ctx, scope) = createTestContext()
        val stage = ToolsStage(toolManager = manager)
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Pending, "Expected Pending for target mismatch, got $outcome")
        assertTrue(
            outcome.description.contains("apktool"),
            "Outcome must name the mismatched group: ${outcome.description}",
        )
    }

    @Test
    fun toolsStageReturnsFailedWhenSettingsJsonUnreadable() = runTest {
        val port = FakeToolStorePort()
        port.statusResult =
            Result.failure(
                ToolErrorException(ToolError.Internal("/home/user/LtiRomTools/settings.json can't be read")),
            )
        val manager = ToolManager(port)

        val (ctx, scope) = createTestContext()
        val stage = ToolsStage(toolManager = manager)
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed for unreadable settings.json, got $outcome")
        assertEquals("~/LtiRomTools/settings.json can't be read", outcome.description)
    }

    @Test
    fun toolsStageReturnsFailedWhenOutputDamaged() = runTest {
        val groupStates =
            ToolGroupCatalog.allGroups.associate { group ->
                val installed =
                    if (group.id.value == "android-tools") {
                        createPinnedVersion(group, intact = false)
                    } else {
                        createPinnedVersion(group, intact = true)
                    }
                group.id to
                    GroupState(
                        group = group.id,
                        current = installed,
                        installed = listOf(installed),
                    )
            }
        val port = FakeToolStorePort()
        port.statusResult = Result.success(ToolInventory(groupStates))
        val manager = ToolManager(port)

        val (ctx, scope) = createTestContext()
        val stage = ToolsStage(toolManager = manager)
        val outcome = stage.run(ctx, scope)

        assertTrue(outcome is StageOutcome.Failed, "Expected Failed when a tool needs repair, got $outcome")
        assertTrue(
            outcome.error?.contains("android-tools") == true || outcome.description.contains("android-tools"),
        )
    }
}
