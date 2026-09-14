package org.ide.lti.core.data.admission

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/** Write-to-temp then atomic replace, so a crash mid-write never leaves a half-written lease file. */
internal fun atomicWrite(target: Path, text: String) {
    val tempFile = Files.createTempFile(target.parent, target.fileName.toString(), ".tmp")
    try {
        Files.writeString(tempFile, text)
        try {
            Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

/** Contents of [file] when it exists and is non-blank, else null. */
internal fun readIfPresent(file: Path?): String? =
    file?.takeIf(Files::exists)?.let(Files::readString)?.takeIf(String::isNotBlank)
