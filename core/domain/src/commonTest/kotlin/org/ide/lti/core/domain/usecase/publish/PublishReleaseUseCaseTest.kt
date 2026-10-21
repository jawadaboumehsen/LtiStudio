/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.publish

import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PublishProvider
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PublishReleaseUseCaseTest {
    /** WSL files by absolute path, changed only through the commands the service runs. */
    private class Folder : PipelineExecutionPort {
        val files = mutableMapOf<String, String>()
        var corruptCopies = false

        /** When set, an upload reaches GitHub and then waits here (the app is still waiting for `gh`). */
        var uploadGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        /** Progress readings fail as the service client's do when it cannot confirm a cancelled query. */
        var failingSamples = false

        /** A fake GitHub: release tag -> asset name -> content; the manifest repository's files. */
        val releases = mutableMapOf<String, MutableMap<String, String>>()
        val repoFiles = mutableMapOf<String, String>()

        @Suppress("CyclomaticComplexMethod")
        override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
            val a = command.args
            return when (command.toolId) {
                "mkdir" -> ToolResult(0, "")
                "stat" -> if (a.last() in files) ToolResult(0, "1") else ToolResult(1, "", "No such file")
                "cp" -> {
                    files[a[2]] = files.getValue(a[1]) + if (corruptCopies) "!" else ""
                    ToolResult(0, "")
                }
                "mv" -> ToolResult(0, "").also { files[a[3]] = files.remove(a[2])!! }
                "sha256sum" -> files[a.last()]?.let { ToolResult(0, "${sha(it)}  ${a.last()}") }
                    ?: ToolResult(1, "", "No such file")
                "gh" -> gh(a).also { if (a.take(2) == listOf("release", "upload")) uploadGate?.await() }
                // The running upload: gh's pid, and the bytes it has read.
                "pgrep" -> if (failingSamples) error("Remote termination was not confirmed") else ToolResult(0, "7")
                "cat" -> ToolResult(0, "rchar: 1")
                else -> error("unexpected ${command.toolId}")
            }
        }

        @Suppress("CyclomaticComplexMethod")
        private fun gh(a: List<String>): ToolResult = when {
            a[0] == "auth" -> ToolResult(0, "")
            a[0] == "release" && a[1] == "view" && "--json" in a -> releases[a[2]]?.let { assets ->
                ToolResult(
                    0,
                    assets.entries.joinToString(",", """{"assets":[""", "]}") { (name, body) ->
                        """{"name":"$name","digest":"sha256:${sha(body)}"}"""
                    },
                )
            } ?: ToolResult(1, "", "release not found")
            a[0] == "release" && a[1] == "view" -> if (a[2] in releases) ToolResult(0, "") else ToolResult(1, "")
            a[0] == "release" && a[1] == "create" -> ToolResult(0, "").also { releases[a[2]] = mutableMapOf() }
            a[0] == "release" && a[1] == "upload" -> ToolResult(0, "").also {
                releases.getValue(a[2])[a[3].substringAfterLast('/')] = files.getValue(a[3])
            }
            a[0] == "api" && "PUT" in a -> ToolResult(0, "{}").also {
                val content = a.first { it.startsWith("content=") }.removePrefix("content=")
                repoFiles[a[3]] = String(java.util.Base64.getDecoder().decode(content))
            }
            a[0] == "api" -> ToolResult(1, "", "HTTP 404")
            else -> error("unexpected gh $a")
        }

        override suspend fun readFile(ws: Workspace, relPath: String): String? = files["${ws.linuxPath}/$relPath"]
        override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
            files["${ws.linuxPath}/$relPath"] = bytes.decodeToString()
            return true
        }
        override suspend fun stat(ws: Workspace, relPath: String): FileStat =
            FileStat(relPath, exists = "${ws.linuxPath}/$relPath" in files)
        private val results = mutableMapOf<String, ToolResult>()

        /** Publishing runs its commands as service steps: run the command and stream its result. */
        override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle {
            results[idempotencyKey] = execute(ws, step)
            return RunHandle(idempotencyKey, "RUNNING", 0L)
        }
        override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> {
            val r = results.getValue(serverRunId)
            val lines = r.stdout.lines().filter { it.isNotEmpty() }.mapIndexed { i, l -> StepEvent.Output(i + 1L, l) } +
                r.stderr.lines().filter { it.isNotEmpty() }.map { StepEvent.Output(0L, it, isError = true) }
            return flowOf(*(lines + StepEvent.Finished(r.exitCode)).toTypedArray())
        }
        override suspend fun status(serverRunId: String): StepStatus? = null
        override suspend fun cancel(serverRunId: String): Boolean = false
        override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
    }

    private class Workspaces(private val ws: Workspace) : WorkspaceRepository {
        override fun getWorkspaces(): Flow<List<Workspace>> = flowOf(listOf(ws))
        override suspend fun getWorkspace(id: String): Workspace? = ws.takeIf { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = flowOf(emptyList())
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
    }

    private class Settings(var publish: PublishSettings) : WorkspaceSettingsStore {
        override suspend fun current(workspaceId: String): ConfigurationSnapshot =
            PipelineFixture().snapshotGlobalOta.copy(publish = publish)
        override suspend fun update(
            workspaceId: String,
            change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
        ): Result<ConfigurationSnapshot> = error("unused")
        override suspend fun device(workspaceId: String): TargetDevice? = null
    }

    private val folder = Folder()
    private val ws = Workspace(id = "ws", name = "ws", path = "/w", linuxPath = "/w")
    private val settings = Settings(
        PublishSettings(provider = PublishProvider.LOCAL_EXPORT, localExportFolder = "D:\\ROM\\releases"),
    )
    private val publish = PublishReleaseUseCase(Workspaces(ws), settings, folder) {
        it.replace("D:\\", "/mnt/d/").replace('\\', '/')
    }

    private fun built(zip: String = "LtiRom-1.0.zip", content: String = "rom") {
        folder.files["/w/out/package/$zip"] = content
        folder.files["/w/out/manifest.json"] =
            """{"response":[{"filename":"$zip","size":3,"sha256":"${sha(content)}"}]}"""
    }

    @Test
    fun aBuiltReleaseIsExportedAndVerifiedAndAnInterruptedOneResumes() = runTest {
        built()
        folder.corruptCopies = true

        val failed = assertNotNull(publish.publish("ws"))
        assertEquals(PublishStepState.FAILED, failed.steps.first { it.id == "verify-zip" }.state)
        assertNull(failed.outcome)

        folder.corruptCopies = false
        val resumed = assertNotNull(publish.publish("ws"))
        // Resuming goes on from the failed step, and a copy that failed verification is made again.
        assertEquals("Exported to /mnt/d/ROM/releases", resumed.outcome)
        assertEquals("rom", folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"])
        assertTrue("/mnt/d/ROM/releases/LtiRom-1.0.json" in folder.files)
        assertTrue(folder.files.keys.none { it.endsWith(".partial") })
    }

    @Test
    fun nothingIsPublishedWithoutAReleaseOrADestinationOrForAnUnsignedPackage() = runTest {
        val none = publish.preflight("ws").problems
        assertEquals(listOf("No release yet: run the build through Release metadata first."), none)

        built()
        folder.files["/w/out/package/UNSIGNED"] = ""
        settings.publish = PublishSettings(provider = PublishProvider.LOCAL_EXPORT)
        val problems = publish.preflight("ws").problems
        assertTrue(problems.any { "unsigned" in it } && problems.any { "export folder" in it }, "$problems")
        assertNull(publish.publish("ws"))
    }

    @Test
    fun theExportFolderMustBeAWindowsFolder() = runTest {
        built()
        // A single leading backslash (a mistyped UNC path) is not a folder.
        for (folder in listOf("ROM", "\\wsl.localhost\\Ubuntu\\x", "relative\\path")) {
            settings.publish = PublishSettings(provider = PublishProvider.LOCAL_EXPORT, localExportFolder = folder)
            assertTrue(publish.preflight("ws").problems.any { "export folder" in it }, folder)
        }
        for (folder in listOf("D:\\ROM\\releases", "D:/ROM", "\\\\wsl.localhost\\Ubuntu\\home\\x")) {
            settings.publish = PublishSettings(provider = PublishProvider.LOCAL_EXPORT, localExportFolder = folder)
            assertEquals(emptyList(), publish.preflight("ws").problems, folder)
        }
    }

    @Test
    fun anExistingPackageIsNotOverwrittenUnlessThePolicySaysSo() = runTest {
        built()
        folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"] = "older"
        val refused = assertNotNull(publish.publish("ws"))
        assertEquals(PublishStepState.FAILED, refused.steps.first { it.id == "export-zip" }.state)
        assertEquals("older", folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"])

        repeat(2) {
            val retry = assertNotNull(publish.publish("ws"))
            assertEquals(PublishStepState.FAILED, retry.steps.first { it.id == "export-zip" }.state)
            assertEquals("older", folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"])
        }

        settings.publish = settings.publish.copy(collisionPolicy = CollisionPolicy.OVERWRITE)
        assertNotNull(publish.publish("ws")?.outcome)
        assertEquals("rom", folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"])
    }

    @Test
    fun skippingAnExistingLocalPackageNeverAuthorizesReplacementOnRetry() = runTest {
        built()
        settings.publish = settings.publish.copy(collisionPolicy = CollisionPolicy.SKIP)
        folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"] = "older"
        repeat(3) {
            assertNull(assertNotNull(publish.publish("ws")).outcome)
            assertEquals("older", folder.files["/mnt/d/ROM/releases/LtiRom-1.0.zip"])
        }
    }

    @Test
    fun differingGithubAssetsRespectTheCollisionPolicyOnEveryRetry() = runTest {
        built()
        for (policy in listOf(CollisionPolicy.FAIL, CollisionPolicy.SKIP)) {
            settings.publish = PublishSettings(
                provider = PublishProvider.GITHUB_RELEASE,
                repository = "me/rom-test",
                sourceTag = policy.name,
                collisionPolicy = policy,
            )
            folder.releases[policy.name] = mutableMapOf("LtiRom-1.0.zip" to "older")
            repeat(3) {
                assertNull(assertNotNull(publish.publish("ws")).outcome)
                assertEquals("older", folder.releases.getValue(policy.name)["LtiRom-1.0.zip"])
            }
        }
        settings.publish = settings.publish.copy(collisionPolicy = CollisionPolicy.OVERWRITE)
        assertNotNull(publish.publish("ws")?.outcome)
        assertEquals("rom", folder.releases.getValue("SKIP")["LtiRom-1.0.zip"])
    }

    @Test
    fun changingTheTagPublishesTheSamePackageToTheNewRelease() = runTest {
        built()
        settings.publish = PublishSettings(
            provider = PublishProvider.GITHUB_RELEASE,
            repository = "me/rom-test",
            sourceTag = "first",
        )
        assertNotNull(publish.publish("ws")?.outcome)
        settings.publish = settings.publish.copy(sourceTag = "second")
        assertNotNull(publish.publish("ws")?.outcome)
        assertEquals("rom", folder.releases.getValue("second")["LtiRom-1.0.zip"])
    }

    @Test
    fun changingTheManifestDestinationPublishesItEvenWhenTheReleaseWasAlreadyUploaded() = runTest {
        built()
        settings.publish = PublishSettings(provider = PublishProvider.GITHUB_RELEASE, repository = "me/rom-test")
        assertNotNull(publish.publish("ws")?.outcome)
        settings.publish = settings.publish.copy(manifestRepository = "me/ota", manifestPath = "device.json")
        assertNotNull(publish.publish("ws")?.outcome)
        assertTrue("repos/me/ota/contents/device.json" in folder.repoFiles)
        settings.publish = settings.publish.copy(manifestRepository = "me/new-ota", manifestPath = "new-device.json")
        assertNotNull(publish.publish("ws")?.outcome)
        assertTrue("repos/me/new-ota/contents/new-device.json" in folder.repoFiles)
    }

    @Test
    fun renamingTheSamePackageStartsANewExport() = runTest {
        built()
        assertNotNull(publish.publish("ws")?.outcome)
        built(zip = "renamed.zip")
        assertNotNull(publish.publish("ws")?.outcome)
        assertEquals("rom", folder.files["/mnt/d/ROM/releases/renamed.zip"])
        assertTrue("/mnt/d/ROM/releases/renamed.json" in folder.files)
    }

    @Test
    fun aGithubReleaseIsUploadedVerifiedAndAddedToTheOtaManifest() = runTest {
        built()
        settings.publish = PublishSettings(
            provider = PublishProvider.GITHUB_RELEASE,
            repository = "me/rom-test",
            manifestRepository = "me/ota",
            manifestPath = "PQ84P01.json",
        )
        val journal = assertNotNull(publish.publish("ws"))

        assertEquals("Published to github.com/me/rom-test", journal.outcome, "${journal.steps}")
        assertEquals("rom", folder.releases.getValue("LtiRom-1.0")["LtiRom-1.0.zip"])
        val manifest = folder.repoFiles.getValue("repos/me/ota/contents/PQ84P01.json")
        assertTrue("https://github.com/me/rom-test/releases/download/LtiRom-1.0/LtiRom-1.0.zip" in manifest, manifest)
    }

    @Test
    fun aCancelledUploadIsJournaledAndResumingKeepsWhatReachedGithub() = runTest {
        built()
        settings.publish = PublishSettings(provider = PublishProvider.GITHUB_RELEASE, repository = "me/rom-test")
        folder.uploadGate = kotlinx.coroutines.CompletableDeferred()
        val transfers = mutableListOf<PublishTransfer>()
        val running = launch { publish.publish("ws", onTransfer = { transfers += it }) }
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()
        assertTrue(transfers.any { it.stepId == "upload-assets" && it.done == 1L && it.total == 1L }, "$transfers")

        running.cancel()
        running.join()
        val cancelled = assertNotNull(publish.preflight("ws").journal).steps.first { it.id == "upload-assets" }
        assertEquals(PublishStepState.FAILED to PublishReleaseUseCase.CANCELLED, cancelled.state to cancelled.detail)

        // The asset got there before the cancel: resuming keeps it (collision policy "fail" is for others' assets).
        folder.uploadGate = null
        val resumed = assertNotNull(publish.publish("ws"))
        assertEquals("Published to github.com/me/rom-test", resumed.outcome, "${resumed.steps}")
    }

    @Test
    fun aProgressReadingThatFailsNeverFailsTheUpload() = runTest {
        built()
        settings.publish = PublishSettings(provider = PublishProvider.GITHUB_RELEASE, repository = "me/rom-test")
        folder.failingSamples = true
        folder.uploadGate = kotlinx.coroutines.CompletableDeferred()
        val running = async { publish.publish("ws") }
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()
        folder.uploadGate?.complete(Unit)
        val journal = assertNotNull(running.await())
        assertEquals("Published to github.com/me/rom-test", journal.outcome, "${journal.steps}")
    }

    private companion object {
        fun sha(text: String): String =
            MessageDigest.getInstance("SHA-256").digest(text.encodeToByteArray()).joinToString("") { "%02x".format(it) }
    }
}
