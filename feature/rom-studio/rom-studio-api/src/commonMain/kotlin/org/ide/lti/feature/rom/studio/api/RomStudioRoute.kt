/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import kotlinx.serialization.Serializable
import org.ide.lti.core.model.run.StageId

/**
 * Public route contract for navigating to the ROM Studio screen.
 */
@Serializable
public data class RomStudioRoute(
    val workspaceId: String,
    val stageId: StageId? = null,
    val subobjectId: StudioSubobjectId? = null,
) {
    init {
        require(workspaceId.isNotBlank()) { "RomStudioRoute workspaceId must not be blank" }
    }

    public companion object {
        public const val ROUTE_PATTERN: String = "studio/{workspaceId}?stageId={stageId}&subobjectId={subobjectId}"

        public fun buildRoute(
            workspaceId: String,
            stageId: StageId? = null,
            subobjectId: StudioSubobjectId? = null,
        ): String {
            require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
            val params = mutableListOf<String>()
            if (stageId != null) params.add("stageId=${stageId.name}")
            if (subobjectId != null) params.add("subobjectId=${subobjectId.value}")
            return if (params.isEmpty()) {
                "studio/$workspaceId"
            } else {
                "studio/$workspaceId?${params.joinToString("&")}"
            }
        }
    }
}
