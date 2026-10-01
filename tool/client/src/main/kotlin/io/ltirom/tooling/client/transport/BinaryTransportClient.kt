package io.ltirom.tooling.client.transport

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.*
import io.ltirom.tooling.core.ports.DaemonSupervisorPort
import io.ltirom.tooling.core.remote.BinaryTransferResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TRANSFER_CHUNK_SIZE = 256 * 1024

public data class TransferProgress(
    val bytesTransferred: Long,
    val totalBytes: Long,
    val fraction: Float = if (totalBytes > 0) bytesTransferred.toFloat() / totalBytes else 0f
)

/**
 * High-performance client for dual-channel zero-copy binary streaming
 * of multi-gigabyte Android ROM images (super.img, payload.bin, boot.img).
 */
public class BinaryTransportClient(
    private val supervisor: DaemonSupervisorPort,
    private val httpClient: HttpClient
) {
    public suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        onProgress: ((TransferProgress) -> Unit)? = null
    ): BinaryTransferResponse = withContext(Dispatchers.IO) {
        val conn = supervisor.ensureStarted()
        val totalSize = localFile.length()

        val content = object : OutgoingContent.WriteChannelContent() {
            override val contentLength: Long = totalSize
            override val contentType: ContentType = ContentType.Application.OctetStream

            override suspend fun writeTo(channel: ByteWriteChannel) {
                var bytesSent = 0L
                localFile.inputStream().use { input ->
                    val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        channel.writeFully(buffer, 0, read)
                        bytesSent += read
                        onProgress?.invoke(TransferProgress(bytesSent, totalSize))
                    }
                }
            }
        }

        val response = httpClient.post("${conn.httpBaseUrl}/api/v1/binary/upload?path=$remotePath") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
            setBody(content)
        }
        response.body<BinaryTransferResponse>()
    }

    public suspend fun downloadFile(
        remotePath: String,
        destinationFile: File,
        onProgress: ((TransferProgress) -> Unit)? = null
    ): Long = withContext(Dispatchers.IO) {
        val conn = supervisor.ensureStarted()
        destinationFile.parentFile?.mkdirs()

        httpClient.prepareGet("${conn.httpBaseUrl}/api/v1/binary/download?path=$remotePath") {
            header(HttpHeaders.Authorization, "Bearer ${conn.token}")
        }.execute { response ->
            val totalSize = response.contentLength() ?: -1L
            val channel = response.bodyAsChannel()
            var bytesReceived = 0L

            destinationFile.outputStream().use { output ->
                val buffer = ByteArray(TRANSFER_CHUNK_SIZE)
                while (!channel.isClosedForRead) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read <= 0) break
                    output.write(buffer, 0, read)
                    bytesReceived += read
                    onProgress?.invoke(TransferProgress(bytesReceived, totalSize))
                }
            }
            bytesReceived
        }
    }
}
