package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ReleaseVersion
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.ChoiceUpdate
import org.ide.lti.core.domain.tools.InstallEvent
import org.ide.lti.core.domain.tools.InstallRequest
import org.ide.lti.core.domain.tools.OperationResult
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.ToolError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host integration test verifying downloaded tools with Release recipes (T091 / US7):
 * - Download runs `curl -fL --retry 3` as a NETWORK stream.
 * - Tampered archive -> ChecksumMismatch, and nothing is published.
 * - Member extracted from tar and zip archives to output file with mode 755.
 * - Folder id is the release version.
 */
class ReleaseRecipeRealTest {
    private fun hostPath(host: RealToolHost, sub: String = ""): String = host.hostPath(sub)

    @Test
    fun tarGzReleaseExtractsMemberWithMode755AndFolderIdAsVersion() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        FixtureHttpServer().use { server ->
            RealToolHost.create().use { host ->
                val baseUrl = server.resolveBaseUrl(host.transport)
                val group =
                    ToolGroup(
                        id = ToolGroupId("release-tar-tool"),
                        source = ToolSource.Release,
                        recipe =
                        RecipeConfig.Release(
                            versions =
                            listOf(
                                ReleaseVersion(
                                    version = "1.0.0",
                                    url = "$baseUrl/releases/sample.tar.gz",
                                    archiveMember = "sample-tool",
                                    sha256 = server.tarGzSha256,
                                ),
                            ),
                            revision = 1,
                        ),
                        layout = emptyList(),
                        submodulePaths = emptyList(),
                        outputs =
                        listOf(
                            ToolOutput(
                                toolId = "sample-tool",
                                file = "sample-tool",
                                kind = ToolOutputKind.NATIVE,
                                probe = ToolProbe(emptyList()),
                            ),
                        ),
                        buildPackages = listOf("tar"),
                    )

                ToolGroupCatalog.withIsolatedTestGroup(group) {
                    val req =
                        InstallRequest(
                            opId = "op-release-tar-1",
                            group = group.id,
                            label = "1.0.0",
                            source =
                            ResolvedSource.Release(
                                url = "$baseUrl/releases/sample.tar.gz",
                                version = "1.0.0",
                                sha256 = server.tarGzSha256,
                                member = "sample-tool",
                            ),
                            recipe = group.recipe,
                            outputs = group.outputs,
                            select = true,
                            choice = ChoiceUpdate.Record,
                        )

                    val logLines = mutableListOf<String>()
                    val events = host.store.install(req).toList()
                    events.filterIsInstance<InstallEvent.Output>().forEach { logLines.add(it.text) }
                    val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()

                    assertNotNull(finished, "Install must emit Finished event")
                    val result = finished.result
                    assertIs<OperationResult.Installed>(result)
                    assertEquals("1.0.0", result.versionId, "Folder ID must be release version")

                    // Verify curl was logged
                    assertTrue(
                        logLines.any { it.contains("curl") || it.contains("Downloading release archive") },
                        "Download must be executed and logged",
                    )

                    // Verify folder ID is the release version
                    val verDir = "${hostPath(host)}/LtiRomTools/bin/release-tar-tool/1.0.0"
                    val dirCheck =
                        host.transport.execute(
                            ToolExecutionRequest("test", listOf("-d", verDir), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, dirCheck.exitCode, "Folder named after release version must exist: $verDir")

                    // Verify member is extracted to file with mode 755
                    val binPath = "$verDir/sample-tool"
                    val execCheck =
                        host.transport.execute(
                            ToolExecutionRequest("test", listOf("-x", binPath), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, execCheck.exitCode, "$binPath must exist and have mode 755 (executable)")

                    // Verify execution output
                    val runRes =
                        host.transport.execute(
                            ToolExecutionRequest("sh", listOf("-c", binPath), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, runRes.exitCode)
                    assertTrue(runRes.stdout.contains("sample-tar 1.0"), "Tool output must match archive content")

                    // Verify symlink moves to version
                    val currentTarget =
                        host.transport.execute(
                            ToolExecutionRequest(
                                "readlink",
                                listOf("${hostPath(host)}/LtiRomTools/bin/release-tar-tool/current"),
                                workingDirectory = "/tmp",
                            ),
                        )
                    assertEquals("1.0.0", currentTarget.stdout.trim())
                }
            }
        }
    }

    @Test
    fun zipReleaseExtractsMemberWithMode755AndFolderIdAsVersion() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        FixtureHttpServer().use { server ->
            RealToolHost.create().use { host ->
                val baseUrl = server.resolveBaseUrl(host.transport)
                val group =
                    ToolGroup(
                        id = ToolGroupId("release-zip-tool"),
                        source = ToolSource.Release,
                        recipe =
                        RecipeConfig.Release(
                            versions =
                            listOf(
                                ReleaseVersion(
                                    version = "2.0.0",
                                    url = "$baseUrl/releases/sample.zip",
                                    archiveMember = "sample-tool",
                                    sha256 = server.zipSha256,
                                ),
                            ),
                            revision = 1,
                        ),
                        layout = emptyList(),
                        submodulePaths = emptyList(),
                        outputs =
                        listOf(
                            ToolOutput(
                                toolId = "sample-tool",
                                file = "sample-tool",
                                kind = ToolOutputKind.NATIVE,
                                probe = ToolProbe(emptyList()),
                            ),
                        ),
                        buildPackages = listOf("unzip"),
                    )

                ToolGroupCatalog.withIsolatedTestGroup(group) {
                    val req =
                        InstallRequest(
                            opId = "op-release-zip-1",
                            group = group.id,
                            label = "2.0.0",
                            source =
                            ResolvedSource.Release(
                                url = "$baseUrl/releases/sample.zip",
                                version = "2.0.0",
                                sha256 = server.zipSha256,
                                member = "sample-tool",
                            ),
                            recipe = group.recipe,
                            outputs = group.outputs,
                            select = true,
                            choice = ChoiceUpdate.Record,
                        )

                    val events = host.store.install(req).toList()
                    val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()

                    assertNotNull(finished, "Install must emit Finished event")
                    val result = finished.result
                    assertIs<OperationResult.Installed>(result)
                    assertEquals("2.0.0", result.versionId, "Folder ID must be release version")

                    val verDir = "${hostPath(host)}/LtiRomTools/bin/release-zip-tool/2.0.0"
                    val dirCheck =
                        host.transport.execute(
                            ToolExecutionRequest("test", listOf("-d", verDir), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, dirCheck.exitCode, "Folder named after release version must exist: $verDir")

                    val binPath = "$verDir/sample-tool"
                    val execCheck =
                        host.transport.execute(
                            ToolExecutionRequest("test", listOf("-x", binPath), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, execCheck.exitCode, "$binPath must exist and have mode 755 (executable)")

                    val runRes =
                        host.transport.execute(
                            ToolExecutionRequest("sh", listOf("-c", binPath), workingDirectory = "/tmp"),
                        )
                    assertEquals(0, runRes.exitCode)
                    assertTrue(
                        runRes.stdout.contains("sample-tool 1.0"),
                        "Tool output must match zip archive content",
                    )
                }
            }
        }
    }

    @Test
    fun tamperedArchiveFailsWithChecksumMismatchAndPublishesNothing() = runBlocking {
        if (!RealToolHost.isAvailable()) return@runBlocking

        FixtureHttpServer().use { server ->
            RealToolHost.create().use { host ->
                val baseUrl = server.resolveBaseUrl(host.transport)
                val group =
                    ToolGroup(
                        id = ToolGroupId("release-tampered-tool"),
                        source = ToolSource.Release,
                        recipe =
                        RecipeConfig.Release(
                            versions =
                            listOf(
                                ReleaseVersion(
                                    version = "1.0.0",
                                    url = "$baseUrl/releases/tampered.tar.gz",
                                    archiveMember = "sample-tool",
                                    sha256 = server.tarGzSha256,
                                ),
                            ),
                            revision = 1,
                        ),
                        layout = emptyList(),
                        submodulePaths = emptyList(),
                        outputs =
                        listOf(
                            ToolOutput(
                                toolId = "sample-tool",
                                file = "sample-tool",
                                kind = ToolOutputKind.NATIVE,
                                probe = ToolProbe(emptyList()),
                            ),
                        ),
                        buildPackages = listOf("tar"),
                    )

                ToolGroupCatalog.withIsolatedTestGroup(group) {
                    val req =
                        InstallRequest(
                            opId = "op-release-tampered-1",
                            group = group.id,
                            label = "1.0.0",
                            source =
                            ResolvedSource.Release(
                                url = "$baseUrl/releases/tampered.tar.gz",
                                version = "1.0.0",
                                sha256 = server.tarGzSha256,
                                member = "sample-tool",
                            ),
                            recipe = group.recipe,
                            outputs = group.outputs,
                            select = true,
                            choice = ChoiceUpdate.Record,
                        )

                    val events = host.store.install(req).toList()
                    val finished = events.filterIsInstance<InstallEvent.Finished>().firstOrNull()

                    assertNotNull(finished, "Install must emit Finished event")
                    val result = finished.result
                    assertIs<OperationResult.Failed>(result, "Result must be Failed")
                    assertIs<ToolError.ChecksumMismatch>(result.error, "Error must be ChecksumMismatch")

                    // Verify NOTHING is published
                    val verDir = "${hostPath(host)}/LtiRomTools/bin/release-tampered-tool/1.0.0"
                    val dirCheck =
                        host.transport.execute(
                            ToolExecutionRequest("test", listOf("-e", verDir), workingDirectory = "/tmp"),
                        )
                    assertTrue(dirCheck.exitCode != 0, "Version directory must NOT exist after checksum failure")

                    val currentCheck =
                        host.transport.execute(
                            ToolExecutionRequest(
                                "test",
                                listOf("-e", "${hostPath(host)}/LtiRomTools/bin/release-tampered-tool/current"),
                                workingDirectory = "/tmp",
                            ),
                        )
                    assertTrue(currentCheck.exitCode != 0, "current symlink must NOT exist after checksum failure")
                }
            }
        }
    }
}
