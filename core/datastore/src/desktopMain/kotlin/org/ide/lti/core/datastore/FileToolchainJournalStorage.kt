/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Desktop JVM [ToolchainJournalStorage]: writes to a sibling temp file, fsyncs it, then atomically
 * moves it over the journal. A crash at any point leaves either the previous complete journal or the
 * new complete journal, never a torn one. Every failure throws so the caller never acknowledges an
 * unpersisted write.
 */
public class FileToolchainJournalStorage(
    public val file: File,
) : ToolchainJournalStorage {

    override fun read(): String? {
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8)
    }

    override fun write(json: String) {
        val parent = file.absoluteFile.parentFile
        if (parent != null && !parent.isDirectory) {
            parent.mkdirs()
            if (!parent.isDirectory) throw java.io.IOException("Cannot create journal directory ${parent.path}")
        }
        val temp = File(parent, "${file.name}.tmp")
        FileOutputStream(temp).use { out ->
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            // Same directory on every supported filesystem; keep the failure explicit rather than
            // silently degrading to a non-atomic copy.
            temp.delete()
            throw java.io.IOException("Atomic journal replace is not supported at ${file.path}", e)
        }
    }

    public companion object {
        public fun resolveDefaultFile(): File =
            File(DesktopRunFileStorage.resolveDefaultBaseDir().parentFile, "toolchain/journal.json")
    }
}

public actual fun defaultToolchainJournalStorage(): ToolchainJournalStorage =
    FileToolchainJournalStorage(FileToolchainJournalStorage.resolveDefaultFile())
