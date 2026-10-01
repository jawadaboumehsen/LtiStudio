/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.ToolStatusInfo
import org.ide.lti.core.domain.ports.ToolRegistryResult
import org.ide.lti.core.domain.ports.ToolSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class ToolStatusMapperTest {

    @Test
    fun `maps tool status info with dynamic source`() {
        val dto = ToolStatusInfo(
            tool = "erofsfuse",
            installed = true,
            path = "/home/user/.ltirom/bin/erofsfuse",
            capabilities = listOf("fuse"),
            source = "DYNAMIC",
            reason = null,
        )

        val domain = ToolStatusMapper.toDomain(dto)

        assertEquals("erofsfuse", domain.tool)
        assertEquals(true, domain.installed)
        assertEquals("/home/user/.ltirom/bin/erofsfuse", domain.path)
        assertEquals(ToolSource.DYNAMIC, domain.source)
        assertNull(domain.reason)
    }

    @Test
    fun `maps tool status info with pinned and system path sources`() {
        val pinnedDto = ToolStatusInfo(
            tool = "adb",
            installed = true,
            path = "/usr/bin/adb",
            source = "PINNED",
            reason = "fallback",
        )
        val systemDto = ToolStatusInfo(
            tool = "fastboot",
            installed = false,
            path = "",
            source = "SYSTEM_PATH",
            reason = "not found",
        )
        val unknownDto = ToolStatusInfo(
            tool = "custom",
            installed = false,
            path = "",
            source = "UNKNOWN_SOURCE",
            reason = null,
        )

        assertEquals(ToolSource.PINNED, ToolStatusMapper.toDomain(pinnedDto).source)
        assertEquals("fallback", ToolStatusMapper.toDomain(pinnedDto).reason)
        assertEquals(ToolSource.SYSTEM_PATH, ToolStatusMapper.toDomain(systemDto).source)
        assertNull(ToolStatusMapper.toDomain(unknownDto).source)
    }

    @Test
    fun `maps tool list result tools to domain tool registry result tools`() {
        val result = ToolListResult.Tools(
            listOf(
                ToolStatusInfo(
                    tool = "signapk",
                    installed = true,
                    path = "/tools/signapk.jar",
                    source = "DYNAMIC",
                    reason = null,
                ),
                ToolStatusInfo(
                    tool = "lpmake",
                    installed = true,
                    path = "/bin/lpmake",
                    source = "PINNED",
                    reason = null,
                ),
            ),
        )

        val domainResult = ToolStatusMapper.toDomain(result)

        assertIs<ToolRegistryResult.Tools>(domainResult)
        assertEquals(2, domainResult.list.size)
        assertEquals("signapk", domainResult.list[0].tool)
        assertEquals(ToolSource.DYNAMIC, domainResult.list[0].source)
        assertEquals("lpmake", domainResult.list[1].tool)
        assertEquals(ToolSource.PINNED, domainResult.list[1].source)
    }

    @Test
    fun `maps tool list result service error to domain tool registry result service error`() {
        val result = ToolListResult.ServiceError("Connection refused: 9090")

        val domainResult = ToolStatusMapper.toDomain(result)

        assertIs<ToolRegistryResult.ServiceError>(domainResult)
        assertEquals("Connection refused: 9090", domainResult.reason)
    }
}
