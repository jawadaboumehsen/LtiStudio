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

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.setup.AttemptStatus
import org.ide.lti.core.model.setup.ChildIntentRecord
import org.ide.lti.core.model.setup.PersistedExecutionRequest
import org.ide.lti.core.model.setup.SetupAttemptRecord
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The journal must survive what `java.util.prefs` cannot: values far above 8 KiB and a torn write.
 */
class FileToolchainJournalStorageTest {

    private fun tempFile(): File = Files.createTempDirectory("lti-journal").resolve("toolchain/journal.json").toFile()

    @Test
    fun readReturnsNullBeforeFirstWriteAndRoundTripsAfterwards() {
        val storage = FileToolchainJournalStorage(tempFile())
        assertNull(storage.read())
        storage.write("{\"a\":1}")
        assertEquals("{\"a\":1}", storage.read())
        assertFalse(File(storage.file.parentFile, "journal.json.tmp").exists(), "temp file must not linger")
    }

    @Test
    fun replacesDocumentAtomicallyWithPayloadLargerThanPreferenceLimit() {
        val storage = FileToolchainJournalStorage(tempFile())
        val big = "x".repeat(64 * 1024)
        storage.write("first")
        storage.write(big)
        assertEquals(big.length, storage.read()!!.length)
        storage.write("second")
        assertEquals("second", storage.read())
    }

    @Test
    fun writeFailureLeavesPreviousDocumentIntact() {
        val storage = FileToolchainJournalStorage(tempFile())
        storage.write("kept")
        // Make the parent unusable for the temp file by turning it into a plain file's path.
        val blocked = FileToolchainJournalStorage(File(storage.file, "child.json"))
        assertFailsWith<java.io.IOException> { blocked.write("never") }
        assertEquals("kept", storage.read())
    }

    @Test
    fun dataSourceMigratesLegacyPreferenceKeyAndThenWritesOnlyTheDurableDocument() = runTest {
        val settings = MapSettings()
        settings.putString(
            ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE,
            "{\"isSetupCompleted\":true,\"completedStages\":[\"WSL_DETECTION\"]}",
        )
        val storage = FileToolchainJournalStorage(tempFile())
        val dataSource = ToolchainPreferencesDataSource(
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
            storage = storage,
        )
        assertTrue(dataSource.currentToolchainState.isSetupCompleted, "legacy key is read when no document exists")
        assertNull(dataSource.currentToolchainState.activeAttempt, "legacy record decodes to no active attempt")

        val record = SetupAttemptRecord("a1", "env", "p1", "h1", "FULL_SETUP", AttemptStatus.AUTHORIZED)
        assertTrue(dataSource.recordAttemptAuthorized(record).isSuccess)
        val intent = ChildIntentRecord(
            childIndex = -1,
            stage = "REPO_SYNCHRONIZATION",
            actionId = "submodule:x",
            request = PersistedExecutionRequest(toolId = "git"),
            idempotencyKey = "k",
            workspaceLock = "l",
        )
        assertTrue(dataSource.appendChildIntent("a1", intent).isSuccess)
        repeat(400) { seq ->
            assertTrue(
                dataSource.commitChildCursorAndEvidence("a1", 0, seq + 1L, "line $seq " + "y".repeat(64)).isSuccess,
                "commit $seq must be acknowledged even once the document exceeds 8 KiB",
            )
        }
        val document = storage.read()!!
        assertTrue(document.length > 8 * 1024, "journal grew past the preference limit: ${document.length}")

        val reopened = ToolchainPreferencesDataSource(
            settings = settings,
            ioDispatcher = Dispatchers.Unconfined,
            storage = storage,
        )
        val reloaded = reopened.currentToolchainState.activeAttempt!!
        assertEquals(400L, reloaded.orderedIntents.single().lastSeq)
        assertEquals(400, reloaded.orderedIntents.single().replayLogs.size)
    }
}
