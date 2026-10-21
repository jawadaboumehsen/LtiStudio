/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import org.ide.lti.core.model.setup.ToolRef
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KotlinToolStoreAdapterTest {
    private class FakeTransport : RemoteTransportPort {
        val requests = mutableListOf<ToolExecutionRequest>()
        var onExecute: (ToolExecutionRequest) -> ToolExecutionResponse = { req ->
            if (req.arguments.any { it.contains("settings.json") }) {
                ToolExecutionResponse(44, "", "", 10L)
            } else {
                ToolExecutionResponse(0, "", "", 10L)
            }
        }

        /** Lets a test hold a command while it does something else. */
        var gate: (suspend (ToolExecutionRequest) -> Unit)? = null

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            requests += request
            gate?.invoke(request)
            return onExecute(request)
        }

        /** What a streamed (long) command produces; by default nothing, which ends it without a result. */
        var onStream: (ToolExecutionRequest) -> Flow<StreamEvent> = { emptyFlow() }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = onStream(request)

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        var refreshToolsCalls = 0

        /** What the service offers after a reload: by default every tool of the catalog. */
        var reload: () -> ToolListResult = {
            ToolListResult.Tools(ToolGroupCatalog.allOutputs.map { ToolStatusInfo(it.toolId, installed = true) })
        }

        override suspend fun refreshTools(): ToolListResult {
            refreshToolsCalls++
            return reload()
        }

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private fun createAdapter(
        transport: RemoteTransportPort,
        serverDirProvider: suspend () -> String = { "/home/testuser/.ltirom/server/current" },
    ): KotlinToolStoreAdapter = KotlinToolStoreAdapter(
        transport = transport,
        serverDirProvider = serverDirProvider,
    )

    @Test
    fun `init creates base directories and reports legacy bin move`() = runTest {
        val transport = FakeTransport()
        transport.onExecute = {
            ToolExecutionResponse(0, "LEGACY_MOVED=bin.old-1234567\n", "", 5L)
        }
        val adapter = createAdapter(transport)
        val res = adapter.init()
        assertTrue(res.isSuccess)
        assertEquals("bin.old-1234567", res.getOrThrow().legacyBinMovedTo)
    }

    @Test
    fun `use creates symlink and registers in tools d`() = runTest {
        val transport = FakeTransport()
        val adapter = createAdapter(transport)
        val res = adapter.use(ToolGroupId("erofs-utils"), "v1.8.1", choice = ChoiceUpdate.Keep)
        assertTrue(res.isSuccess)
        assertEquals("v1.8.1", res.getOrThrow().current)

        val switchCmd =
            transport.requests.first {
                it.arguments.any { arg -> arg.contains("current.tmp") && arg.contains("tools.d") }
            }
        val lastArg = switchCmd.arguments.last()
        val expectedExec = "\"executable\":\"/home/testuser/LtiRomTools/bin/erofs-utils/current/mkfs.erofs\""
        assertTrue(lastArg.contains(expectedExec))
        kotlin.test.assertFalse(switchCmd.arguments.last().contains("\"executable\":\"'"))
        assertEquals(1, transport.refreshToolsCalls)
    }

    @Test
    fun `a switch is not reported as done while the service does not offer the tools`() = runTest {
        val transport = FakeTransport()
        val adapter = createAdapter(transport)

        transport.reload = { ToolListResult.Tools(emptyList()) }
        val missing = adapter.use(ToolGroupId("erofs-utils"), "v1.8.1", choice = ChoiceUpdate.Keep)
        val error = (missing.exceptionOrNull() as ToolErrorException).toolError
        assertIs<ToolError.RegistrationFailed>(error)
        assertTrue("mkfs.erofs" in error.message, error.message)

        transport.reload = { ToolListResult.ServiceError("boom") }
        val failed = adapter.use(ToolGroupId("erofs-utils"), "v1.8.1", choice = ChoiceUpdate.Keep)
        assertIs<ToolError.RegistrationFailed>((failed.exceptionOrNull() as ToolErrorException).toolError)
    }

    private val erofsVersionJson =
        """
        {"schema":1,"group":"erofs-utils","id":"v1.8.1","label":"v1.8.1",
         "source":{"kind":"git","repo":"https://example.com/erofs.git",
                   "commit":"abcdef1234567890abcdef1234567890abcdef12","submodules":{}},
         "recipe":{"type":"cmake","revision":1},
         "outputs":[{"file":"mkfs.erofs","size":100,"mode":"exec"}],"builtAt":"1000","intact":true}
        """.trimIndent()

    @Test
    fun `a switch that fails puts the previous saved pick back`() = runTest {
        val transport = FakeTransport()
        val writes = mutableListOf<String>()
        transport.onExecute = { req ->
            val script = req.arguments.lastOrNull().orEmpty()
            when {
                "base64 -d" in script -> {
                    writes += String(Base64.getDecoder().decode(script.substringAfter("echo '").substringBefore("'")))
                    ToolExecutionResponse(0, "", "", 1L)
                }
                "settings.json" in script -> ToolExecutionResponse(44, "", "", 1L)
                req.toolId == "cat" || "version.json" in script -> ToolExecutionResponse(0, erofsVersionJson, "", 1L)
                "current.tmp" in script && "tools.d" in script -> ToolExecutionResponse(1, "", "no space", 1L)
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }

        val result = createAdapter(transport).use(ToolGroupId("erofs-utils"), "v1.8.1", ChoiceUpdate.Record)

        assertTrue(result.isFailure)
        assertEquals(2, writes.size, "the pick was recorded, then taken back")
        assertTrue("erofs-utils" in writes[0])
        assertTrue("erofs-utils" !in writes[1], "no pick existed before, so none may remain: ${writes[1]}")
    }

    @Test
    fun `a switch that fails and cannot take its pick back says the saved pick is out of step`() = runTest {
        val transport = FakeTransport()
        var writes = 0
        transport.onExecute = { req ->
            val script = req.arguments.lastOrNull().orEmpty()
            when {
                "base64 -d" in script -> {
                    writes++
                    if (writes == 1) {
                        ToolExecutionResponse(0, "", "", 1L)
                    } else {
                        ToolExecutionResponse(1, "", "disk full", 1L)
                    }
                }
                "settings.json" in script -> ToolExecutionResponse(44, "", "", 1L)
                req.toolId == "cat" || "version.json" in script -> ToolExecutionResponse(0, erofsVersionJson, "", 1L)
                "current.tmp" in script && "tools.d" in script -> ToolExecutionResponse(1, "", "no space", 1L)
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }

        val result = createAdapter(transport).use(ToolGroupId("erofs-utils"), "v1.8.1", ChoiceUpdate.Record)

        val error = (result.exceptionOrNull() as ToolErrorException).toolError
        assertEquals(2, writes, "the restore was attempted")
        assertTrue("could not be put back" in error.message, "the failed rollback is reported: ${error.message}")
        assertTrue("settings.json still names v1.8.1" in error.message, error.message)
    }

    @Test
    fun `use and remove of one group never run at the same time`() = runTest {
        val transport = FakeTransport()
        val inSwitch = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        transport.gate = { req ->
            if (req.arguments.any { "current.tmp" in it && "tools.d" in it }) {
                inSwitch.complete(Unit)
                release.await()
            }
        }
        val adapter = createAdapter(transport)
        val group = ToolGroupId("erofs-utils")

        val use = launch { adapter.use(group, "v1.8.1", ChoiceUpdate.Keep) }
        inSwitch.await()
        val remove = launch { adapter.remove(group, "v1.8.0") }
        runCurrent()
        assertTrue(transport.requests.none { r -> r.arguments.any { "rm -rf" in it } }, "remove waits for the switch")

        release.complete(Unit)
        advanceUntilIdle()
        use.join()
        remove.join()
        assertTrue(transport.requests.any { r -> r.arguments.any { "rm -rf" in it } }, "remove runs afterwards")
    }

    @Test
    fun `a cancel that arrives between two commands stops the installation before it goes on`() = runTest {
        val transport = FakeTransport()
        val atWorkspace = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        transport.gate = { req ->
            if (req.arguments.any { "op.json.tmp" in it }) {
                atWorkspace.complete(Unit)
                release.await()
            }
        }
        val adapter = createAdapter(transport)
        val request =
            InstallRequest(
                opId = "op-cancel",
                group = ToolGroupId("erofs-utils"),
                label = "v1.8.1",
                source = ResolvedSource.Git("https://example.com/erofs.git", ToolRef.Tag("v1.8.1"), "c".repeat(40)),
                recipe = ToolGroupCatalog.EROFS_UTILS.recipe,
                outputs = ToolGroupCatalog.EROFS_UTILS.outputs,
                select = true,
            )
        val events = mutableListOf<InstallEvent>()

        val install = launch { adapter.install(request).collect { events += it } }
        atWorkspace.await()
        val cancel = launch { adapter.cancel(ToolGroupId("erofs-utils")) }
        runCurrent() // the flag is set, the command in flight has not noticed
        release.complete(Unit)
        advanceUntilIdle()
        install.join()
        cancel.join()

        val finished = events.filterIsInstance<InstallEvent.Finished>().single()
        assertEquals(OperationResult.Cancelled, finished.result)
        assertTrue(transport.requests.none { r -> r.arguments.any { "worktree add" in it || "current.tmp" in it } })
    }

    @Test
    fun `remove deletes target version when not active`() = runTest {
        val transport = FakeTransport()
        val adapter = createAdapter(transport)
        val res = adapter.remove(ToolGroupId("erofs-utils"), "v1.8.0")
        assertTrue(res.isSuccess)

        val removeCmd = transport.requests.last()
        assertTrue(removeCmd.arguments.any { it.contains("rm -rf") && it.contains("v1.8.0") })
    }

    @Test
    fun `install reuses existing intact version without rebuilding`() = runTest {
        val transport = FakeTransport()
        val versionJson =
            """
                {
                  "schema": 1,
                  "group": "erofs-utils",
                  "id": "v1.8.1",
                  "label": "v1.8.1",
                  "source": {
                    "kind": "git",
                    "repo": "https://example.com/erofs.git",
                    "commit": "abcdef1234567890abcdef1234567890abcdef12",
                    "submodules": {}
                  },
                  "recipe": {"type": "cmake", "revision": 1},
                  "outputs": [{"file": "mkfs.erofs", "size": 100, "mode": "exec"}],
                  "builtAt": "1000",
                  "intact": true
                }
            """.trimIndent()
        transport.onExecute = { req ->
            if (req.toolId == "cat" || req.arguments.any { it.contains("version.json") }) {
                ToolExecutionResponse(0, versionJson, "", 5L)
            } else {
                ToolExecutionResponse(0, "", "", 5L)
            }
        }
        val adapter = createAdapter(transport)
        val request =
            InstallRequest(
                opId = "op-1",
                group = ToolGroupId("erofs-utils"),
                label = "v1.8.1",
                source =
                ResolvedSource.Git(
                    repoUrl = "https://example.com/erofs.git",
                    ref = ToolRef.Tag("v1.8.1"),
                    commit = "abcdef1234567890abcdef1234567890abcdef12",
                ),
                recipe = ToolGroupCatalog.EROFS_UTILS.recipe,
                outputs =
                listOf(
                    ToolOutput(
                        "mkfs.erofs",
                        "mkfs.erofs",
                        ToolOutputKind.NATIVE,
                        org.ide.lti.core.domain.setup.ToolProbe(listOf("--version")),
                    ),
                ),
                select = true,
            )

        val events = mutableListOf<InstallEvent>()
        adapter.install(request).collect { events += it }

        val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()
        assertNotNull(finished)
        assertTrue(finished.result is OperationResult.Reused)
        assertEquals("v1.8.1", (finished.result as OperationResult.Reused).versionId)
    }

    @Test
    fun `an intact cached install with a support directory is reused, not rebuilt`() = runTest {
        val group = ToolGroupCatalog.ANDROID_TOOLS
        val source = ResolvedSource.Git("https://example.com/at.git", ToolRef.Tag("v1"), "d".repeat(40))
        val versionData =
            VersionData(
                schema = 1,
                group = group.id.value,
                id = "v1",
                label = "v1",
                source = with(KotlinStoreLayout) { source.toData() },
                recipe = RecipeData(
                    type = with(KotlinStoreLayout) {
                        group.recipe.typeName()
                    },
                    revision = group.recipe.revision,
                ),
                outputs = emptyList(),
                builtAt = "1",
                intact = true,
            )
        val versionJson = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }.encodeToString(VersionData.serializer(), versionData)
        val checked = mutableListOf<String>()
        val transport = FakeTransport()
        transport.onExecute = { req ->
            val script = req.arguments.lastOrNull().orEmpty()
            when {
                req.toolId == "cat" || "version.json" in req.arguments.joinToString(" ") ->
                    ToolExecutionResponse(0, versionJson, "", 1L)
                // gki/ is a directory: a file-only test on it fails, exactly as on a real machine
                "gki/" in script -> {
                    checked += script
                    ToolExecutionResponse(if ("-f '" in script && "gki/" in script) 1 else 0, "", "", 1L)
                }
                // a probe of an installed tool answers with an exit code the catalog accepts for it
                group.outputs.any { req.toolId.endsWith("/${it.file}") } -> {
                    val out = group.outputs.first { req.toolId.endsWith("/${it.file}") }
                    ToolExecutionResponse(out.probe.acceptedExitCodes.first(), "", "", 1L)
                }
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }
        val request =
            InstallRequest(
                opId = "op-gki",
                group = group.id,
                label = "v1",
                source = source,
                recipe = group.recipe,
                outputs = group.outputs,
                supportFiles = group.supportFiles + group.outputs.flatMap { it.supportFiles },
                select = false,
            )

        val events = mutableListOf<InstallEvent>()
        createAdapter(transport).install(request).collect { events += it }

        assertTrue(request.supportFiles.contains("gki/"), "the catalog requires the gki/ directory")
        assertTrue(checked.isNotEmpty(), "support paths were checked")
        val finished = events.filterIsInstance<InstallEvent.Finished>().single()
        assertTrue(finished.result is OperationResult.Reused, "was ${finished.result}")
    }

    @Test
    fun `activating a version checks its support files and runs its probes first`() = runTest {
        val transport = FakeTransport()
        createAdapter(transport).use(ToolGroupCatalog.ANDROID_TOOLS.id, "v1", ChoiceUpdate.Keep)

        val precheck =
            transport.requests.map { it.arguments.lastOrNull().orEmpty() }
                .first { "realpath -m" in it && "timeout 10" in it }
        assertTrue("[ -e '/home/testuser/LtiRomTools/bin/android-tools/v1/gki/' ]" in precheck, precheck)
        assertTrue("grep -Eiq" in precheck, "probes must check what the tool prints, not only its exit code")
    }

    @Test
    fun `a folder named after the commit that an older recipe built is rebuilt, not collided with`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS
        val commit = "a".repeat(40)
        val source = ResolvedSource.Git("https://example.com/erofs.git", ToolRef.Tag("v1"), commit)

        fun versionJson(id: String, sourceCommit: String, recipeRevision: Int) = VersionData(
            schema = 1,
            group = group.id.value,
            id = id,
            label = "v1",
            source = with(KotlinStoreLayout) { ResolvedSource.Git(source.repoUrl, source.ref, sourceCommit).toData() },
            recipe = RecipeData(with(KotlinStoreLayout) { group.recipe.typeName() }, recipeRevision),
            outputs = emptyList(),
            builtAt = "1",
            intact = true,
        ).let { kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(VersionData.serializer(), it) }

        val ownId = "v1-" + KotlinStoreLayout.disambiguator(source)
        val transport = FakeTransport()
        transport.onExecute = { req ->
            val path = req.arguments.lastOrNull().orEmpty()
            when {
                req.toolId == "cat" && path.endsWith("/erofs-utils/v1/version.json") ->
                    ToolExecutionResponse(0, versionJson("v1", "b".repeat(40), group.recipe.revision), "", 1L)
                req.toolId == "cat" && path.endsWith("/erofs-utils/$ownId/version.json") ->
                    ToolExecutionResponse(0, versionJson(ownId, commit, group.recipe.revision - 1), "", 1L)
                req.toolId == "cat" -> ToolExecutionResponse(1, "", "", 1L)
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }
        val request =
            InstallRequest(
                opId = "op-recipe",
                group = group.id,
                label = "v1",
                source = source,
                recipe = group.recipe,
                outputs = group.outputs,
                select = false,
            )

        transport.onStream = { kotlinx.coroutines.flow.flowOf(StreamEvent.ExecutionFinished(0, 1L)) }

        val events = mutableListOf<InstallEvent>()
        createAdapter(transport).install(request).collect { events += it }

        val publish =
            transport.requests.map { it.arguments.lastOrNull().orEmpty() }
                .firstOrNull { "mv -T" in it && ownId in it && "ver_name=" in it }
        assertNotNull(publish, "the older build of this commit is replaced in place instead of failing on exit 3")
        val finished = events.filterIsInstance<InstallEvent.Finished>().single()
        assertEquals(OperationResult.Installed(ownId), finished.result)
    }

    @Test
    fun `a workspace that cannot be removed does not turn a committed installation into a failure`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS
        val source = ResolvedSource.Git("https://example.com/erofs.git", ToolRef.Tag("v1"), "a".repeat(40))
        val transport = FakeTransport()
        transport.onExecute = { req ->
            when {
                req.toolId == "cat" -> ToolExecutionResponse(1, "", "", 1L)
                req.toolId == "rm" && "/work/" in req.arguments.last() ->
                    ToolExecutionResponse(1, "", "Device or resource busy", 1L)
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }
        transport.onStream = { kotlinx.coroutines.flow.flowOf(StreamEvent.ExecutionFinished(0, 1L)) }
        val request =
            InstallRequest(
                opId = "op-cleanup",
                group = group.id,
                label = "v1",
                source = source,
                recipe = group.recipe,
                outputs = group.outputs,
                select = false,
            )

        val events = mutableListOf<InstallEvent>()
        createAdapter(transport).install(request).collect { events += it }

        val finished = events.filterIsInstance<InstallEvent.Finished>().single()
        assertEquals(OperationResult.Installed("v1"), finished.result)
        assertTrue(
            events.filterIsInstance<InstallEvent.Output>().any { "was not removed" in it.text },
            "the leftover workspace is reported as a warning",
        )
    }

    @Test
    fun `two forks at the same commit get separate folders instead of colliding`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS
        val commit = "a".repeat(40)
        val upstream = ResolvedSource.Git("https://example.com/upstream.git", ToolRef.Tag("v1"), commit)
        val forkA = ResolvedSource.Git("https://example.com/forkA.git", ToolRef.Tag("v1"), commit)
        val forkB = ResolvedSource.Git("https://example.com/forkB.git", ToolRef.Tag("v1"), commit)
        val idA = "v1-" + KotlinStoreLayout.disambiguator(forkA)
        val idB = "v1-" + KotlinStoreLayout.disambiguator(forkB)
        assertTrue(idA != idB, "the same commit in two repositories must name two folders")

        fun versionJson(id: String, source: ResolvedSource) = VersionData(
            schema = 1,
            group = group.id.value,
            id = id,
            label = "v1",
            source = with(KotlinStoreLayout) { source.toData() },
            recipe = RecipeData(with(KotlinStoreLayout) { group.recipe.typeName() }, group.recipe.revision),
            outputs = emptyList(),
            builtAt = "1",
            intact = true,
        ).let { kotlinx.serialization.json.Json { encodeDefaults = true }.encodeToString(VersionData.serializer(), it) }

        val transport = FakeTransport()
        transport.onExecute = { req ->
            val path = req.arguments.lastOrNull().orEmpty()
            when {
                req.toolId == "cat" && path.endsWith("/erofs-utils/v1/version.json") ->
                    ToolExecutionResponse(0, versionJson("v1", upstream), "", 1L)
                req.toolId == "cat" && path.endsWith("/erofs-utils/$idA/version.json") ->
                    ToolExecutionResponse(0, versionJson(idA, forkA), "", 1L)
                req.toolId == "cat" -> ToolExecutionResponse(1, "", "", 1L)
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }
        transport.onStream = { kotlinx.coroutines.flow.flowOf(StreamEvent.ExecutionFinished(0, 1L)) }
        val request =
            InstallRequest(
                opId = "op-forks",
                group = group.id,
                label = "v1",
                source = forkB,
                recipe = group.recipe,
                outputs = group.outputs,
                select = false,
            )

        val events = mutableListOf<InstallEvent>()
        createAdapter(transport).install(request).collect { events += it }

        val finished = events.filterIsInstance<InstallEvent.Finished>().single()
        assertEquals(OperationResult.Installed(idB), finished.result)
    }

    @Test
    fun `paths resolves to immutable version directory when active version present`() = runTest {
        val transport = FakeTransport()
        transport.onExecute = { req ->
            if (req.toolId == "readlink") {
                ToolExecutionResponse(0, "v1.8.1\n", "", 5L)
            } else {
                ToolExecutionResponse(0, "", "", 5L)
            }
        }
        val adapter = createAdapter(transport)
        val res = adapter.paths(ToolGroupCatalog)
        assertTrue(res.isSuccess)
        val pinned = res.getOrThrow()
        assertTrue(pinned.paths["mkfs.erofs"]?.contains("/v1.8.1/mkfs.erofs") == true)
        kotlin.test.assertFalse(pinned.paths["mkfs.erofs"]?.contains("/current/mkfs.erofs") == true)
    }

    @Test
    fun `pinning for a ROM job refuses a version whose support files or probes fail`() = runTest {
        val transport = FakeTransport()
        var supportPresent = true
        val checks = mutableListOf<String>()
        transport.onExecute = { req ->
            val script = req.arguments.lastOrNull().orEmpty()
            when {
                req.toolId == "readlink" -> ToolExecutionResponse(0, "v1\n", "", 1L)
                "timeout 10" in script -> {
                    checks += script
                    // The support directory is deleted after setup passed.
                    val damaged = !supportPresent && "gki/" in script
                    ToolExecutionResponse(if (damaged) 1 else 0, "", "", 1L)
                }
                else -> ToolExecutionResponse(0, "", "", 1L)
            }
        }
        val adapter = createAdapter(transport)

        assertTrue(adapter.paths(ToolGroupCatalog).isSuccess)
        assertTrue(
            checks.any { "gki/" in it && "grep -Eiq" in it },
            "support files and probes are part of the pin check",
        )

        supportPresent = false
        val damaged = adapter.paths(ToolGroupCatalog)
        assertTrue(damaged.isFailure, "a job must not receive paths of a version that no longer validates")
        assertIs<ToolError.UnsupportedLayout>((damaged.exceptionOrNull() as ToolErrorException).toolError)
    }

    @Test
    fun `remove rejects traversal and reserved version ids`() = runTest {
        val transport = FakeTransport()
        val adapter = createAdapter(transport)
        assertTrue(adapter.remove(ToolGroupId("erofs-utils"), "../other").isFailure)
        assertTrue(adapter.remove(ToolGroupId("erofs-utils"), "current").isFailure)
        assertTrue(adapter.remove(ToolGroupId("erofs-utils"), "previous").isFailure)
        assertTrue(adapter.remove(ToolGroupId("erofs-utils"), "v1;rm -rf /").isFailure)
        assertTrue(adapter.remove(ToolGroupId("erofs-utils"), "v1'injection").isFailure)
    }

    @Test
    fun `use rejects invalid and injection version ids`() = runTest {
        val transport = FakeTransport()
        val adapter = createAdapter(transport)
        assertTrue(adapter.use(ToolGroupId("erofs-utils"), "../other").isFailure)
        assertTrue(adapter.use(ToolGroupId("erofs-utils"), "current").isFailure)
        assertTrue(adapter.use(ToolGroupId("erofs-utils"), "v1;bad").isFailure)
        assertTrue(adapter.use(ToolGroupId("erofs-utils"), "v1'bad").isFailure)
    }

    @Test
    fun `paths never includes mutable current fallback when version is absent`() = runTest {
        val transport = FakeTransport()
        transport.onExecute = { req ->
            if (req.toolId == "readlink") {
                ToolExecutionResponse(1, "", "No such file", 5L)
            } else {
                ToolExecutionResponse(0, "", "", 5L)
            }
        }
        val adapter = createAdapter(transport)
        val res = adapter.paths(ToolGroupCatalog)
        assertTrue(res.isFailure)
    }
}
