/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.utils

import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Atomically writes UTF-8 [text] to [target] via a temporary sibling file so crashes or disk errors
 * never leave a torn record.
 */
public fun atomicWrite(target: Path, text: String, fsync: Boolean = false, allowNonAtomicFallback: Boolean = true) {
    atomicWrite(
        target = target,
        fsync = fsync,
        allowNonAtomicFallback = allowNonAtomicFallback,
    ) { out ->
        out.write(text.toByteArray(Charsets.UTF_8))
    }
}

/**
 * Atomically writes bytes via [writer] to a temporary sibling of [target], optionally syncing the
 * file descriptor before replacing [target].
 */
public fun atomicWrite(
    target: Path,
    fsync: Boolean = false,
    allowNonAtomicFallback: Boolean = true,
    writer: (OutputStream) -> Unit,
) {
    val parent = target.toAbsolutePath().parent
    if (parent != null && !Files.isDirectory(parent)) {
        Files.createDirectories(parent)
    }
    val dir = parent ?: Path.of(".")
    val tempFile = Files.createTempFile(dir, "${target.fileName}.", ".tmp")
    try {
        FileOutputStream(tempFile.toFile()).use { out ->
            writer(out)
            out.flush()
            if (fsync) {
                out.fd.sync()
            }
        }
        atomicMove(source = tempFile, destination = target, allowNonAtomicFallback = allowNonAtomicFallback)
    } finally {
        Files.deleteIfExists(tempFile)
    }
}

/**
 * Moves [source] over [destination] using `ATOMIC_MOVE` + `REPLACE_EXISTING`. When the filesystem
 * does not support atomic moves, falls back to standard replace if [allowNonAtomicFallback] is true,
 * otherwise throws [IOException].
 */
public fun atomicMove(source: Path, destination: Path, allowNonAtomicFallback: Boolean = true) {
    try {
        Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (e: AtomicMoveNotSupportedException) {
        if (!allowNonAtomicFallback) {
            throw IOException("Atomic replace is not supported at $destination", e)
        }
        Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
    }
}

/**
 * Returns the UTF-8 text of [file] when it exists and is non-blank, otherwise null.
 */
public fun readIfPresent(file: Path?): String? =
    file?.takeIf(Files::exists)?.let(Files::readString)?.takeIf(String::isNotBlank)
