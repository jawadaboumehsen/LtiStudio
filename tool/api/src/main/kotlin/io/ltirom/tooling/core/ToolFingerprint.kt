package io.ltirom.tooling.core

import java.io.File
import java.security.MessageDigest

public data class ToolFingerprint(
    val sha256: String,
    val helpHash: String,
    val platform: PlatformIdentity
) {
    public companion object {
        public fun computeFor(file: File): ToolFingerprint {
            require(file.isFile) { "Cannot compute fingerprint for non-existent file: ${file.absolutePath}" }
            
            val sha256 = computeSha256(file)
            val platform = PlatformIdentity.CURRENT
            
            // We can resolve helpHash by matching file properties
            return ToolFingerprint(
                sha256 = sha256,
                helpHash = "", // Optional/deferred to verification commands
                platform = platform
            )
        }

        private fun computeSha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var read = input.read(buffer)
                while (read != -1) {
                    digest.update(buffer, 0, read)
                    read = input.read(buffer)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }
}
