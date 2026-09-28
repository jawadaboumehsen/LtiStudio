package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult

@Serializable
private data class ToolManifestDto(val name: String, val executable: String, val description: String? = null)

public class ToolPublicationAdapter(
    private val transport: RemoteTransportPort,
    private val expectedTools: Set<String> = DEFAULT_EXPECTED_TOOLS,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) : ToolPublicationPort {

    public companion object {
        public val DEFAULT_EXPECTED_TOOLS: Set<String> = setOf(
            "payload-dumper-go",
            "lpunpack",
            "lpmake",
            "simg2img",
            "erofsfuse",
            "mkfs.erofs",
            "avbtool",
            "unpack_bootimg",
            "img2sdat",
            "signapk",
        )
    }

    override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
        val normalizedBin = binDir.trimEnd('/')
        val findRes = transport.execute(
            ToolExecutionRequest(
                toolId = "find",
                arguments = listOf(normalizedBin, "-maxdepth", "1", "-type", "f"),
                workingDirectory = "/",
            ),
        )

        val published = mutableSetOf<String>()
        val candidates = if (findRes.exitCode == 0) findRes.stdout.lines() else emptyList()
        candidates.map { it.trim() }
            .filter { it.isNotBlank() }
            .map { path -> path to path.substringAfterLast('/') }
            .filterNot { (_, fileName) -> fileName.startsWith(".") || fileName.endsWith(".json") }
            .map { (path, fileName) -> path to fileName.removeSuffix(".jar") }
            .filter { (_, toolName) -> toolIds.isEmpty() || toolName in toolIds }
            .forEach { (path, toolName) ->
                val manifest = ToolManifestDto(
                    name = toolName,
                    executable = path,
                    description = "Published binary for $toolName",
                )
                val targetPath = "~/.ltirom/tools.d/$toolName.json"
                if (transport.uploadFile(targetPath, json.encodeToString(manifest).encodeToByteArray())) {
                    published.add(toolName)
                }
            }

        transport.refreshTools()

        val failed = (toolIds - published).associateWith { "Not published" }
        return PublicationReport(
            requested = toolIds,
            registered = published,
            failed = failed,
            pruned = emptySet(),
        )
    }

    override suspend fun resolvedTools(): ToolRegistryResult {
        val toolsRes = transport.listTools()
        return ToolStatusMapper.toDomain(toolsRes)
    }
}
