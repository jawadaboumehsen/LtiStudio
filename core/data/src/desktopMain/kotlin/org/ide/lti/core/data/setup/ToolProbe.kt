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

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunPurpose
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import org.ide.lti.core.domain.setup.ToolCapabilities
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolOutputKind

/** Where a tool's binary is, and whether it is a jar (run with `java -jar`). */
internal data class ToolBinary(val path: String, val isJar: Boolean)

/** Whether the binary is there, and whether its probe (`--version` or the tool's own) answered as expected. */
internal data class ToolProbeResult(val exists: Boolean, val verified: Boolean)

/**
 * [toolId]'s installed binary under [home]: the current version of its group (`LtiRomTools/bin/<group>/current/…`),
 * or [fallbackName] in the flat bin folder for a tool no group builds.
 */
internal fun toolBinary(home: String, toolId: String, fallbackName: String): ToolBinary {
    val group = ToolGroupCatalog.allGroups.firstOrNull { g -> g.outputs.any { it.toolId == toolId } }
    val output = group?.outputs?.firstOrNull { it.toolId == toolId }
    return if (group != null && output != null) {
        ToolBinary("$home/LtiRomTools/bin/${group.id.value}/current/${output.file}", output.kind == ToolOutputKind.JAR)
    } else {
        ToolBinary("$home/LtiRomTools/bin/$fallbackName", isJar = false)
    }
}

/** Checks one installed tool in WSL: the file is there (readable jar or executable) and its probe passes. */
internal class ToolProbe(private val transport: RemoteTransportPort) {
    suspend fun probe(toolId: String, binary: ToolBinary): ToolProbeResult {
        val exists = run("test", listOf(if (binary.isJar) "-r" else "-x", binary.path)) == 0
        if (!exists) return ToolProbeResult(exists = false, verified = false)
        val args = ToolCapabilities.probeArgumentsFor(toolId)
        val exitCode = if (binary.isJar) run("java", listOf("-jar", binary.path) + args) else run(binary.path, args)
        return ToolProbeResult(exists = true, verified = ToolCapabilities.isAcceptedExitCode(toolId, exitCode))
    }

    private suspend fun run(tool: String, arguments: List<String>): Int = transport.execute(
        ToolExecutionRequest(toolId = tool, arguments = arguments, workingDirectory = "/", purpose = RunPurpose.SETUP),
    ).exitCode
}
