/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.checkpoint

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.pipeline.ManifestEntryKind
import org.ide.lti.core.domain.pipeline.StageCheckpoint
import org.ide.lti.core.domain.ports.StagingResult
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.Workspace
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CheckpointRepositoryTest {

    private lateinit var tempDir: Path
    private lateinit var workspace: Workspace
    private lateinit var sourceDir: Path
    private val repo = CheckpointRepository()

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("checkpoint-test")
        workspace = Workspace(
            id = "ws-test",
            name = "Test Workspace",
            path = tempDir.toString(),
        )
        sourceDir = tempDir.resolve("work/source_tree")
        Files.createDirectories(sourceDir.resolve("bin"))
        Files.createDirectories(sourceDir.resolve("configs"))
        Files.createDirectories(sourceDir.resolve("etc"))

        Files.writeString(sourceDir.resolve("bin/app"), "executable-content")
        Files.writeString(sourceDir.resolve("configs/fs_config-system"), "system/bin/app 0 0 755")
        Files.writeString(sourceDir.resolve("etc/sample.prop"), "ro.build.version=12345")
    }

    @AfterTest
    fun tearDown() {
        deleteRecursively(tempDir)
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.walkFileTree(
            path,
            object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.deleteIfExists(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    Files.deleteIfExists(dir)
                    return FileVisitResult.CONTINUE
                }
            },
        )
    }

    private fun createCheckpoint(rootRel: String = "work/source_tree"): StageCheckpoint {
        val rootPath = tempDir.resolve(rootRel)
        val manifest = manifestOf(rootPath)
        return StageCheckpoint(
            stageId = StageId.WORK_TREE_ASSEMBLY,
            stageVersion = 1,
            inputDigest = "input-hash-1",
            rootRelPath = rootRel,
            manifest = manifest,
            sidecarRelPaths = manifest.entries.filter { it.kind == ManifestEntryKind.SIDECAR }.map { it.relPath },
            outputDigest = manifest.canonicalDigest(),
            verification = null,
        )
    }

    @Test
    fun `stage copies a tree into staging and leaves source untouched`() {
        runBlocking {
            val checkpoint = createCheckpoint()
            val result = repo.stage(workspace, checkpoint, requiredBytes = 1000L)

            assertTrue(result is StagingResult.Staged)
            val handle = result.handle
            val stagingPath = tempDir.resolve(handle.stagingRelPath)

            assertTrue(Files.exists(stagingPath))
            assertTrue(Files.exists(stagingPath.resolve("bin/app")))
            assertTrue(Files.exists(stagingPath.resolve("configs/fs_config-system")))
            assertTrue(Files.exists(stagingPath.resolve("etc/sample.prop")))

            // Source must be completely untouched
            assertTrue(Files.exists(sourceDir.resolve("bin/app")))
            assertEquals("executable-content", Files.readString(sourceDir.resolve("bin/app")))
        }
    }

    @Test
    fun `insufficient disk is reported without copying`() {
        runBlocking {
            val checkpoint = createCheckpoint()
            val result = repo.stage(workspace, checkpoint, requiredBytes = Long.MAX_VALUE)

            assertTrue(result is StagingResult.InsufficientDisk)
            assertEquals(Long.MAX_VALUE, result.requiredBytes)

            val stagingRoot = tempDir.resolve(".staging")
            assertFalse(Files.exists(stagingRoot))
        }
    }

    @Test
    fun `source missing is reported when root rel path does not exist`() {
        runBlocking {
            val checkpoint = createCheckpoint().copy(rootRelPath = "missing/path")
            val result = repo.stage(workspace, checkpoint, requiredBytes = 100L)

            assertTrue(result is StagingResult.SourceMissing)
            assertEquals("missing/path", result.relPath)
        }
    }

    @Test
    fun `verify passes on an untouched tree and FAILS when one file bytes are changed while size stays identical`() {
        runBlocking {
            val checkpoint = createCheckpoint()

            // Verify clean tree
            val cleanReceipt = repo.verify(workspace, checkpoint)
            assertTrue(cleanReceipt.verified)
            assertEquals(0, cleanReceipt.mismatches.size)
            assertEquals("sha256-content", cleanReceipt.method)

            // Change one file's bytes while size stays exactly identical
            val propPath = sourceDir.resolve("etc/sample.prop")
            val originalSize = Files.size(propPath)
            Files.writeString(propPath, "ro.build.version=99999")
            assertEquals(originalSize, Files.size(propPath))

            val tamperedReceipt = repo.verify(workspace, checkpoint)
            assertFalse(tamperedReceipt.verified)
            assertEquals(listOf("etc/sample.prop"), tamperedReceipt.mismatches)
        }
    }

    @Test
    fun `promote moves staging dir, returns checkpoint matching digest, and reuses dir`() {
        runBlocking {
            val checkpoint = createCheckpoint()
            val stageResult = repo.stage(workspace, checkpoint, 1000L) as StagingResult.Staged
            val handle = stageResult.handle
            val stagedDir = tempDir.resolve(handle.stagingRelPath)
            val expectedManifest = manifestOf(stagedDir)

            val promoted = repo.promote(workspace, handle, expectedManifest)
            assertEquals(promoted.outputDigest, expectedManifest.canonicalDigest())
            assertEquals(
                "checkpoints/${checkpoint.stageId}/${expectedManifest.canonicalDigest()}",
                promoted.rootRelPath,
            )

            val promotedDir = tempDir.resolve(promoted.rootRelPath)
            assertTrue(Files.exists(promotedDir))
            assertFalse(Files.exists(stagedDir))

            // Second promote with identical content reuses existing directory
            val stageResult2 = repo.stage(workspace, checkpoint, 1000L) as StagingResult.Staged
            val handle2 = stageResult2.handle
            val stagedDir2 = tempDir.resolve(handle2.stagingRelPath)
            val promoted2 = repo.promote(workspace, handle2, expectedManifest)

            assertEquals(promoted.rootRelPath, promoted2.rootRelPath)
            assertEquals(promoted.outputDigest, promoted2.outputDigest)
            assertFalse(Files.exists(stagedDir2))
        }
    }

    @Test
    fun `promote throws IllegalStateException when staged manifest does not match expected`() {
        runBlocking {
            val checkpoint = createCheckpoint()
            val stageResult = repo.stage(workspace, checkpoint, 1000L) as StagingResult.Staged
            val handle = stageResult.handle
            val stagedDir = tempDir.resolve(handle.stagingRelPath)
            val expectedManifest = manifestOf(stagedDir)

            // Mutate staging dir after staging
            Files.writeString(stagedDir.resolve("bin/app"), "tampered-content")

            assertFailsWith<IllegalStateException> {
                repo.promote(workspace, handle, expectedManifest)
            }
        }
    }

    @Test
    fun `discard removes only the staging dir`() {
        runBlocking {
            val checkpoint = createCheckpoint()
            val stageResult = repo.stage(workspace, checkpoint, 1000L) as StagingResult.Staged
            val handle = stageResult.handle
            val stagingPath = tempDir.resolve(handle.stagingRelPath)

            assertTrue(Files.exists(stagingPath))
            repo.discard(workspace, handle)

            assertFalse(Files.exists(stagingPath))
            assertTrue(Files.exists(sourceDir.resolve("bin/app")))
        }
    }

    @Test
    fun `symlinks are recorded, not dereferenced`() {
        val testProbe = tempDir.resolve("symlink_probe")
        val testTarget = tempDir.resolve("symlink_target")
        Files.writeString(testTarget, "target")
        val canCreateSymlink = try {
            Files.createSymbolicLink(testProbe, testTarget)
            Files.deleteIfExists(testProbe)
            Files.deleteIfExists(testTarget)
            true
        } catch (_: Exception) {
            false
        }

        if (!canCreateSymlink) {
            println("Skipping symlink assertion: filesystem refuses symlink creation")
            return
        }

        val outsideTarget = tempDir.resolve("outside_secret.txt")
        Files.writeString(outsideTarget, "secret data that must not be copied")
        val linkPath = sourceDir.resolve("symlink_to_outside")
        Files.createSymbolicLink(linkPath, outsideTarget)

        val manifest = manifestOf(sourceDir)
        val entry = manifest.entries.firstOrNull { it.relPath == "symlink_to_outside" }
        assertNotNull(entry)
        assertEquals(ManifestEntryKind.SYMLINK, entry.kind)
    }
}
