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
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import org.ide.lti.core.model.workspace.EnvironmentId
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.WorkspaceManifest
import org.ide.lti.core.model.workspace.WorkspaceType

/**
 * Service that detects physical workspaces on disk inside LtiRomWorkDir.
 *
 * Dynamically resolves execution environment paths based on [EnvironmentId] or configuration:
 * - WSL hypervisor UNC paths: \\wsl.localhost\<distro>\<home>\LtiRomWorkDir and \\wsl$\<distro>\<home>\LtiRomWorkDir
 * - Direct Linux host paths: /<home>/LtiRomWorkDir
 * - Local filesystem fallback: ${user.home}/LtiRomWorkDir
 *
 * Does not contain hard-coded Ubuntu or lti distribution paths.
 */
public class WorkDirDiscoveryService(
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
    private val json: Json = Json { ignoreUnknownKeys = true },
    private val environmentId: EnvironmentId? = null,
    private val defaultDistro: String? = null,
    private val defaultUser: String? = null,
    private val workDirLinuxPath: String? = null,
) {
    public fun discoverWorkspaces(
        basePathOverride: String? = null,
        environmentIdOverride: EnvironmentId? = null,
    ): List<RecentProject> {
        val physicalDir = resolvePhysicalWorkDir(basePathOverride, environmentIdOverride)
        if (physicalDir == null || !safeExists(physicalDir)) {
            return emptyList()
        }

        val discovered = mutableListOf<RecentProject>()
        val now = Clock.System.now()

        // Scan exclusively workspaces/* subdirectories containing a valid workspace.json
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
                                ),
                            )
                        } catch (_: Exception) {
                            // Skip invalid or unparseable workspace
                        }
                    }
                }
            }
        }

        return discovered
    }

    private fun resolvePhysicalWorkDir(
        basePathOverride: String?,
        environmentIdOverride: EnvironmentId? = null,
    ): Path? {
        if (!basePathOverride.isNullOrBlank()) {
            try {
                val candidate = basePathOverride.toPath()
                if (safeExists(candidate)) return candidate
            } catch (_: Exception) {}
        }

        val env = environmentIdOverride ?: environmentId
        val distro = env?.distro?.ifBlank { null } ?: defaultDistro?.ifBlank { null }
        val user = env?.user?.ifBlank { null } ?: defaultUser?.ifBlank { null }

        val candidates = mutableListOf<String>()

        if (distro != null && user != null) {
            val linuxHome = if (user == "root") "root" else "home/$user"
            val effectiveWorkDir = workDirLinuxPath?.trimStart('/') ?: "$linuxHome/LtiRomWorkDir"
            val uncPart = effectiveWorkDir.replace('/', '\\')

            candidates.add("\\\\wsl.localhost\\$distro\\$uncPart")
            candidates.add("\\\\wsl$\\$distro\\$uncPart")
            candidates.add("/$effectiveWorkDir")
        } else if (distro != null) {
            if (!workDirLinuxPath.isNullOrBlank()) {
                val uncPart = workDirLinuxPath.trimStart('/').replace('/', '\\')
                candidates.add("\\\\wsl.localhost\\$distro\\$uncPart")
                candidates.add("\\\\wsl$\\$distro\\$uncPart")
                candidates.add(workDirLinuxPath)
            }
        } else if (!workDirLinuxPath.isNullOrBlank()) {
            candidates.add(workDirLinuxPath)
        }

        val userHome = try {
            System.getProperty("user.home")
        } catch (_: Throwable) {
            null
        }
        if (!userHome.isNullOrBlank()) {
            candidates.add("$userHome/LtiRomWorkDir")
        }

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
