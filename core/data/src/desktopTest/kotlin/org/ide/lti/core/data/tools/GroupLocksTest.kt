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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolErrorException
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

class GroupLocksTest {
    /** Tool names a real build service reported (GET /api/v1/tools) once the catalog's groups were installed. */
    private val liveNames: List<String> =
        Json.parseToJsonElement(
            checkNotNull(javaClass.getResource("/live-service-tool-listing.json")) { "fixture missing" }.readText(),
        ).jsonArray.map { it.jsonObject.getValue("tool").jsonPrimitive.content }

    private class Transport(private val listing: ToolListResult) : RemoteTransportPort {
        override suspend fun execute(request: ToolExecutionRequest) = ToolExecutionResponse(0, "", "", 1L)

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = emptyFlow()

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = listing

        override suspend fun refreshTools(): ToolListResult = listing

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    private fun offering(names: List<String>) =
        Transport(ToolListResult.Tools(names.map { ToolStatusInfo(it, installed = true) }))

    @Test
    fun `every catalog tool the live service offers is accepted under the name the catalog uses`() = runTest {
        val transport = offering(liveNames)

        for (group in ToolGroupCatalog.allGroups) {
            val required = group.outputs.filter { it.requiredForProduct }.map { it.toolId }
            assertTrue(required.all { it in liveNames }, "the fixture lacks ${group.id.value}: $required")
            val outcome = transport.registrationOutcome(group, "v1")
            assertTrue(outcome.isSuccess, "${group.id.value}: ${outcome.exceptionOrNull()?.message}")
        }
    }

    @Test
    fun `a missing required tool is named and a service error is reported`() = runTest {
        val group = ToolGroupCatalog.EROFS_UTILS

        val missing = offering(liveNames - "mkfs.erofs").registrationOutcome(group, "v1")
        val error = (missing.exceptionOrNull() as ToolErrorException).toolError
        assertIs<ToolError.RegistrationFailed>(error)
        assertTrue("mkfs.erofs" in error.detail, error.detail)

        val broken = Transport(ToolListResult.ServiceError("boom")).registrationOutcome(group, "v1")
        assertTrue("boom" in (broken.exceptionOrNull() as ToolErrorException).toolError.message)
    }

    @Test
    fun `one lock per group, the same one every time`() {
        val locks = GroupLocks()

        assertSame(locks(ToolGroupCatalog.GH.id), locks(ToolGroupCatalog.GH.id))
        assertTrue(locks(ToolGroupCatalog.GH.id) !== locks(ToolGroupCatalog.APKTOOL.id))
    }
}
