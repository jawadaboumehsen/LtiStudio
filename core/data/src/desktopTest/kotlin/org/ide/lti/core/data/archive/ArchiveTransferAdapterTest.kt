/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.archive

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES
import org.ide.lti.core.domain.ports.TransferProgress
import org.ide.lti.core.domain.ports.TransferSession
import org.ide.lti.core.domain.ports.TransferSource
import org.ide.lti.core.domain.ports.TransferState
import org.ide.lti.core.model.workspace.Workspace
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ArchiveTransferAdapterTest {

    private lateinit var tempDir: Path
    private lateinit var workspace: Workspace
    private val adapter = ArchiveTransferAdapter()

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("archive-transfer-test")
        workspace = Workspace(
            id = "ws-transfer-test",
            name = "Test Workspace",
            path = tempDir.toString(),
        )
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

    private fun createPayloadFile(path: Path, sizeBytes: Long): String {
        Files.createDirectories(path.parent)
        val md = MessageDigest.getInstance("SHA-256")
        val chunk = ByteArray(1024 * 1024)
        for (i in chunk.indices) {
            chunk[i] = (i % 251).toByte()
        }
        Files.newOutputStream(path).use { out ->
            var written = 0L
            while (written < sizeBytes) {
                val toWrite = minOf(chunk.size.toLong(), sizeBytes - written).toInt()
                out.write(chunk, 0, toWrite)
                md.update(chunk, 0, toWrite)
                written += toWrite
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun `a 12 MiB local file transfers with correct digest and monotonic progress crossing 8 MiB boundary`() =
        runBlocking {
            val sourceFile = tempDir.resolve("external/source_12mib.bin")
            val sizeBytes = 12L * 1024 * 1024
            val expectedDigest = createPayloadFile(sourceFile, sizeBytes)

            val session = TransferSession(
                id = "session-12mib",
                workspaceId = workspace.id,
                capturedRevision = 1,
                source = TransferSource.LocalFile(sourceFile.toString()),
                stagingRelPath = "firmware/downloaded/firmware.zip",
                bytesTransferred = 0L,
                totalBytes = sizeBytes,
                state = TransferState.SELECTED,
            )

            val emissions = mutableListOf<TransferProgress>()
            adapter.transfer(workspace, session, expectedDigest).collect { progress ->
                emissions.add(progress)
            }

            val progressEmissions = emissions.filterIsInstance<TransferProgress.Progress>()
            assertTrue(progressEmissions.size >= 2, "Expected at least 2 Progress emissions across 8 MiB boundary")

            var previous = 0L
            for (p in progressEmissions) {
                assertTrue(
                    p.session.bytesTransferred > previous,
                    "Progress bytesTransferred must be strictly monotonic: ${p.session.bytesTransferred} <= $previous",
                )
                previous = p.session.bytesTransferred
            }

            assertEquals(MAX_TRANSFER_CHUNK_BYTES, progressEmissions.first().session.bytesTransferred)
            assertEquals(sizeBytes, progressEmissions.last().session.bytesTransferred)

            val finalized = emissions.last() as TransferProgress.Finalized
            assertEquals("session-12mib", finalized.receipt.sessionId)
            assertEquals("firmware/downloaded/firmware.zip", finalized.receipt.finalRelPath)
            assertEquals(sizeBytes, finalized.receipt.sizeBytes)
            assertEquals(expectedDigest, finalized.receipt.sha256)
            assertTrue(finalized.receipt.verifiedAgainstExpected)

            val finalPath = tempDir.resolve("firmware/downloaded/firmware.zip")
            assertTrue(Files.exists(finalPath))
            assertEquals(sizeBytes, Files.size(finalPath))

            val partPath = tempDir.resolve(".transfer/session-12mib.part")
            assertFalse(Files.exists(partPath), "Staging .part file must not exist after finalize")
        }

    @Test
    fun `transfer with null expectedSha256 finalizes with verifiedAgainstExpected false`() = runBlocking {
        val sourceFile = tempDir.resolve("external/source_null_sha.bin")
        val sizeBytes = 2L * 1024 * 1024
        val expectedDigest = createPayloadFile(sourceFile, sizeBytes)

        val session = TransferSession(
            id = "session-null-sha",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.LocalFile(sourceFile.toString()),
            stagingRelPath = "firmware/downloaded/unverified.zip",
            bytesTransferred = 0L,
            totalBytes = sizeBytes,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
        }

        val finalized = emissions.last() as TransferProgress.Finalized
        assertEquals(expectedDigest, finalized.receipt.sha256)
        assertFalse(finalized.receipt.verifiedAgainstExpected)
    }

    @Test
    fun `a wrong expectedSha256 fails with CHECKSUM_MISMATCH and leaves no finalized file and no part`() = runBlocking {
        val sourceFile = tempDir.resolve("external/source_wrong_sha.bin")
        val sizeBytes = 10L * 1024 * 1024
        createPayloadFile(sourceFile, sizeBytes)

        val session = TransferSession(
            id = "session-wrong-sha",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.LocalFile(sourceFile.toString()),
            stagingRelPath = "firmware/downloaded/corrupt.zip",
            bytesTransferred = 0L,
            totalBytes = sizeBytes,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, "0000000000000000000000000000000000000000000000000000000000000000")
            .collect { progress ->
                emissions.add(progress)
            }

        val failed = emissions.last() as TransferProgress.Failed
        assertEquals(ArchiveTransferCodes.CHECKSUM_MISMATCH, failed.reason)
        assertEquals(ArchiveTransferCodes.CHECKSUM_MISMATCH, failed.session.error)
        assertEquals(TransferState.FAILED, failed.session.state)

        val finalPath = tempDir.resolve("firmware/downloaded/corrupt.zip")
        assertFalse(Files.exists(finalPath), "Finalized file must not exist after checksum mismatch")

        val partPath = tempDir.resolve(".transfer/session-wrong-sha.part")
        assertFalse(Files.exists(partPath), ".part file must not exist after checksum mismatch")
    }

    @Test
    fun `cancel mid-transfer emits Cancelled and cleans up`() = runBlocking {
        val sourceFile = tempDir.resolve("external/source_cancel.bin")
        val sizeBytes = 16L * 1024 * 1024
        createPayloadFile(sourceFile, sizeBytes)

        val sessionId = "session-cancel"
        val session = TransferSession(
            id = sessionId,
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.LocalFile(sourceFile.toString()),
            stagingRelPath = "firmware/downloaded/cancelled.zip",
            bytesTransferred = 0L,
            totalBytes = sizeBytes,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
            if (progress is TransferProgress.Progress) {
                adapter.cancel(sessionId)
            }
        }

        val lastEmission = emissions.last()
        assertTrue(lastEmission is TransferProgress.Cancelled, "Expected terminal emission to be Cancelled")
        assertEquals(TransferState.CANCELLED, lastEmission.session.state)
        assertTrue(
            lastEmission.session.cleanupEvidence.isNotEmpty(),
            "Expected cleanupEvidence to record the cleaned .part file",
        )

        val finalPath = tempDir.resolve("firmware/downloaded/cancelled.zip")
        assertFalse(Files.exists(finalPath), "Finalized file must not exist after cancellation")

        val partPath = tempDir.resolve(".transfer/session-cancel.part")
        assertFalse(Files.exists(partPath), ".part file must be cleaned up after cancellation")
    }

    @Test
    fun `missing source emits Failed with SOURCE_MISSING`() = runBlocking {
        val session = TransferSession(
            id = "session-missing",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.LocalFile(tempDir.resolve("non_existent.bin").toString()),
            stagingRelPath = "firmware/downloaded/missing.zip",
            bytesTransferred = 0L,
            totalBytes = null,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
        }

        val failed = emissions.single() as TransferProgress.Failed
        assertEquals(ArchiveTransferCodes.SOURCE_MISSING, failed.reason)
        assertEquals(ArchiveTransferCodes.SOURCE_MISSING, failed.session.error)
        assertEquals(TransferState.FAILED, failed.session.state)
    }

    @Test
    fun `path escaping workspace emits Failed with PATH_ESCAPES_WORKSPACE`() = runBlocking {
        val session = TransferSession(
            id = "session-escape",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.ExistingWorkspaceFile("../escaped_file.bin"),
            stagingRelPath = "firmware/downloaded/escaped.zip",
            bytesTransferred = 0L,
            totalBytes = null,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
        }

        val failed = emissions.single() as TransferProgress.Failed
        assertEquals(ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE, failed.reason)
        assertEquals(ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE, failed.session.error)
        assertEquals(TransferState.FAILED, failed.session.state)
    }

    @Test
    fun `http URL fails with INSECURE_SOURCE without hitting network`() = runBlocking {
        val session = TransferSession(
            id = "session-http",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.RemoteUrl("http://example.com/fw.zip"),
            stagingRelPath = "firmware/downloaded/insecure.zip",
            bytesTransferred = 0L,
            totalBytes = null,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
        }

        val failed = emissions.single() as TransferProgress.Failed
        assertEquals(ArchiveTransferCodes.INSECURE_SOURCE, failed.reason)
        assertEquals(ArchiveTransferCodes.INSECURE_SOURCE, failed.session.error)
        assertEquals(TransferState.FAILED, failed.session.state)
    }

    @Test
    fun `URL carrying userinfo credentials fails with CREDENTIALS_IN_URL without hitting network`() = runBlocking {
        val session = TransferSession(
            id = "session-creds",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.RemoteUrl("https://user:password@example.com/fw.zip"),
            stagingRelPath = "firmware/downloaded/creds.zip",
            bytesTransferred = 0L,
            totalBytes = null,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, null).collect { progress ->
            emissions.add(progress)
        }

        val failed = emissions.single() as TransferProgress.Failed
        assertEquals(ArchiveTransferCodes.CREDENTIALS_IN_URL, failed.reason)
        assertEquals(ArchiveTransferCodes.CREDENTIALS_IN_URL, failed.session.error)
        assertEquals(TransferState.FAILED, failed.session.state)
    }

    @Test
    fun `probeExisting returns real size and digest for file and reports directory as not regular file`() =
        runBlocking {
            val regularFile = tempDir.resolve("firmware/existing.bin")
            val size = 512L * 1024
            val digest = createPayloadFile(regularFile, size)

            val fileProbe = adapter.probeExisting(workspace, "firmware/existing.bin")
            assertTrue(fileProbe.exists)
            assertTrue(fileProbe.isRegularFile)
            assertEquals(size, fileProbe.sizeBytes)
            assertEquals(digest, fileProbe.sha256)

            val dirPath = tempDir.resolve("firmware/subdir")
            Files.createDirectories(dirPath)

            val dirProbe = adapter.probeExisting(workspace, "firmware/subdir")
            assertTrue(dirProbe.exists)
            assertFalse(dirProbe.isRegularFile)
            assertNull(dirProbe.sizeBytes)
            assertNull(dirProbe.sha256)

            val missingProbe = adapter.probeExisting(workspace, "firmware/non_existent.bin")
            assertFalse(missingProbe.exists)
            assertFalse(missingProbe.isRegularFile)
            assertNull(missingProbe.sizeBytes)
            assertNull(missingProbe.sha256)

            val escapingProbe = adapter.probeExisting(workspace, "../escaping.bin")
            assertFalse(escapingProbe.exists)
            assertFalse(escapingProbe.isRegularFile)
            assertNull(escapingProbe.sizeBytes)
            assertNull(escapingProbe.sha256)
        }

    @Test
    fun `existing workspace file transfer streams and finalizes successfully`() = runBlocking {
        val relPath = "raw_archive/source.zip"
        val fullSource = tempDir.resolve(relPath)
        val size = 1024L * 1024
        val digest = createPayloadFile(fullSource, size)

        val session = TransferSession(
            id = "session-ws-file",
            workspaceId = workspace.id,
            capturedRevision = 1,
            source = TransferSource.ExistingWorkspaceFile(relPath),
            stagingRelPath = "firmware/downloaded/promoted.zip",
            bytesTransferred = 0L,
            totalBytes = size,
            state = TransferState.SELECTED,
        )

        val emissions = mutableListOf<TransferProgress>()
        adapter.transfer(workspace, session, digest).collect { progress ->
            emissions.add(progress)
        }

        val finalized = emissions.last() as TransferProgress.Finalized
        assertEquals(digest, finalized.receipt.sha256)
        assertEquals(size, finalized.receipt.sizeBytes)
        assertTrue(finalized.receipt.verifiedAgainstExpected)
        assertTrue(Files.exists(tempDir.resolve("firmware/downloaded/promoted.zip")))
    }
}
