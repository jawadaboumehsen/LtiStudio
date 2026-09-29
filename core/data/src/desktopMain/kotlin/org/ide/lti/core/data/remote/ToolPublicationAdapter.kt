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

        // Make the new manifests live before the (slower) cleanup: an interruption while pruning then
        // leaves the tools registered, not written-but-unloaded.
        val registered = verifyDynamicRegistrations(uploadedTools, failed)
        val pruned = pruneOldManifests(normalizedBin)

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

    /**
     * Removes this app's stale manifests (non-catalog tools it once published under [binDir]) in two round
     * trips: one listing that returns every manifest, one `rm` for all stale paths. Reading and removing
     * each manifest separately cost two calls per file, minutes for a directory full of old junk.
     */
    private suspend fun pruneOldManifests(binDir: String): Set<String> {
        val stale = readAllManifests().filter { (_, manifest) -> shouldPrune(manifest, binDir) }
        if (stale.isEmpty()) return emptySet()

        val failure = try {
            val exitCode = transport.execute(
                ToolExecutionRequest(
                    toolId = "rm",
                    arguments = listOf("-f") + stale.map { it.first },
                    workingDirectory = "/",
                ),
            ).exitCode
            if (exitCode == 0) null else "rm exited with $exitCode"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.message ?: "rm failed"
        }
        return if (failure == null) {
            stale.forEach { (path, manifest) -> log("Pruned non-catalog manifest for '${manifest.name}' at $path") }
            stale.map { it.second.name }.toSet()
        } else {
            log("Failed to remove ${stale.size} stale manifest(s): $failure")
            emptySet()
        }
    }

    private suspend fun readAllManifests(): List<Pair<String, ToolManifestDto>> {
        val listing = try {
            // The service runs tools without a shell, so `~` would reach a command literally; `sh -c`
            // expands $HOME and the glob and prints absolute manifest paths.
            transport.execute(
                ToolExecutionRequest(toolId = "sh", arguments = listOf("-c", MANIFEST_LISTING), workingDirectory = "/"),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (listing == null || listing.exitCode != 0) return emptyList()

        return listing.stdout.lines().mapNotNull { line ->
            val tab = line.indexOf('\t')
            if (tab <= 0) return@mapNotNull null
            val manifest = runCatching { json.decodeFromString<ToolManifestDto>(line.substring(tab + 1)) }.getOrNull()
            manifest?.let { line.substring(0, tab) to it }
        }
    }

    private fun shouldPrune(manifest: ToolManifestDto, binDir: String): Boolean {
        val isAppPublished = manifest.description?.startsWith("Published binary for") == true
        val isUnderBin = manifest.executable.startsWith("$binDir/") || manifest.executable == binDir
        val isCatalog = ToolCatalog.isCatalogTool(manifest.name)
        return isAppPublished && isUnderBin && !isCatalog
    }

    /**
     * What the service resolves right now. A refresh rescans the manifests on disk, so a publish that was
     * interrupted after writing them (before its own refresh) is still seen by the next check.
     */
    override suspend fun resolvedTools(): ToolRegistryResult = ToolStatusMapper.toDomain(transport.refreshTools())

    private companion object {
        /** Every manifest in one round trip, one `path<TAB>json` line each (manifests are one-line JSON). */
        val MANIFEST_LISTING = """for f in "${'$'}HOME"/.ltirom/tools.d/*.json; do [ -f "${'$'}f" ] || continue; """ +
            """printf '%s\t' "${'$'}f"; tr -d '\n' < "${'$'}f"; echo; done"""
    }
}
