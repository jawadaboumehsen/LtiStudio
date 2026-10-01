/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.ActivateToolchainRequest
import io.ltirom.tooling.core.remote.ActivateToolchainResponse
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ports.ActivationOutcome
import org.ide.lti.core.domain.setup.ports.ActivationRequest
import org.ide.lti.core.domain.setup.ports.ArtifactId
import org.ide.lti.core.domain.setup.ports.CleanupReport
import org.ide.lti.core.domain.setup.ports.InstallId
import org.ide.lti.core.domain.setup.ports.InstalledGroupArtifact
import org.ide.lti.core.domain.setup.ports.InstalledToolchain
import org.ide.lti.core.domain.setup.ports.ToolchainInstallationPort

public fun detectHostArch(): String = when (System.getProperty("os.arch")?.lowercase()) {
    "aarch64", "arm64" -> "aarch64"
    else -> "x86_64"
}

/**
 * WSL adapter implementing [ToolchainInstallationPort] over [RemoteTransportPort]
 * and local [InstallationManager] storage.
 */
public class WslToolchainInstallationAdapter(
    public val installationManager: InstallationManager,
    private val transport: RemoteTransportPort,
    private val distro: String = "Ubuntu",
    private val arch: String = detectHostArch(),
    private val catalogOutputs: List<InstallOutputDescriptor> = ToolGroupCatalog.allOutputs.map { out ->
        InstallOutputDescriptor(
            toolId = out.toolId,
            file = out.file,
            kind = out.kind.name,
            requiredForProduct = out.requiredForProduct,
        )
    },
    private val cli: WslCliExecutor = WslCliExecutor(),
) : ToolchainInstallationPort {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun state(): InstalledToolchain {
        val remoteState = transport.getToolchainState()
        val activeId = remoteState?.activeInstallId
        val active = activeId?.let { InstallId(it) }
        val prev = remoteState?.previousInstallId?.let { InstallId(it) }

        val activeGroups = mutableMapOf<String, InstalledGroupArtifact>()
        if (activeId != null) {
            val manifest = installationManager.getManifest(activeId)
            val store = ArtifactStore(cli, distro)
            if (manifest != null) {
                val home = resolveWslHome()
                val binDir = if (home != null) "$home/LtiRomTools/installs/$activeId/bin" else null
                for ((group, artId) in manifest.groups) {
                    val artManifest = store.getManifest(group, artId)
                    val ver = artManifest?.source?.version ?: artManifest?.source?.commit?.take(7)
                    val catGroup = ToolGroupCatalog.group(ToolGroupId(group))
                    val healthy = if (binDir != null && catGroup != null) {
                        catGroup.outputs.all { out ->
                            cli.execute(distro, listOf("test", "-x", "$binDir/${out.file}")).exitCode == 0
                        }
                    } else {
                        true
                    }
                    activeGroups[group] = InstalledGroupArtifact(
                        artifactId = artId,
                        version = ver,
                        commit = artManifest?.source?.commit,
                        repoUrl = artManifest?.source?.repoUrl,
                        isHealthy = healthy,
                    )
                }
            }
        }

        return InstalledToolchain(
            activeInstallId = active,
            previousInstallId = prev,
            activeGroups = activeGroups,
        )
    }

    override suspend fun assembleCandidate(artifacts: Map<ToolGroupId, ArtifactId>): InstallId {
        val groups = artifacts.mapKeys { it.key.value }.mapValues { it.value.value }
        val candidate = installationManager.assembleCandidate(
            distro = distro,
            arch = arch,
            groups = groups,
            outputs = catalogOutputs,
        )
        assembleCandidateInWsl(candidate.installId, groups)
        return InstallId(candidate.installId)
    }

    private fun assembleCandidateInWsl(installId: String, groups: Map<String, String>) {
        val home = resolveWslHome() ?: return
        val toolsRoot = "$home/LtiRomTools"
        val installDir = "$toolsRoot/installs/$installId"
        val binDir = "$installDir/bin"

        val mkRes = cli.execute(distro, listOf("mkdir", "-p", binDir))
        require(mkRes.exitCode == 0) { "Failed to create candidate bin dir $binDir: ${mkRes.error}" }

        for ((group, artifactId) in groups) {
            val artBin = "$toolsRoot/artifacts/$group/$artifactId/bin"
            if (cli.execute(distro, listOf("test", "-d", artBin)).exitCode == 0) {
                val lsRes = cli.execute(distro, listOf("ls", "-1", artBin))
                if (lsRes.exitCode == 0) {
                    val files = lsRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }
                    for (file in files) {
                        cli.execute(
                            distro,
                            listOf("ln", "-sfn", "../../../artifacts/$group/$artifactId/bin/$file", "$binDir/$file"),
                        )
                    }
                }
            }
        }

        val manifest = InstallManifest(
            schema = 1,
            installId = installId,
            distro = distro,
            arch = arch,
            groups = groups,
            outputs = catalogOutputs,
            catalogRevision = 1,
            createdAt = System.currentTimeMillis(),
        )
        val manifestJson = json.encodeToString(manifest)
        val teeRes = cli.execute(distro, listOf("tee", "$installDir/install.json"), stdin = manifestJson)
        require(teeRes.exitCode == 0) { "Failed to write install.json: ${teeRes.error}" }
        cli.execute(distro, listOf("chmod", "-R", "+x", binDir))
    }

    private fun resolveWslHome(): String? {
        val res = cli.execute(distro, listOf("printenv", "HOME"))
        val out = res.output.trim()
        return if (res.exitCode == 0 && out.startsWith("/")) out else null
    }

    override suspend fun activate(request: ActivationRequest): ActivationOutcome {
        val remoteReq = ActivateToolchainRequest(
            activationRequestId = request.requestId,
            expectedActiveInstallId = request.expectedActiveInstallId?.value,
            targetInstallId = request.targetInstallId.value,
        )

        val resp = try {
            transport.activateToolchain(remoteReq)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return ActivationOutcome.Unknown
        }

        return mapResponseToOutcome(resp, request.targetInstallId.value)
    }

    override suspend fun activationOutcome(requestId: String): ActivationOutcome? {
        val recordJson = transport.getActivation(requestId) ?: return null
        return parseRecordJson(recordJson)
    }

    override suspend fun cleanup(references: Set<InstallId>): CleanupReport {
        val currentState = state()
        val protectedIds = references.map { it.value }.toSet()
        val res = installationManager.cleanup(
            activeInstallId = currentState.activeInstallId?.value,
            previousInstallId = currentState.previousInstallId?.value,
            protectedInstallIds = protectedIds,
        )

        return CleanupReport(
            deletedInstallCount = res.deletedInstalls.size,
            deletedArtifactCount = res.deletedArtifacts.size,
            reclaimedBytes = 0L,
        )
    }

    private fun mapResponseToOutcome(resp: ActivateToolchainResponse, fallbackTarget: String): ActivationOutcome {
        val stateOutcome = mapStateToOutcome(resp.state, resp.activeInstallId ?: fallbackTarget)
        return stateOutcome ?: mapCodeToOutcome(resp)
    }

    private fun mapStateToOutcome(state: String?, activeInstallId: String): ActivationOutcome? = when (state) {
        "COMMITTED" -> ActivationOutcome.Committed(InstallId(activeInstallId))
        "IN_PROGRESS" -> ActivationOutcome.InProgress
        else -> null
    }

    private fun mapCodeToOutcome(resp: ActivateToolchainResponse): ActivationOutcome = when (resp.code) {
        "BLOCKED" -> ActivationOutcome.Blocked(resp.activeWork ?: 1)
        "CONFLICT" -> ActivationOutcome.Conflict(resp.actual?.let { InstallId(it) })
        "REQUEST_MISMATCH" -> ActivationOutcome.RequestMismatch
        "INVALID_TARGET" -> ActivationOutcome.InvalidTarget(resp.reason ?: "Invalid target install")
        "VERIFY_FAILED" -> ActivationOutcome.VerifyFailedRestored(
            reason = resp.reason ?: "Verification failed",
            activeInstallId = resp.activeInstallId?.let { InstallId(it) },
        )
        "MAINTENANCE_FAILED" -> ActivationOutcome.MaintenanceFailed
        else -> ActivationOutcome.Unknown
    }

    private fun parseRecordJson(recordJson: String): ActivationOutcome {
        val root = runCatching { json.parseToJsonElement(recordJson).jsonObject }.getOrNull()
            ?: return ActivationOutcome.Unknown
        return mapRecordFieldsToOutcome(root)
    }

    private fun mapRecordFieldsToOutcome(root: kotlinx.serialization.json.JsonObject): ActivationOutcome {
        val state = root["state"]?.jsonPrimitive?.contentOrNull
        val target = root["target"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val activeInstallId = root["activeInstallId"]?.jsonPrimitive?.contentOrNull
        val reason = root["reason"]?.jsonPrimitive?.contentOrNull

        return when (state) {
            "COMMITTED" -> ActivationOutcome.Committed(InstallId(activeInstallId ?: target))
            "SWITCHING", "IN_PROGRESS" -> ActivationOutcome.InProgress
            "RESTORED", "VERIFY_FAILED" -> ActivationOutcome.VerifyFailedRestored(
                reason = reason ?: "Verification failed",
                activeInstallId = activeInstallId?.let { InstallId(it) },
            )
            "MAINTENANCE_FAILED" -> ActivationOutcome.MaintenanceFailed
            else -> mapRecordRejectionToOutcome(root, reason)
        }
    }

    private fun mapRecordRejectionToOutcome(
        root: kotlinx.serialization.json.JsonObject,
        reason: String?,
    ): ActivationOutcome {
        val actualActive = root["actualActiveInstallId"]?.jsonPrimitive?.contentOrNull
        val activeWorkCount = root["activeWorkCount"]?.jsonPrimitive?.intOrNull ?: 1
        return when (root["state"]?.jsonPrimitive?.contentOrNull) {
            "BLOCKED" -> ActivationOutcome.Blocked(activeWorkCount)
            "CONFLICT" -> ActivationOutcome.Conflict(actualActive?.let { InstallId(it) })
            "REQUEST_MISMATCH" -> ActivationOutcome.RequestMismatch
            "INVALID_TARGET" -> ActivationOutcome.InvalidTarget(reason ?: "Invalid target")
            else -> ActivationOutcome.Unknown
        }
    }
}
