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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.domain.ports.BoundedTransferPort
import org.ide.lti.core.domain.ports.ExistingFileProbe
import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES
import org.ide.lti.core.domain.ports.TransferProgress
import org.ide.lti.core.domain.ports.TransferReceipt
import org.ide.lti.core.domain.ports.TransferSession
import org.ide.lti.core.domain.ports.TransferSource
import org.ide.lti.core.domain.ports.TransferState
import org.ide.lti.core.model.workspace.Workspace
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

public object ArchiveTransferCodes {
    public const val SOURCE_MISSING: String = "SOURCE_MISSING"
    public const val PATH_ESCAPES_WORKSPACE: String = "PATH_ESCAPES_WORKSPACE"
    public const val INSECURE_SOURCE: String = "INSECURE_SOURCE"
    public const val CREDENTIALS_IN_URL: String = "CREDENTIALS_IN_URL"
    public const val CHECKSUM_MISMATCH: String = "CHECKSUM_MISMATCH"
    public const val TRANSFER_FAILED: String = "TRANSFER_FAILED"
    public const val DISK_FULL: String = "DISK_FULL"
}

public class ArchiveTransferAdapter : BoundedTransferPort {

    private val cancellations = ConcurrentHashMap<String, AtomicBoolean>()

    override fun transfer(ws: Workspace, session: TransferSession, expectedSha256: String?): Flow<TransferProgress> =
        flow {
            val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
            if (!Files.exists(wsRoot)) {
                Files.createDirectories(wsRoot)
            }

            val partFile = wsRoot.resolve(".transfer/${session.id}.part").normalize()
            val finalFile = wsRoot.resolve(session.stagingRelPath).normalize()

            if (!finalFile.startsWith(wsRoot)) {
                emit(
                    TransferProgress.Failed(
                        session = session.copy(
                            state = TransferState.FAILED,
                            error = ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE,
                        ),
                        reason = ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE,
                    ),
                )
                return@flow
            }

            val isCancelled = cancellations.computeIfAbsent(session.id) { AtomicBoolean(false) }
            if (isCancelled.get()) {
                emit(
                    TransferProgress.Cancelled(
                        session = session.copy(
                            state = TransferState.CANCELLED,
                            cleanupEvidence = emptyList(),
                        ),
                    ),
                )
                return@flow
            }

            val prepared = prepareSource(session, wsRoot)
            if (prepared is PreparedSource.Failure) {
                emit(
                    TransferProgress.Failed(
                        session = session.copy(
                            state = TransferState.FAILED,
                            error = prepared.code,
                        ),
                        reason = prepared.message,
                    ),
                )
                return@flow
            }
            val ready = prepared as PreparedSource.Ready

            try {
                val streamResult = streamAndHash(
                    input = ready.stream,
                    partFile = partFile,
                    isCancelled = isCancelled,
                ) { transferred ->
                    emit(
                        TransferProgress.Progress(
                            session = session.copy(
                                state = TransferState.TRANSFERRING,
                                bytesTransferred = transferred,
                                totalBytes = ready.totalBytes,
                            ),
                        ),
                    )
                }

                when (streamResult) {
                    is StreamResult.Cancelled -> {
                        emit(
                            TransferProgress.Cancelled(
                                session = session.copy(
                                    state = TransferState.CANCELLED,
                                    bytesTransferred = streamResult.bytesTransferred,
                                    totalBytes = ready.totalBytes,
                                    cleanupEvidence = streamResult.cleanupEvidence,
                                ),
                            ),
                        )
                    }
                    is StreamResult.IoFailure -> {
                        emit(
                            TransferProgress.Failed(
                                session = session.copy(
                                    state = TransferState.FAILED,
                                    error = streamResult.code,
                                    bytesTransferred = streamResult.bytesTransferred,
                                    totalBytes = ready.totalBytes,
                                    cleanupEvidence = streamResult.cleanupEvidence,
                                ),
                                reason = streamResult.message,
                            ),
                        )
                    }
                    is StreamResult.Success -> {
                        val checksumMatches = expectedSha256 == null ||
                            streamResult.sha256.equals(expectedSha256, ignoreCase = true)
                        if (!checksumMatches) {
                            val cleaned = mutableListOf<String>()
                            if (Files.deleteIfExists(partFile)) {
                                cleaned.add(partFile.toString())
                            }
                            emit(
                                TransferProgress.Failed(
                                    session = session.copy(
                                        state = TransferState.FAILED,
                                        error = ArchiveTransferCodes.CHECKSUM_MISMATCH,
                                        bytesTransferred = streamResult.bytesTransferred,
                                        totalBytes = ready.totalBytes,
                                        cleanupEvidence = cleaned,
                                    ),
                                    reason = ArchiveTransferCodes.CHECKSUM_MISMATCH,
                                ),
                            )
                            return@flow
                        }

                        val moveError = finalizeFile(partFile, finalFile)
                        if (moveError != null) {
                            emit(
                                TransferProgress.Failed(
                                    session = session.copy(
                                        state = TransferState.FAILED,
                                        error = ArchiveTransferCodes.TRANSFER_FAILED,
                                        bytesTransferred = streamResult.bytesTransferred,
                                    ),
                                    reason = moveError,
                                ),
                            )
                            return@flow
                        }

                        emit(
                            TransferProgress.Finalized(
                                TransferReceipt(
                                    sessionId = session.id,
                                    finalRelPath = session.stagingRelPath,
                                    sizeBytes = streamResult.bytesTransferred,
                                    sha256 = streamResult.sha256,
                                    verifiedAgainstExpected = expectedSha256 != null,
                                ),
                            ),
                        )
                    }
                }
            } finally {
                cancellations.remove(session.id)
            }
        }

    private fun prepareSource(session: TransferSession, wsRoot: Path): PreparedSource =
        when (val src = session.source) {
            is TransferSource.LocalFile -> prepareLocalFile(src, wsRoot)
            is TransferSource.ExistingWorkspaceFile -> prepareWorkspaceFile(src, wsRoot)
            is TransferSource.RemoteUrl -> prepareRemoteUrl(src, wsRoot, session.totalBytes)
        }

    private fun prepareLocalFile(src: TransferSource.LocalFile, wsRoot: Path): PreparedSource {
        val srcPath = Path.of(src.absolutePath).toAbsolutePath().normalize()
        val exists = Files.exists(srcPath, LinkOption.NOFOLLOW_LINKS) &&
            Files.isRegularFile(srcPath, LinkOption.NOFOLLOW_LINKS)
        val srcSize = if (exists) Files.size(srcPath) else 0L
        val usable = Files.getFileStore(wsRoot).usableSpace

        return when {
            !exists -> PreparedSource.Failure(
                ArchiveTransferCodes.SOURCE_MISSING,
                ArchiveTransferCodes.SOURCE_MISSING,
            )
            usable < srcSize -> PreparedSource.Failure(
                ArchiveTransferCodes.DISK_FULL,
                ArchiveTransferCodes.DISK_FULL,
            )
            else -> try {
                PreparedSource.Ready(Files.newInputStream(srcPath), srcSize)
            } catch (e: IOException) {
                PreparedSource.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}",
                )
            }
        }
    }

    private fun prepareWorkspaceFile(src: TransferSource.ExistingWorkspaceFile, wsRoot: Path): PreparedSource {
        val srcPath = wsRoot.resolve(src.relPath).normalize()
        val inBounds = srcPath.startsWith(wsRoot)
        val exists = inBounds && Files.exists(srcPath, LinkOption.NOFOLLOW_LINKS) &&
            Files.isRegularFile(srcPath, LinkOption.NOFOLLOW_LINKS)
        val srcSize = if (exists) Files.size(srcPath) else 0L
        val usable = Files.getFileStore(wsRoot).usableSpace

        return when {
            !inBounds -> PreparedSource.Failure(
                ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE,
                ArchiveTransferCodes.PATH_ESCAPES_WORKSPACE,
            )
            !exists -> PreparedSource.Failure(
                ArchiveTransferCodes.SOURCE_MISSING,
                ArchiveTransferCodes.SOURCE_MISSING,
            )
            usable < srcSize -> PreparedSource.Failure(
                ArchiveTransferCodes.DISK_FULL,
                ArchiveTransferCodes.DISK_FULL,
            )
            else -> try {
                PreparedSource.Ready(Files.newInputStream(srcPath), srcSize)
            } catch (e: IOException) {
                PreparedSource.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}",
                )
            }
        }
    }

    private fun prepareRemoteUrl(
        src: TransferSource.RemoteUrl,
        wsRoot: Path,
        sessionTotalBytes: Long?,
    ): PreparedSource {
        val uri = try {
            URI(src.url)
        } catch (_: Exception) {
            null
        }

        return when {
            uri == null -> PreparedSource.Failure(
                ArchiveTransferCodes.TRANSFER_FAILED,
                "${ArchiveTransferCodes.TRANSFER_FAILED}: Invalid URI ${src.url}",
            )
            uri.scheme?.lowercase() != "https" -> PreparedSource.Failure(
                ArchiveTransferCodes.INSECURE_SOURCE,
                ArchiveTransferCodes.INSECURE_SOURCE,
            )
            !uri.userInfo.isNullOrBlank() -> PreparedSource.Failure(
                ArchiveTransferCodes.CREDENTIALS_IN_URL,
                ArchiveTransferCodes.CREDENTIALS_IN_URL,
            )
            else -> handleRemoteOpenResult(openRemoteStreamWithRedirects(uri), wsRoot, sessionTotalBytes)
        }
    }

    private fun handleRemoteOpenResult(
        opened: RemoteOpenResult,
        wsRoot: Path,
        sessionTotalBytes: Long?,
    ): PreparedSource = when (opened) {
        is RemoteOpenResult.Failure -> PreparedSource.Failure(opened.code, opened.message)
        is RemoteOpenResult.Success -> {
            val total = if (opened.contentLength >= 0) opened.contentLength else sessionTotalBytes
            val hasSpace = opened.contentLength < 0 || Files.getFileStore(wsRoot).usableSpace >= opened.contentLength
            if (hasSpace) {
                PreparedSource.Ready(opened.stream, total)
            } else {
                opened.stream.close()
                PreparedSource.Failure(ArchiveTransferCodes.DISK_FULL, ArchiveTransferCodes.DISK_FULL)
            }
        }
    }

    private suspend fun streamAndHash(
        input: InputStream,
        partFile: Path,
        isCancelled: AtomicBoolean,
        onProgress: suspend (Long) -> Unit,
    ): StreamResult {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(MAX_TRANSFER_CHUNK_BYTES.toInt())
        Files.createDirectories(partFile.parent)
        Files.deleteIfExists(partFile)

        return try {
            val bytes = copyAndHash(input, partFile, buffer, md, isCancelled, onProgress)
            if (isCancelled.get()) {
                val cleaned = deletePartFile(partFile)
                StreamResult.Cancelled(bytes, cleaned)
            } else {
                val sha = md.digest().joinToString("") { "%02x".format(it) }
                StreamResult.Success(bytes, sha)
            }
        } catch (e: IOException) {
            val cleaned = deletePartFile(partFile)
            toIoFailure(e, cleaned)
        }
    }

    private suspend fun copyAndHash(
        input: InputStream,
        partFile: Path,
        buffer: ByteArray,
        md: MessageDigest,
        isCancelled: AtomicBoolean,
        onProgress: suspend (Long) -> Unit,
    ): Long = Files.newOutputStream(
        partFile,
        StandardOpenOption.CREATE,
        StandardOpenOption.WRITE,
        StandardOpenOption.TRUNCATE_EXISTING,
    ).use { output ->
        input.use { inStream ->
            pumpStream(inStream, output, buffer, md, isCancelled, onProgress)
        }
    }

    private suspend fun pumpStream(
        input: InputStream,
        output: OutputStream,
        buffer: ByteArray,
        md: MessageDigest,
        isCancelled: AtomicBoolean,
        onProgress: suspend (Long) -> Unit,
    ): Long {
        var transferred = 0L
        var isDone = false
        while (!isDone && !isCancelled.get()) {
            val read = input.read(buffer)
            if (read == -1) {
                isDone = true
            } else {
                output.write(buffer, 0, read)
                md.update(buffer, 0, read)
                transferred += read
                onProgress(transferred)
            }
        }
        return transferred
    }

    private fun deletePartFile(partFile: Path): List<String> {
        val cleaned = mutableListOf<String>()
        if (Files.deleteIfExists(partFile)) {
            cleaned.add(partFile.toString())
        }
        return cleaned
    }

    private fun toIoFailure(e: IOException, cleaned: List<String>): StreamResult {
        val isSpace = e.message?.contains("space", ignoreCase = true) == true ||
            e.message?.contains("full", ignoreCase = true) == true
        val code = if (isSpace) ArchiveTransferCodes.DISK_FULL else ArchiveTransferCodes.TRANSFER_FAILED
        val reason = if (isSpace) {
            ArchiveTransferCodes.DISK_FULL
        } else {
            "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}"
        }
        return StreamResult.IoFailure(code, reason, 0L, cleaned)
    }

    private fun finalizeFile(partFile: Path, finalFile: Path): String? {
        Files.createDirectories(finalFile.parent)
        try {
            Files.move(partFile, finalFile, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return null
        } catch (_: AtomicMoveNotSupportedException) {
            return try {
                copyStreamed(partFile, finalFile)
                Files.deleteIfExists(partFile)
                null
            } catch (e: IOException) {
                Files.deleteIfExists(partFile)
                Files.deleteIfExists(finalFile)
                "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}"
            }
        }
    }

    override suspend fun cancel(sessionId: String): Boolean {
        cancellations.computeIfAbsent(sessionId) { AtomicBoolean() }.set(true)
        return true
    }

    /**
     * Probes an existing file in the workspace to verify its existence, regular file status,
     * size in bytes, and streamed SHA-256 digest.
     *
     * The identity of the file is defined by its measured bytes (real size and SHA-256 digest),
     * not by its filename. A directory or symbolic link is not considered a regular file.
     */
    override suspend fun probeExisting(ws: Workspace, relPath: String): ExistingFileProbe {
        val wsRoot = Path.of(ws.path).toAbsolutePath().normalize()
        val file = wsRoot.resolve(relPath).normalize()
        val exists = file.startsWith(wsRoot) && Files.exists(file, LinkOption.NOFOLLOW_LINKS)
        val isRegular = exists && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isSymbolicLink(file)

        return if (isRegular) {
            ExistingFileProbe(
                exists = true,
                isRegularFile = true,
                sizeBytes = Files.size(file),
                sha256 = sha256Of(file),
            )
        } else {
            ExistingFileProbe(
                exists = exists,
                isRegularFile = false,
                sizeBytes = null,
                sha256 = null,
            )
        }
    }

    private fun sha256Of(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(MAX_TRANSFER_CHUNK_BYTES.toInt())
        Files.newInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyStreamed(source: Path, destination: Path) {
        val buffer = ByteArray(MAX_TRANSFER_CHUNK_BYTES.toInt())
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(
                destination,
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { output ->
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                }
            }
        }
    }

    private fun openRemoteStreamWithRedirects(initialUri: URI): RemoteOpenResult {
        var currentUri = initialUri
        var redirects = 0
        var finalResult: RemoteOpenResult? = null

        while (redirects <= MAX_REDIRECTS && finalResult == null) {
            when (val step = stepRemoteConnection(currentUri)) {
                is StepResult.Redirect -> {
                    redirects++
                    if (redirects > MAX_REDIRECTS) {
                        finalResult = RemoteOpenResult.Failure(
                            ArchiveTransferCodes.TRANSFER_FAILED,
                            "${ArchiveTransferCodes.TRANSFER_FAILED}: Too many redirects",
                        )
                    } else {
                        currentUri = step.nextUri
                    }
                }
                is StepResult.Done -> {
                    finalResult = step.result
                }
            }
        }

        return finalResult ?: RemoteOpenResult.Failure(
            ArchiveTransferCodes.TRANSFER_FAILED,
            "${ArchiveTransferCodes.TRANSFER_FAILED}: Too many redirects",
        )
    }

    private fun stepRemoteConnection(currentUri: URI): StepResult {
        val conn = try {
            (currentUri.toURL().openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
        } catch (e: IOException) {
            return StepResult.Done(
                RemoteOpenResult.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}",
                ),
            )
        }

        return try {
            handleHttpResponse(conn, conn.responseCode, currentUri)
        } catch (e: IOException) {
            conn.disconnect()
            StepResult.Done(
                RemoteOpenResult.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}",
                ),
            )
        }
    }

    private fun handleHttpResponse(conn: HttpURLConnection, code: Int, currentUri: URI): StepResult = when (code) {
        in REDIRECT_RANGE -> handleRedirectResponse(conn, currentUri)
        in SUCCESS_RANGE -> {
            val len = conn.contentLengthLong
            try {
                StepResult.Done(RemoteOpenResult.Success(conn.inputStream, len))
            } catch (e: IOException) {
                conn.disconnect()
                StepResult.Done(
                    RemoteOpenResult.Failure(
                        ArchiveTransferCodes.TRANSFER_FAILED,
                        "${ArchiveTransferCodes.TRANSFER_FAILED}: ${e.message}",
                    ),
                )
            }
        }
        else -> {
            conn.disconnect()
            StepResult.Done(
                RemoteOpenResult.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: HTTP $code",
                ),
            )
        }
    }

    private fun handleRedirectResponse(conn: HttpURLConnection, currentUri: URI): StepResult {
        val loc = conn.getHeaderField("Location")
        conn.disconnect()
        if (loc.isNullOrBlank()) {
            return StepResult.Done(
                RemoteOpenResult.Failure(
                    ArchiveTransferCodes.TRANSFER_FAILED,
                    "${ArchiveTransferCodes.TRANSFER_FAILED}: Redirect missing Location header",
                ),
            )
        }
        val nextUri = currentUri.resolve(loc)
        return when {
            nextUri.scheme?.lowercase() != "https" -> StepResult.Done(
                RemoteOpenResult.Failure(ArchiveTransferCodes.INSECURE_SOURCE, ArchiveTransferCodes.INSECURE_SOURCE),
            )
            !nextUri.userInfo.isNullOrBlank() -> StepResult.Done(
                RemoteOpenResult.Failure(
                    ArchiveTransferCodes.CREDENTIALS_IN_URL,
                    ArchiveTransferCodes.CREDENTIALS_IN_URL,
                ),
            )
            else -> StepResult.Redirect(nextUri)
        }
    }

    private sealed interface PreparedSource {
        data class Ready(val stream: InputStream, val totalBytes: Long?) : PreparedSource
        data class Failure(val code: String, val message: String) : PreparedSource
    }

    private sealed interface StreamResult {
        data class Success(val bytesTransferred: Long, val sha256: String) : StreamResult
        data class Cancelled(val bytesTransferred: Long, val cleanupEvidence: List<String>) : StreamResult
        data class IoFailure(
            val code: String,
            val message: String,
            val bytesTransferred: Long,
            val cleanupEvidence: List<String>,
        ) : StreamResult
    }

    private sealed interface RemoteOpenResult {
        data class Success(val stream: InputStream, val contentLength: Long) : RemoteOpenResult
        data class Failure(val code: String, val message: String) : RemoteOpenResult
    }

    private sealed interface StepResult {
        data class Redirect(val nextUri: URI) : StepResult
        data class Done(val result: RemoteOpenResult) : StepResult
    }

    private companion object {
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 15000
        private const val READ_TIMEOUT_MS = 30000
        private val REDIRECT_RANGE = 300..399
        private val SUCCESS_RANGE = 200..299
    }
}
