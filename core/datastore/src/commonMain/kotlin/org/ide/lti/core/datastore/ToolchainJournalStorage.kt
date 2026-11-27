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

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

/**
 * Durable document store for the toolchain evidence + attempt journal.
 *
 * The journal grows with every replayed output line, so it cannot live in the platform preference
 * store (`java.util.prefs` caps a value at 8 KiB and flushes lazily). [write] must be atomic and
 * durable: after it returns normally the previous document is fully replaced on stable storage; on
 * any failure it throws and the previous document is untouched. "Journal write failure prevents
 * submission" (FR-007) relies on that contract.
 */
interface ToolchainJournalStorage {
    /** The last durably written document, or null when nothing was ever written. */
    fun read(): String?

    /** Atomically replaces the document; throws when durability cannot be guaranteed. */
    fun write(json: String)
}

/**
 * Preference-backed store used by tests (in-memory `MapSettings`) and as the legacy location that
 * pre-journal releases wrote to. Not durable for large journals on JVM; production wiring uses
 * [defaultToolchainJournalStorage].
 */
class SettingsToolchainJournalStorage(
    private val settings: Settings,
    private val key: String = ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE,
) : ToolchainJournalStorage {
    override fun read(): String? = settings.getStringOrNull(key)

    override fun write(json: String) {
        settings[key] = json
    }
}

/** Platform durable store: an atomically replaced, fsync'd file under the application data directory. */
expect fun defaultToolchainJournalStorage(): ToolchainJournalStorage
