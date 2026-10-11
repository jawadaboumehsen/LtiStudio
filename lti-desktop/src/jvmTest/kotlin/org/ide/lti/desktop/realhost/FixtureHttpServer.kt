package org.ide.lti.desktop.realhost

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Fixture HTTP server on 127.0.0.1:0 serving real release archives and SHA-256 checksums,
 * including tampered copies for error path validation.
 */
class FixtureHttpServer : AutoCloseable {
    private val server: HttpServer = HttpServer.create(InetSocketAddress("0.0.0.0", 0), 0)
    val port: Int get() = server.address.port
    val baseUrl: String get() = "http://127.0.0.1:$port"

    suspend fun resolveBaseUrl(transport: io.ltirom.tooling.core.ports.RemoteTransportPort? = null): String {
        val isWsl = System.getenv("LTI_REAL_HOST") == "wsl" || System.getProperty("lti.real.host") == "wsl"
        if (!isWsl || transport == null) {
            return baseUrl
        }
        val res =
            transport.execute(
                io.ltirom.tooling.core.remote.ToolExecutionRequest(
                    toolId = "sh",
                    arguments = listOf("-c", "ip route show | awk '/default/ {print \$3; exit}'"),
                    workingDirectory = "/tmp",
                ),
            )
        val hostIp = res.stdout.trim().ifBlank { "127.0.0.1" }
        return "http://$hostIp:$port"
    }

    val tarGzBytes: ByteArray = createSampleTarGz()
    val tarGzSha256: String = sha256(tarGzBytes)
    val tamperedTarGzBytes: ByteArray =
        tarGzBytes.copyOf().also {
            if (it.isNotEmpty()) it[it.size - 1] = (it[it.size - 1] + 1).toByte()
        }

    val zipBytes: ByteArray = createSampleZip()
    val zipSha256: String = sha256(zipBytes)
    val tamperedZipBytes: ByteArray =
        zipBytes.copyOf().also {
            if (it.isNotEmpty()) it[it.size - 1] = (it[it.size - 1] + 1).toByte()
        }

    init {
        server.createContext("/releases/sample.tar.gz", BytesHandler(tarGzBytes, "application/gzip"))
        server.createContext("/releases/sample.tar.gz.sha256", TextHandler(tarGzSha256))
        server.createContext("/releases/tampered.tar.gz", BytesHandler(tamperedTarGzBytes, "application/gzip"))

        server.createContext("/releases/sample.zip", BytesHandler(zipBytes, "application/zip"))
        server.createContext("/releases/sample.zip.sha256", TextHandler(zipSha256))
        server.createContext("/releases/tampered.zip", BytesHandler(tamperedZipBytes, "application/zip"))

        server.start()
    }

    override fun close() {
        server.stop(0)
    }

    private class BytesHandler(private val data: ByteArray, private val contentType: String) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            exchange.responseHeaders.set("Content-Type", contentType)
            exchange.sendResponseHeaders(200, data.size.toLong())
            exchange.responseBody.use { it.write(data) }
        }
    }

    private class TextHandler(private val text: String) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "text/plain")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
    }

    companion object {
        fun sha256(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        private fun createSampleZip(): ByteArray {
            val baos = ByteArrayOutputStream()
            ZipOutputStream(baos).use { zos ->
                val entry = ZipEntry("sample-tool")
                zos.putNextEntry(entry)
                zos.write("#!/bin/sh\necho sample-tool 1.0\n".toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }
            return baos.toByteArray()
        }

        private fun createSampleTarGz(): ByteArray {
            val baos = ByteArrayOutputStream()
            GZIPOutputStream(baos).use { gzos ->
                // Simple ustar tar format without external commons-compress dependency if needed, or simple tar structure
                val content = "#!/bin/sh\necho sample-tar 1.0\n".toByteArray(Charsets.UTF_8)
                val header = ByteArray(512)
                // Name at offset 0 (100 bytes)
                System.arraycopy("sample-tool".toByteArray(Charsets.UTF_8), 0, header, 0, 11)
                // Mode at offset 100 (8 bytes) -> 0000755
                System.arraycopy("0000755\u0000".toByteArray(Charsets.UTF_8), 0, header, 100, 8)
                // UID / GID (8 bytes each)
                System.arraycopy("0000000\u0000".toByteArray(Charsets.UTF_8), 0, header, 108, 8)
                System.arraycopy("0000000\u0000".toByteArray(Charsets.UTF_8), 0, header, 116, 8)
                // Size at offset 124 (12 bytes octal)
                val sizeOctal = String.format("%011o\u0000", content.size)
                System.arraycopy(sizeOctal.toByteArray(Charsets.UTF_8), 0, header, 124, 12)
                // Mtime at offset 136 (12 bytes octal)
                val mtimeOctal = String.format("%011o\u0000", System.currentTimeMillis() / 1000)
                System.arraycopy(mtimeOctal.toByteArray(Charsets.UTF_8), 0, header, 136, 12)
                // Checksum spaces at 148 (8 bytes)
                for (i in 148 until 156) header[i] = ' '.code.toByte()
                // Typeflag at 156 ('0' for normal file)
                header[156] = '0'.code.toByte()
                // Magic at 257 (ustar)
                System.arraycopy("ustar\u000000".toByteArray(Charsets.UTF_8), 0, header, 257, 8)
                // Calculate checksum
                var sum = 0
                for (b in header) sum += (b.toInt() and 0xFF)
                val chkOctal = String.format("%06o\u0000 ", sum)
                System.arraycopy(chkOctal.toByteArray(Charsets.UTF_8), 0, header, 148, 8)

                gzos.write(header)
                gzos.write(content)
                val padding = (512 - (content.size % 512)) % 512
                if (padding > 0) gzos.write(ByteArray(padding))
                // End of archive marker (two 512-byte blocks of zeroes)
                gzos.write(ByteArray(1024))
            }
            return baos.toByteArray()
        }
    }
}
