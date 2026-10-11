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

import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class KotlinToolStoreAdapterResilienceTest {
    private class Transport : RemoteTransportPort {
        var gate: (suspend (ToolExecutionRequest) -> Unit)? = null
        var health: WslServerInfo? = null

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            gate?.invoke(request)
            return ToolExecutionResponse(0, "", "", 1L)
        }

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun checkHealth(): WslServerInfo? = health

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList<ToolStatusInfo>())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList<ToolStatusInfo>())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private fun info(activeWork: List<String>?) = WslServerInfo(
        status = "UP",
        distro = "Ubuntu",
        kernelRelease = "6",
        architecture = "x86_64",
        javaVersion = "21",
        serverUptimeMs = 1L,
        activeWork = activeWork,
    )

    private fun erofsRequest(op: String) = ToolGroupCatalog.EROFS_UTILS.let { group ->
        InstallRequest(
            opId = op,
            group = group.id,
            label = "v1",
            source = ResolvedSource.Git("https://example.com/erofs.git", ToolRef.Tag("v1"), "e".repeat(40)),
            recipe = group.recipe,
            outputs = group.outputs,
            select = true,
        )
    }

    /** In a coroutine of its own, as in the app: cancelling an install must not cancel whoever started it. */
    private suspend fun KotlinToolStoreAdapter.runInstall(request: InstallRequest): List<InstallEvent> =
        CoroutineScope(Dispatchers.Default).async {
            val events = mutableListOf<InstallEvent>()
            install(request).collect { events += it }
            events
        }.await()

    @Test
    fun `a process that could not be confirmed stopped blocks the group only until the service says it is gone`() =
        runBlocking<Unit> {
            val transport = Transport()
            val adapter =
                KotlinToolStoreAdapter(transport = transport, serverDirProvider = { "/home/u/.ltirom/server/current" })
            var failOnce = true
            transport.gate = { request ->
                if (failOnce && request.arguments.any { "op.json.tmp" in it }) {
                    failOnce = false
                    throw RemoteCancellationUnconfirmedException("exec-1")
                }
            }

            assertFailsWith<RemoteCancellationUnconfirmedException> { adapter.runInstall(erofsRequest("op-1")) }

            transport.health = info(listOf("execute:exec-1")) // the service still tracks it
            assertFailsWith<RemoteCancellationUnconfirmedException> { adapter.runInstall(erofsRequest("op-2")) }
            assertTrue(adapter.cancel(ToolGroupCatalog.EROFS_UTILS.id).isFailure, "cancelling does not clear it either")

            transport.health = null // no answer is not "gone"
            assertFailsWith<RemoteCancellationUnconfirmedException> { adapter.runInstall(erofsRequest("op-3")) }
            transport.health = info(null) // a service that does not report its work cannot confirm either
            assertFailsWith<RemoteCancellationUnconfirmedException> { adapter.runInstall(erofsRequest("op-4")) }

            transport.health = info(emptyList()) // it ended (or the service restarted and no longer tracks it)
            val events = adapter.runInstall(erofsRequest("op-5"))
            assertTrue(events.any { it is InstallEvent.Started }, "the group works again")
            assertTrue(events.filterIsInstance<InstallEvent.Finished>().isNotEmpty())
            assertTrue(adapter.cancel(ToolGroupCatalog.EROFS_UTILS.id).isSuccess)
        }

    @Test
    fun `cancelling several stuck operations stays within one budget, not one budget each`() = runBlocking<Unit> {
        val transport = Transport()
        // Work that ignores cancellation, as a hung remote call would.
        transport.gate = { request ->
            if (request.arguments.any { "op.json.tmp" in it }) withContext(NonCancellable) { delay(60_000) }
        }
        val adapter =
            KotlinToolStoreAdapter(transport = transport, serverDirProvider = { "/home/u/.ltirom/server/current" })
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            val gh = ToolGroupCatalog.GH
            val ghRelease = (gh.recipe as org.ide.lti.core.domain.setup.RecipeConfig.Release).versions.first()
            val ghRequest =
                InstallRequest(
                    opId = "op-gh",
                    group = gh.id,
                    label = ghRelease.version,
                    source = ResolvedSource.Release(
                        ghRelease.version,
                        ghRelease.url,
                        ghRelease.sha256,
                        ghRelease.archiveMember,
                    ),
                    recipe = gh.recipe,
                    outputs = gh.outputs,
                    select = true,
                )
            scope.launch { adapter.runInstall(erofsRequest("op-erofs")) }
            scope.launch { adapter.runInstall(ghRequest) }
            delay(500) // both are stuck in the workspace step

            val started = System.nanoTime()
            val results = adapter.cancelAll(budgetMs = 800L)
            val tookMs = (System.nanoTime() - started) / 1_000_000

            assertEquals(2, results.size)
            assertTrue(results.all { it.isFailure }, "stuck work is reported as not cancelled, not waited for")
            assertTrue(tookMs < 1_400, "two stuck groups took ${tookMs}ms for an 800ms budget")
        } finally {
            scope.cancel()
        }
    }
}
