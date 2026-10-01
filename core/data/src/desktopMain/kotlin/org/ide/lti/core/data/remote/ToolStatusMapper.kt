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
import org.ide.lti.core.domain.ports.ToolStatus

public object ToolStatusMapper {
    public fun toDomain(dto: ToolStatusInfo): ToolStatus = ToolStatus(
        tool = dto.tool,
        installed = dto.installed,
        path = dto.path,
        source = dto.source?.let { parseSource(it) },
        reason = dto.reason,
    )

    public fun toDomain(result: ToolListResult): ToolRegistryResult = when (result) {
        is ToolListResult.Tools -> ToolRegistryResult.Tools(result.list.map { toDomain(it) })
        is ToolListResult.ServiceError -> ToolRegistryResult.ServiceError(result.reason)
    }

    private fun parseSource(source: String): ToolSource? = when (source.uppercase()) {
        "DYNAMIC" -> ToolSource.DYNAMIC
        "PINNED" -> ToolSource.PINNED
        "SYSTEM_PATH" -> ToolSource.SYSTEM_PATH
        else -> null
    }
}
