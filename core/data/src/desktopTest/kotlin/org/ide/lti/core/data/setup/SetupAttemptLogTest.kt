/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.SetupLogEvent
import org.ide.lti.core.domain.setup.SetupLogKind
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SetupAttemptLogTest {

    @Test
    fun `file header records attempt cap and rotation`() {
        val tempDir = File.createTempFile("setup_log_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val maxBytes = 1024L
            val buffer = SetupLogBuffer(
                logDirectory = tempDir,
                maxLogFileSizeBytes = maxBytes,
            )

            buffer.append(
                SetupLogEvent(
                    attemptId = "attempt-123",
                    childRunId = "run-1",
                    sequence = 1L,
                    text = "Initial log line",
                    kind = SetupLogKind.CHECK,
                ),
            )

            val logFile = buffer.getLogFile("attempt-123")
            assertNotNull(logFile, "Log file must exist for attempt-123")
            val content = logFile.readText()
            assertTrue(
                content.contains("# Setup Attempt Log: attempt-123"),
                "Header must identify attempt",
            )
            assertTrue(
                content.contains("# Max Size: $maxBytes bytes | Rotation: 1 backup"),
                "Header must record cap and rotation",
            )
            assertTrue(
                content.contains("[CHECK] Initial log line"),
                "Line must record kind and text",
            )
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `checks outside an attempt are written to local log`() {
        val tempDir = File.createTempFile("setup_log_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val buffer = SetupLogBuffer(logDirectory = tempDir)
            buffer.append(
                SetupLogEvent(
                    attemptId = "local",
                    childRunId = "local",
                    sequence = 1L,
                    text = "[check] WSL 2 Runtime: started",
                    kind = SetupLogKind.CHECK,
                ),
            )

            val localLog = buffer.getLogFile("local")
            assertNotNull(localLog, "local.log must exist for non-attempt checks")
            assertEquals("local.log", localLog.name)
            val lines = localLog.readLines()
            assertTrue(lines.any { it.contains("[CHECK] [check] WSL 2 Runtime: started") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `file rotates when size cap is exceeded`() {
        val tempDir = File.createTempFile("setup_log_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val maxBytes = 250L
            val buffer = SetupLogBuffer(
                logDirectory = tempDir,
                maxLogFileSizeBytes = maxBytes,
            )

            for (i in 1..20) {
                buffer.append(
                    SetupLogEvent(
                        attemptId = "attempt-rotate",
                        childRunId = "run-1",
                        sequence = i.toLong(),
                        text = "This is a moderately long log line number $i to trigger rotation",
                        kind = if (i % 2 == 0) SetupLogKind.RUN else SetupLogKind.ERROR,
                    ),
                )
            }

            val currentLog = File(tempDir, "attempt-rotate.log")
            val rotatedLog = File(tempDir, "attempt-rotate.log.1")

            assertTrue(currentLog.exists(), "Current log must exist")
            assertTrue(rotatedLog.exists(), "Rotated log backup (.1) must exist")
            assertTrue(currentLog.readText().contains("# Setup Attempt Log: attempt-rotate"))
            assertTrue(rotatedLog.readText().contains("# Setup Attempt Log: attempt-rotate"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `in-memory buffer stays bounded while log file persists events`() {
        val tempDir = File.createTempFile("setup_log_test", "").apply {
            delete()
            mkdirs()
        }
        try {
            val buffer = SetupLogBuffer(
                maxLines = 5,
                logDirectory = tempDir,
            )

            for (i in 1..20) {
                buffer.append(
                    SetupLogEvent(
                        attemptId = "attempt-bounds",
                        childRunId = "run-1",
                        sequence = i.toLong(),
                        text = "Line $i",
                    ),
                )
            }

            assertEquals(5, buffer.size, "In-memory buffer must stay bounded to maxLines=5")
            assertEquals(15L, buffer.droppedLineCount, "Dropped line count must reflect in-memory eviction")

            val logFile = File(tempDir, "attempt-bounds.log")
            assertTrue(logFile.exists())
            val fileLines = logFile.readLines().filter { !it.startsWith("#") }
            assertEquals(20, fileLines.size, "Persisted log file must retain all 20 lines")
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
