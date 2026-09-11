/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.workspace

import kotlinx.datetime.Clock
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import kotlinx.serialization.json.Json
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.WorkspaceManifest
import org.ide.lti.core.model.workspace.WorkspaceType

/**
 * Service that detects physical workspaces on disk inside LtiRomWorkDir.
 *
 * Resolves both native host filesystem paths and WSL hypervisor UNC paths:
 * - Windows WSL: \\wsl.localhost\\Ubuntu\\home\\lti\\LtiRomWorkDir
 * - Linux Host: /home/lti/LtiRomWorkDir or ~/LtiRomWorkDir
 * - Local fallback: ${user.home}/LtiRomWorkDir
 */
public class WorkDirDiscoveryService(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    public fun discoverWorkspaces(basePathOverride: String? = null): List<RecentProject> {
        val physicalDir = resolvePhysicalWorkDir(basePathOverride)
        if (physicalDir == null || !safeExists(physicalDir)) {
            return emptyList()
        }

        val discovered = mutableListOf<RecentProject>()
        val now = Clock.System.now()

        // Scan workspaces/* subdirectories with workspace.json
        val workspacesDir = physicalDir / "workspaces"
        if (safeExists(workspacesDir)) {
            safeList(workspacesDir).forEach { wsSubDir ->
                if (safeIsDirectory(wsSubDir)) {
                    val manifestFile = wsSubDir / "workspace.json"
                    if (safeExists(manifestFile)) {
                        try {
                            val content = fileSystem.read(manifestFile) { readUtf8() }
                            val manifest = json.decodeFromString<WorkspaceManifest>(content)
                            val dirName = wsSubDir.name
                            discovered.add(
                                RecentProject(
                                    workspaceId = manifest.id,
                                    name = dirName,
                                    path = "~/LtiRomWorkDir/workspaces/$dirName",
                                    lastOpened = now,
                                    type = WorkspaceType.REMOTE_WSL,
                                    targetBinding = manifest.targetBinding,
                                    targetDisplayName = manifest.targetBinding.profileId,
                                )
                            )
                        } catch (_: Exception) {
                            // Skip invalid/unparseable workspace
                        }
                    }
                }
            }
        }

        return discovered
    }

    private fun resolvePhysicalWorkDir(basePathOverride: String?): Path? {
        if (!basePathOverride.isNullOrBlank()) {
            try {
                val candidate = basePathOverride.toPath()
                if (safeExists(candidate)) return candidate
            } catch (_: Exception) {}
        }

        val candidates = listOf(
            "\\\\wsl.localhost\\Ubuntu\\home\\lti\\LtiRomWorkDir",
            "\\\\wsl$\\Ubuntu\\home\\lti\\LtiRomWorkDir",
            "/home/lti/LtiRomWorkDir",
            "${System.getProperty("user.home")}/LtiRomWorkDir",
        )

        for (candidateStr in candidates) {
            try {
                val candidate = candidateStr.toPath()
                if (safeExists(candidate)) {
                    return candidate
                }
            } catch (_: Exception) {
                // Check next candidate
            }
        }

        return null
    }

    private fun safeExists(path: Path): Boolean {
        return try {
            fileSystem.exists(path)
        } catch (_: Exception) {
            false
        }
    }

    private fun safeIsDirectory(path: Path): Boolean {
        return try {
            fileSystem.metadataOrNull(path)?.isDirectory == true
        } catch (_: Exception) {
            false
        }
    }

    private fun safeList(path: Path): List<Path> {
        return try {
            fileSystem.list(path)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
