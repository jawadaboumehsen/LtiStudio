package org.ide.lti.core.data.remote

import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolListResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.setup.ToolCatalog
import org.ide.lti.core.data.setup.ToolKind
import org.ide.lti.core.domain.ports.PublicationReport
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolRegistryResult
import kotlin.coroutines.cancellation.CancellationException

@Serializable
private data class ToolManifestDto(
    val name: String,
    val executable: String,
    val description: String? = null,
)

/**
 * Adapter implementing [ToolPublicationPort] for daemon tool manifest generation, upload, and pruning.
 *
 * Implements:
 * - Direct ID-based publication against [ToolCatalog] (FR-016–019, FR-028)
 * - Kind-aware verification: native (`test -x`) and jar (`test -r`)
 * - Live daemon dynamic resolution confirmation (`source == DYNAMIC`, `path == expectedPath`)
 * - Safe app-created manifest pruning (research R6)
 */
public class ToolPublicationAdapter(
    private val transport: RemoteTransportPort,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
    private val log: (String) -> Unit = {},
) : ToolPublicationPort {

    private sealed interface UploadResult {
        data class Success(val expectedPath: String) : UploadResult
        data class Failed(val reason: String) : UploadResult
    }

    override suspend fun publish(binDir: String, toolIds: Set<String>): PublicationReport {
        val normalizedBin = binDir.trimEnd('/')
        val failed = mutableMapOf<String, String>()
        val uploadedTools = mutableMapOf<String, String>()

        for (toolId in toolIds) {
            when (val result = uploadCatalogTool(toolId, normalizedBin)) {
                is UploadResult.Success -> uploadedTools[toolId] = result.expectedPath
                is UploadResult.Failed -> failed[toolId] = result.reason
            }
        }

        val pruned = pruneOldManifests(normalizedBin)
        val registered = verifyDynamicRegistrations(uploadedTools, failed)

        return PublicationReport(
            requested = toolIds,
            registered = registered,
            failed = failed,
            pruned = pruned,
        )
    }

    private suspend fun uploadCatalogTool(toolId: String, normalizedBin: String): UploadResult {
        val entry = ToolCatalog.entryFor(toolId)
            ?: return UploadResult.Failed("not in catalog")
        val expectedPath = "$normalizedBin/${entry.binaryName}"
        val checkFlag = if (entry.kind == ToolKind.NATIVE) "-x" else "-r"

        val failureReason = checkToolExecutable(checkFlag, expectedPath, entry.kind)
            ?: uploadManifestFile(toolId, expectedPath)

        return if (failureReason == null) {
            UploadResult.Success(expectedPath)
        } else {
            UploadResult.Failed(failureReason)
        }
    }

    private suspend fun checkToolExecutable(checkFlag: String, expectedPath: String, kind: ToolKind): String? {
        val exitCode = try {
            transport.execute(
                ToolExecutionRequest(
                    toolId = "test",
                    arguments = listOf(checkFlag, expectedPath),
                    workingDirectory = "/",
                ),
            ).exitCode
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "check error: ${e.message}"
        }
        return if (exitCode != 0) {
            if (kind == ToolKind.NATIVE) "not executable" else "not readable"
        } else {
            null
        }
    }

    private suspend fun uploadManifestFile(toolId: String, expectedPath: String): String? {
        val manifest = ToolManifestDto(
            name = toolId,
            executable = expectedPath,
            description = "Published binary for $toolId",
        )
        val manifestBytes = json.encodeToString(manifest).encodeToByteArray()
        val uploaded = try {
            transport.uploadFile("~/.ltirom/tools.d/$toolId.json", manifestBytes)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return "upload failed: ${e.message}"
        }
        return if (uploaded) null else "upload failed"
    }

    private suspend fun verifyDynamicRegistrations(
        uploadedTools: Map<String, String>,
        failed: MutableMap<String, String>,
    ): Set<String> {
        val registered = mutableSetOf<String>()
        val refreshRes = try {
            transport.refreshTools()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolListResult.ServiceError("refresh failed: ${e.message}")
        }

        when (refreshRes) {
            is ToolListResult.Tools -> {
                val toolsByName = refreshRes.list.associateBy { it.tool }
                for ((toolId, expectedPath) in uploadedTools) {
                    val status = toolsByName[toolId]
                    when {
                        status == null || !status.installed -> {
                            failed[toolId] = status?.reason ?: "manifest rejected by daemon"
                        }
                        status.source != "DYNAMIC" || status.path != expectedPath -> {
                            failed[toolId] = "resolved elsewhere: ${status.path}"
                        }
                        else -> registered.add(toolId)
                    }
                }
            }
            is ToolListResult.ServiceError -> {
                for (toolId in uploadedTools.keys) {
                    failed[toolId] = "service error: ${refreshRes.reason}"
                }
            }
        }
        return registered
    }

    private suspend fun pruneOldManifests(binDir: String): Set<String> {
        val manifestPaths = queryManifestPaths()
        if (manifestPaths.isEmpty()) return emptySet()

        val prunedNames = mutableSetOf<String>()
        for (path in manifestPaths) {
            val manifest = readManifest(path) ?: continue
            if (shouldPrune(manifest, binDir) && removeManifest(path, manifest.name)) {
                prunedNames.add(manifest.name)
            }
        }
        return prunedNames
    }

    private suspend fun queryManifestPaths(): List<String> {
        val listRes = try {
            transport.execute(
                ToolExecutionRequest(
                    toolId = "find",
                    arguments = listOf("~/.ltirom/tools.d", "-maxdepth", "1", "-name", "*.json"),
                    workingDirectory = "/",
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

        return if (listRes != null && listRes.exitCode == 0 && listRes.stdout.isNotBlank()) {
            listRes.stdout.lines().map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }
    }

    private suspend fun readManifest(path: String): ToolManifestDto? {
        val bytes = try {
            transport.downloadFile(path)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        } ?: return null

        return runCatching {
            json.decodeFromString<ToolManifestDto>(bytes.decodeToString())
        }.getOrNull()
    }

    private fun shouldPrune(manifest: ToolManifestDto, binDir: String): Boolean {
        val isAppPublished = manifest.description?.startsWith("Published binary for") == true
        val isUnderBin = manifest.executable.startsWith("$binDir/") || manifest.executable == binDir
        val isCatalog = ToolCatalog.isCatalogTool(manifest.name)
        return isAppPublished && isUnderBin && !isCatalog
    }

    private suspend fun removeManifest(path: String, name: String): Boolean = try {
        transport.execute(
            ToolExecutionRequest(
                toolId = "rm",
                arguments = listOf("-f", path),
                workingDirectory = "/",
            ),
        )
        log("Pruned non-catalog manifest for '$name' at $path")
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        log("Failed to remove pruned manifest $path: ${e.message}")
        false
    }

    override suspend fun resolvedTools(): ToolRegistryResult = ToolStatusMapper.toDomain(transport.listTools())
}
