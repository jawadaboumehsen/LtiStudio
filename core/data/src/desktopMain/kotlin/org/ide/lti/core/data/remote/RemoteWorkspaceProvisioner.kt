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
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.ports.DestinationExistsException
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceKeys
import org.ide.lti.core.model.workspace.WorkspaceManifest
import java.util.concurrent.ConcurrentHashMap

/**
 * Materializes an independent workspace in WSL conforming to research.md R8 and contracts/pipeline-stages.md.
 *
 * All operations execute through [RemoteTransportPort]. No bash -c chains or temporary .sh scripts are ever created.
 */
public class RemoteWorkspaceProvisioner(
    private val transport: RemoteTransportPort,
    private val readinessPort: EnvironmentReadinessPort,
    private val keyProvisioner: WorkspaceKeyProvisioner = WorkspaceKeyProvisioner(transport),
    private val json: Json = ConfigurationSnapshot.canonicalJson,
) : WorkspaceProvisioningPort {

    private val cancelledSpecs = ConcurrentHashMap.newKeySet<String>()

    private companion object {
        const val GLOBAL_AVB_KEY_RELATIVE_PATH = "security/avb/lti_rsa4096.pem"
        val LAYOUT_DIRS = listOf("firmware", "work", "modules", "keys", "out/images", "out/package", "runs", ".cache")
    }

    /** Raised inside a step to abort provisioning; the flow turns it into a [ProvisioningEvent.Failed]. */
    private class StepFailure(val step: String, message: String) : RuntimeException(message)

    override fun provision(spec: ProvisioningSpec): Flow<ProvisioningEvent> = flow {
        val dest = spec.destinationLinuxPath
        require(dest.startsWith("/")) { "Destination must be an absolute Linux path: $dest" }
        val parentDir = dest.substringBeforeLast('/').ifEmpty { "/" }
        val tmp = "$dest.tmp-${spec.manifest.id}"

        // Destination collision is a hard error the use case maps to DestinationExists (FR-007).
        if (run(parentDir, "test", "-e", dest).exitCode == 0) {
            val msg = "Destination already exists: $dest"
            emit(ProvisioningEvent.Failed("check_destination", msg))
            throw DestinationExistsException(msg)
        }

        try {
            checkNotCancelled(spec, dest)
            emit(ProvisioningEvent.Step("layout", "Creating workspace directory layout"))
            createLayout(parentDir, tmp)
            checkNotCancelled(spec, dest)

            emit(ProvisioningEvent.Step("config", "Writing workspace configuration"))
            upload("$tmp/workspace.json", json.encodeToString(spec.manifest), "config")
            upload("$tmp/config.json", json.encodeToString(spec.snapshot), "config")
            checkNotCancelled(spec, dest)

            emit(ProvisioningEvent.Step("key", "Provisioning workspace security keys"))
            val keys = provisionKeys(spec, tmp)
            checkNotCancelled(spec, dest)

            emit(ProvisioningEvent.Step("validate", "Validating workspace readiness"))
            validate(spec, parentDir, tmp)
            checkNotCancelled(spec, dest)

            emit(ProvisioningEvent.Step("promote", "Promoting workspace to final destination"))
            val mvRes = run(parentDir, "mv", tmp, dest)
            if (mvRes.exitCode != 0) throw StepFailure("promote", "Failed to promote workspace: ${mvRes.stderr}")

            emit(ProvisioningEvent.Completed(keys))
        } catch (failure: StepFailure) {
            run(parentDir, "rm", "-rf", tmp)
            emit(ProvisioningEvent.Failed(failure.step, failure.message.orEmpty()))
        } finally {
            cancelledSpecs.remove(spec.manifest.id)
            cancelledSpecs.remove(dest)
        }
    }

    override suspend fun cancel(id: String) {
        cancelledSpecs.add(id)
    }

    override suspend fun writeConfig(workspace: Workspace, snapshot: ConfigurationSnapshot) {
        val linuxPath = requireNotNull(workspace.linuxPath) { "Workspace has no linuxPath" }
        val configBytes = json.encodeToString(snapshot).encodeToByteArray()
        val uploaded = transport.uploadFile("$linuxPath/config.json", configBytes)
        check(uploaded) { "Failed to upload config.json to $linuxPath/config.json" }
    }

    private fun checkNotCancelled(spec: ProvisioningSpec, dest: String) {
        if (cancelledSpecs.contains(spec.manifest.id) || cancelledSpecs.contains(dest)) {
            throw StepFailure("cancelled", "Provisioning cancelled")
        }
    }

    private suspend fun createLayout(parentDir: String, tmp: String) {
        val mkdirRes = run(parentDir, "mkdir", listOf("-p") + LAYOUT_DIRS.map { "$tmp/$it" })
        if (mkdirRes.exitCode != 0) throw StepFailure("layout", "Failed to create directories: ${mkdirRes.stderr}")
    }

    private suspend fun upload(remotePath: String, content: String, step: String) {
        if (!transport.uploadFile(remotePath, content.encodeToByteArray())) {
            throw StepFailure(step, "Failed to upload ${remotePath.substringAfterLast('/')}")
        }
    }

    private suspend fun provisionKeys(spec: ProvisioningSpec, tmp: String): WorkspaceKeys = try {
        keyProvisioner.provisionKeys(
            workspaceRoot = tmp,
            keySource = spec.keySource,
            globalAvbKeyPath = "${spec.workDirLinuxPath}/$GLOBAL_AVB_KEY_RELATIVE_PATH",
        )
    } catch (e: IllegalStateException) {
        throw StepFailure("key", "Key provisioning failed: ${e.message}")
    } catch (e: IllegalArgumentException) {
        throw StepFailure("key", "Key provisioning failed: ${e.message}")
    }

    private suspend fun validate(spec: ProvisioningSpec, parentDir: String, tmp: String) {
        readBack<WorkspaceManifest>("$tmp/workspace.json")
        readBack<ConfigurationSnapshot>("$tmp/config.json")
        LAYOUT_DIRS.map { "$tmp/$it" }.forEach { dir ->
            if (run(parentDir, "test", "-d", dir).exitCode != 0) {
                throw StepFailure("validate", "Missing layout directory: $dir")
            }
        }

        val staged = Workspace(
            id = spec.manifest.id,
            name = spec.target.name,
            path = tmp,
            linuxPath = tmp,
            targetBinding = spec.manifest.targetBinding,
            layoutVersion = spec.manifest.layoutVersion,
            effectiveSnapshotId = spec.snapshot.id,
        )
        val readiness = readinessPort.forWorkspace(staged)
        if (readiness.state != EnvironmentReadinessState.READY) {
            throw StepFailure("validate", readiness.failingCheck ?: "Readiness check failed: ${readiness.state}")
        }
    }

    private suspend inline fun <reified T> readBack(remotePath: String) {
        val name = remotePath.substringAfterLast('/')
        val bytes = transport.downloadFile(remotePath)
        val decoded = bytes?.let { runCatching { json.decodeFromString<T>(it.decodeToString()) } }
        val failure = when {
            decoded == null -> "Failed to read back $name"
            decoded.isFailure -> "$name is corrupt: ${decoded.exceptionOrNull()?.message}"
            else -> null
        }
        if (failure != null) throw StepFailure("validate", failure)
    }

    private suspend fun run(workingDirectory: String, toolId: String, vararg arguments: String): ToolExecutionResponse =
        run(workingDirectory, toolId, arguments.toList())

    private suspend fun run(workingDirectory: String, toolId: String, arguments: List<String>): ToolExecutionResponse =
        transport.execute(
            ToolExecutionRequest(
                toolId = toolId,
                arguments = arguments,
                workingDirectory = workingDirectory,
            ),
        )
}
