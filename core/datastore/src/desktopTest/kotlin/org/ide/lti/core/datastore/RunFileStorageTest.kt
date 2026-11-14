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

import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class RunFileStorageTest {
    @Test
    fun aMultiLineEventIsReadBackAsOneEvent() = runBlocking {
        val dir = Files.createTempDirectory("run-storage-").toFile()
        try {
            val storage = DesktopRunFileStorage(dir)
            storage.appendEvents("r1", listOf("one\ntwo", "", "three"))
            // Memory keeps these three events: the file must give the same three, so sequence numbers agree.
            assertEquals(listOf("one\ntwo", "", "three"), storage.getEvents("r1", 0))
            assertEquals(listOf("three"), storage.getEvents("r1", 3))
            // A log written before stays readable, a line per event.
            dir.resolve("r2.events.jsonl").writeText("old line\nanother\n")
            assertEquals(listOf("old line", "another"), storage.getEvents("r2", 0))
        } finally {
            dir.deleteRecursively()
        }
    }
}
