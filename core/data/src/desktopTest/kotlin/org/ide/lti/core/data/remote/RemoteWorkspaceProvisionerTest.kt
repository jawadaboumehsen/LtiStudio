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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.ports.DestinationExistsException
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.KeySource
import org.ide.lti.core.domain.ports.ProvisioningEvent
import org.ide.lti.core.domain.ports.ProvisioningSpec
import org.ide.lti.core.model.setup.EnvironmentReadiness
import org.ide.lti.core.model.setup.EnvironmentReadinessState
import org.ide.lti.core.model.target.PackagePolicy
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.workspace.AcquisitionMode
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RemoteWorkspaceProvisionerTest {

    private val target = TargetDevice(
        id = "PQ84P01",
        name = "REDMAGIC Astra",
        codename = "PQ84P01",
        revision = 1,
        availableRegions = listOf(TargetRegion.GLOBAL),
        availableFirmwares = mapOf(
            TargetRegion.GLOBAL to listOf(
                TargetFirmware(
                    version = "10.5.15",
                    buildId = "PQ84P01:15",
                    androidVersion = "15",
                    securityPatch = "2026-02-01",
                ),
            ),
        ),
        packagePolicy = PackagePolicy(
            flashablePartitions = listOf("system", "vendor"),
            flashableBootPartitions = listOf("boot"),
        ),
        socPlatform = "Snapdragon 8 Elite",
        filesystemType = "erofs",
        superPartitionBytes = 17179869184L,
        dynamicPartitions = listOf("system", "vendor"),
        bootPartitions = listOf("boot"),
        status = TargetStatus.QUALIFIED,
        description = "Official baseline",
    )

    private val snapshot = ConfigurationSnapshot.create(
        id = "snap-123",
        workspaceId = "ws-123",
        profileRevision = 1,
        acquisition = org.ide.lti.core.model.workspace.AcquisitionSettings(
            mode = AcquisitionMode.DOWNLOAD,
            region = TargetRegion.GLOBAL,
            firmware = target.availableFirmwares[TargetRegion.GLOBAL]!!.first(),
        ),
        build = org.ide.lti.core.model.workspace.BuildSettings(
            packagePolicy = target.packagePolicy!!,
            signing = org.ide.lti.core.model.workspace.SigningPolicy(),
        ),
        assembly = org.ide.lti.core.model.workspace.AssemblySettings(
            buildType = "user",
            romVersion = "1.0.0",
        ),
        release = org.ide.lti.core.model.workspace.ReleaseSettings(
            otaBaseUrl = "https://example.com/updates",
        ),
        createdAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1700000000000L),
    )

    private val manifest = WorkspaceManifest(
        id = "ws-123",
        layoutVersion = 1,
        targetBinding = TargetBinding("PQ84P01", 1),
        createdAt = "2026-09-11T12:00:00Z",
        appVersion = "1.0.0",
    )

    private class ScriptedFakeTransport : RemoteTransportPort {
        val executedRequests = mutableListOf<ToolExecutionRequest>()
        val uploadedFiles = mutableMapOf<String, ByteArray>()
        var destinationExists = false
        var simulateMkdirFailure = false

        override suspend fun execute(request: ToolExecutionRequest): ToolExecutionResponse {
            executedRequests.add(request)
            val isExistsCheck = request.toolId == "test" && request.arguments.contains("-e")
            val mkdirFails = request.toolId == "mkdir" && simulateMkdirFailure
            val (exit, stdout, stderr) = when {
                isExistsCheck && !destinationExists -> Triple(1, "", "")
                mkdirFails -> Triple(1, "", "mkdir error")
                request.toolId == "sha1sum" -> Triple(0, "4b825dc642cb6eb9a060e54bf8d69288fbee4904  file\n", "")
                request.toolId == "test" || request.toolId == "mkdir" -> Triple(0, "", "")
                else -> Triple(0, "ok", "")
            }
            return ToolExecutionResponse(exitCode = exit, stdout = stdout, stderr = stderr, durationMs = 5)
        }

        override fun stream(request: ToolExecutionRequest) = flowOf<io.ltirom.tooling.core.remote.StreamEvent>()
        override suspend fun checkHealth() = null
        override suspend fun listTools() = io.ltirom.tooling.core.remote.ToolListResult.Tools(emptyList())
        override suspend fun refreshTools() = io.ltirom.tooling.core.remote.ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean {
            uploadedFiles[remotePath] = content
            return true
        }

        override suspend fun downloadFile(remotePath: String): ByteArray? {
            return uploadedFiles[remotePath]
        }

        override suspend fun shutdown() = true
    }

    private class FakeReadinessPort : EnvironmentReadinessPort {
        var readiness = EnvironmentReadiness(state = EnvironmentReadinessState.READY)
        override fun observe(): Flow<EnvironmentReadiness> = flowOf(readiness)
        override suspend fun refresh(): EnvironmentReadiness = readiness
        override suspend fun forWorkspace(workspace: Workspace): EnvironmentReadiness = readiness
    }

    @Test
    fun exactHappyPathSequenceCreatesWorkspaceAndPromotes() = runTest {
        val transport = ScriptedFakeTransport()
        val readiness = FakeReadinessPort()
        val provisioner = RemoteWorkspaceProvisioner(transport, readiness)

        val destPath = "/home/lti/LtiRomWorkDir/workspaces/astra-workspace"
        val spec = ProvisioningSpec(
            destinationLinuxPath = destPath,
            target = target,
            snapshot = snapshot,
            manifest = manifest,
            keySource = KeySource.GENERATE,
        )

        val events = provisioner.provision(spec).toList()
        val completed = events.filterIsInstance<ProvisioningEvent.Completed>().firstOrNull()
        assertNotNull(completed)
        assertEquals("4b825dc642cb6eb9a060e54bf8d69288fbee4904", completed.keys.avbPublicKeySha1)

        val tmp = "$destPath.tmp-${manifest.id}"

        // 1. First command was `test -e <dest>`
        assertEquals("test", transport.executedRequests[0].toolId)
        assertEquals(listOf("-e", destPath), transport.executedRequests[0].arguments)

        // 2. Second command was `mkdir -p <tmp>/...`
        assertEquals("mkdir", transport.executedRequests[1].toolId)
        assertTrue(transport.executedRequests[1].arguments.contains("$tmp/firmware"))
        assertTrue(transport.executedRequests[1].arguments.contains("$tmp/work"))
        assertTrue(transport.executedRequests[1].arguments.contains("$tmp/out/images"))

        // 3. Uploaded workspace.json and config.json
        assertTrue(transport.uploadedFiles.containsKey("$tmp/workspace.json"))
        assertTrue(transport.uploadedFiles.containsKey("$tmp/config.json"))

        // 4. Key generation executed
        assertTrue(transport.executedRequests.any { it.toolId == "openssl" && it.arguments.contains("genpkey") })
        assertTrue(
            transport.executedRequests.any { it.toolId == "avbtool" && it.arguments.contains("extract_public_key") },
        )
        assertTrue(transport.executedRequests.any { it.toolId == "openssl" && it.arguments.contains("genrsa") })

        // 5. Layout directories validated
        assertTrue(transport.executedRequests.any { it.toolId == "test" && it.arguments.contains("-d") })

        // 6. Final promotion mv <tmp> <dest>
        val lastCommand = transport.executedRequests.last()
        assertEquals("mv", lastCommand.toolId)
        assertEquals(listOf(tmp, destPath), lastCommand.arguments)

        // 7. Every command has absolute workingDirectory
        assertTrue(transport.executedRequests.all { it.workingDirectory?.startsWith("/") == true })

        // 8. No .sh files were written or executed
        assertTrue(transport.uploadedFiles.keys.none { it.endsWith(".sh") })
        assertTrue(transport.executedRequests.none { req -> req.arguments.any { it.endsWith(".sh") } })
    }

    @Test
    fun destinationExistsRefusesCreationWithoutModifications() = runTest {
        val transport = ScriptedFakeTransport().apply { destinationExists = true }
        val readiness = FakeReadinessPort()
        val provisioner = RemoteWorkspaceProvisioner(transport, readiness)

        val destPath = "/home/lti/LtiRomWorkDir/workspaces/existing-workspace"
        val spec = ProvisioningSpec(
            destinationLinuxPath = destPath,
            target = target,
            snapshot = snapshot,
            manifest = manifest,
        )

        assertFailsWith<DestinationExistsException> {
            provisioner.provision(spec).toList()
        }

        // Only test -e was executed
        assertEquals(1, transport.executedRequests.size)
        assertEquals("test", transport.executedRequests[0].toolId)
        assertEquals(0, transport.uploadedFiles.size)
    }

    @Test
    fun failureCleansUpTmpDirectory() = runTest {
        val transport = ScriptedFakeTransport().apply { simulateMkdirFailure = true }
        val readiness = FakeReadinessPort()
        val provisioner = RemoteWorkspaceProvisioner(transport, readiness)

        val destPath = "/home/lti/LtiRomWorkDir/workspaces/fail-workspace"
        val tmp = "$destPath.tmp-${manifest.id}"
        val spec = ProvisioningSpec(
            destinationLinuxPath = destPath,
            target = target,
            snapshot = snapshot,
            manifest = manifest,
        )

        val events = provisioner.provision(spec).toList()
        val failed = events.filterIsInstance<ProvisioningEvent.Failed>().firstOrNull()
        assertNotNull(failed)

        // Verify rm -rf <tmp> was executed
        assertTrue(transport.executedRequests.any { it.toolId == "rm" && it.arguments == listOf("-rf", tmp) })
        // No mv command executed
        assertTrue(transport.executedRequests.none { it.toolId == "mv" })
    }

    @Test
    fun writeConfigUploadsConfigurationJson() = runTest {
        val transport = ScriptedFakeTransport()
        val readiness = FakeReadinessPort()
        val provisioner = RemoteWorkspaceProvisioner(transport, readiness)

        val ws = Workspace(
            id = "ws-123",
            name = "Astra",
            path = "/home/lti/LtiRomWorkDir/workspaces/astra",
            linuxPath = "/home/lti/LtiRomWorkDir/workspaces/astra",
        )

        provisioner.writeConfig(ws, snapshot)

        assertTrue(transport.uploadedFiles.containsKey("/home/lti/LtiRomWorkDir/workspaces/astra/config.json"))
    }
}
