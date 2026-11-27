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

import org.ide.lti.core.common.utils.atomicWrite
import java.io.File

/**
 * Desktop JVM [ToolchainJournalStorage]: writes to a sibling temp file, fsyncs it, then atomically
 * moves it over the journal. A crash at any point leaves either the previous complete journal or the
 * new complete journal, never a torn one. Every failure throws so the caller never acknowledges an
 * unpersisted write.
 */
class FileToolchainJournalStorage(
    val file: File,
) : ToolchainJournalStorage {

    override fun read(): String? {
        if (!file.isFile) return null
        return file.readText(Charsets.UTF_8)
    }

    override fun write(json: String) {
        atomicWrite(file.toPath(), json, fsync = true, allowNonAtomicFallback = false)
    }

    companion object {
        fun resolveDefaultFile(): File =
            File(DesktopRunFileStorage.resolveDefaultBaseDir().parentFile, "toolchain/journal.json")
    }
}

actual fun defaultToolchainJournalStorage(): ToolchainJournalStorage =
    FileToolchainJournalStorage(FileToolchainJournalStorage.resolveDefaultFile())
