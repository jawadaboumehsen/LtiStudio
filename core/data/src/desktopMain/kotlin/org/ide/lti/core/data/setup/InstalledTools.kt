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

import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolComponentItem
import org.ide.lti.core.domain.setup.ToolEvidence
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ToolInventory

/*
 * What a finished setup plan left installed, as the tools matrix shows it, and the evidence the plan's outcome
 * carries. A tool's status comes from a fact (the tool store's inventory, or the file there), never from the plan
 * having returned.
 */

/**
 * [tools] as the tool store's [inventory] has them under [home]: a tool whose file the current version of its group
 * lacks is pending, one in a damaged version failed; a failure seen earlier in this run stays a failure.
 */
internal fun toolsFromInventory(
    tools: List<ToolComponentItem>,
    inventory: ToolInventory,
    home: String,
): List<ToolComponentItem> = tools.map { tool ->
    val group = ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == tool.id } }
    val output = group?.outputs?.firstOrNull { it.toolId == tool.id }
    val current = group?.id?.let { inventory.groups[it]?.current }
    val exists = output != null && current?.outputs.orEmpty().any { it.file == output.file }
    val status = when {
        !exists -> StepStatus.PENDING
        tool.status == StepStatus.FAILED -> StepStatus.FAILED
        current?.intact != true -> StepStatus.FAILED
        else -> StepStatus.SUCCESS
    }
    val path = if (group != null && output != null && exists) {
        "$home/LtiRomTools/bin/${group.id.value}/current/${output.file}"
    } else {
        null
    }
    tool.copy(path = path, status = status)
}

/**
 * [tool] once its file was looked for at [path]: found, it succeeded unless it failed earlier in this run; not found,
 * it keeps its status and loses its path.
 */
internal fun toolFromFile(tool: ToolComponentItem, path: String, exists: Boolean): ToolComponentItem = tool.copy(
    path = path.takeIf { exists },
    status = when {
        !exists -> tool.status
        tool.status == StepStatus.FAILED -> StepStatus.FAILED
        else -> StepStatus.SUCCESS
    },
)

/** The evidence a succeeded plan carries: each tool that ended ready, with its probe as the build would run it. */
internal fun planEvidence(tools: List<ToolComponentItem>, binDir: String, now: Long): Map<String, ToolEvidence> =
    tools.filter { it.status == StepStatus.SUCCESS }.associate { tool ->
        tool.id to ToolEvidence(
            toolId = tool.id,
            recipeGroup = ToolCapabilities.recipeGroupFor(tool.id),
            probeArgv = ToolCapabilities.probeArgumentsFor(tool.id, "$binDir/${tool.binaryName}"),
            acceptedExitCodes = ToolCapabilities.acceptedExitCodesFor(tool.id),
            isInstalled = true,
            isVerified = tool.lastTested != null,
            isFailedExecution = false,
            lastVerifiedTimestamp = now,
        )
    }
