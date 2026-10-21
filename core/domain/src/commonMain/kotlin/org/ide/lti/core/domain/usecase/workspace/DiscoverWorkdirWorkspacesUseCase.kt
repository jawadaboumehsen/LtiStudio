/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.workspace

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceManifest
import org.ide.lti.core.model.workspace.WorkspaceType

/**
 * Finds the workspaces in the service's `~/LtiRomWorkDir/workspaces/` and makes each one openable. A workspace
 * lives in its folder (`workspace.json`, `device.json`, `config.json`); the app's record is rebuilt from there
 * when the app has none (a new install, a cleared app store), so opening it shows its saved configuration.
 */
class DiscoverWorkdirWorkspacesUseCase(
    private val repository: WorkspaceRepository,
    private val readiness: EnvironmentReadinessPort? = null,
    private val folders: PipelineExecutionPort? = null,
) {
    /** Waits until the build service is ready, then returns the workspaces it found (new or already known). */
    suspend operator fun invoke(): List<Workspace> {
        val files = folders
        val ready = readiness?.takeIf { files != null }
            ?.observe()
            // READY can be reported before the service's home (and so the WorkDir) is known.
            ?.first { it.state == EnvironmentReadinessState.READY && it.workDirLinuxPath != null }
        val workDir = ready?.workDirLinuxPath?.trimEnd('/')
        if (files == null || workDir == null) return emptyList()
        val manifests = manifests(files, workDir)
        co.touchlab.kermit.Logger.i { "Workspace discovery: ${manifests.size} workspace folder(s) in $workDir" }
        return manifests.mapNotNull { manifestPath ->
            val dir = manifestPath.removeSuffix("/$MANIFEST")
            try {
                adopt(files, dir)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                co.touchlab.kermit.Logger.w(e) { "Skipping workspace folder $dir" }
                null
            }
        }
    }

    /** Paths of `workspaces/<dir>/workspace.json`; none while the workspaces folder does not exist yet. */
    private suspend fun manifests(files: PipelineExecutionPort, workDir: String): List<String> {
        val args = listOf("$workDir/workspaces", "-mindepth", "2", "-maxdepth", "2", "-name", MANIFEST)
        val root = Workspace(id = "", name = "", path = workDir, linuxPath = workDir)
        val listing = files.execute(root, StepCommand("find", args, workDir, TIMEOUT_MS))
        return if (listing.exitCode == 0) listing.stdout.lines().filter { it.isNotBlank() } else emptyList()
    }

    private suspend fun adopt(files: PipelineExecutionPort, dir: String): Workspace? {
        val folder = Workspace(id = "", name = dir.substringAfterLast('/'), path = dir, linuxPath = dir)
        val manifest = files.readFile(folder, MANIFEST)?.let { json.decodeFromString<WorkspaceManifest>(it) }
            ?: return null
        val device = files.readFile(folder, WorkspaceDeviceRepository.FILE_NAME)
        val known = repository.getWorkspace(manifest.id)
        val workspace = known ?: folder.copy(
            id = manifest.id,
            type = WorkspaceType.REMOTE_WSL,
            // workspace.json records the revision at creation; device.json has the current one.
            targetBinding = manifest.targetBinding.copy(
                profileRevision = device?.let { field(it, "revision")?.toIntOrNull() }
                    ?: manifest.targetBinding.profileRevision,
            ),
            layoutVersion = manifest.layoutVersion,
            effectiveSnapshotId = files.readFile(folder, CONFIG)?.let { configId(it) },
        ).also { repository.saveWorkspace(it) }

        val recents = repository.getRecentProjects().first()
        if (recents.none { it.workspaceId == workspace.id }) {
            val deviceName = device?.let { field(it, "name") }
            repository.addRecentProject(
                RecentProject(
                    workspaceId = workspace.id,
                    name = workspace.name,
                    path = dir,
                    lastOpened = Clock.System.now(),
                    type = WorkspaceType.REMOTE_WSL,
                    targetBinding = workspace.targetBinding,
                    targetDisplayName = deviceName,
                ),
            )
        }
        return workspace
    }

    private fun configId(text: String): String? = field(text, "id")

    private fun field(text: String, name: String): String? =
        runCatching { json.parseToJsonElement(text).jsonObject[name]?.jsonPrimitive?.content }.getOrNull()

    private companion object {
        const val MANIFEST = "workspace.json"
        const val CONFIG = "config.json"
        const val TIMEOUT_MS = 30_000L
        val json = Json { ignoreUnknownKeys = true }
    }
}
