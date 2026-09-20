package io.ltirom.tooling.core

import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile

public data class SignApkInvocation(
    val publicKey: File,
    val privateKey: File,
    val inputArchive: File,
    val outputArchive: File
)

public object SignedArchiveValidator {
    public fun validateInvocation(invocation: SignApkInvocation) {
        requireRegular(invocation.publicKey, "public key")
        requireRegular(invocation.privateKey, "private key")
        requireRegular(invocation.inputArchive, "input archive")
        requireDistinct(invocation.inputArchive, invocation.outputArchive)
    }

    /**
     * Reads every signed entry so JarFile performs certificate verification.
     * This rejects unsigned archives and archives whose signer chain cannot be
     * validated by the current JVM trust implementation.
     */
    public fun verifySignedArchive(archive: File) {
        requireRegular(archive, "signed archive")
        JarFile(archive, true).use { jar ->
            var signedEntries = 0
            val entries = jar.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || entry.name.startsWith("META-INF/")) continue
                jar.getInputStream(entry).use { input -> input.copyTo(java.io.OutputStream.nullOutputStream()) }
                require(!entry.certificates.isNullOrEmpty()) { "Archive entry is unsigned: ${entry.name}" }
                signedEntries++
            }
            require(signedEntries > 0) { "Archive contains no verifiable signed entries: $archive" }
        }
    }

    private fun requireRegular(file: File, label: String) {
        require(file.isFile) { "$label is not a regular file: $file" }
    }

    private fun requireDistinct(input: File, output: File) {
        val inputPath: Path = input.toPath().toAbsolutePath().normalize()
        val outputPath: Path = output.toPath().toAbsolutePath().normalize()
        require(inputPath != outputPath) { "SignApk input and output archives must be different paths" }
    }
}
