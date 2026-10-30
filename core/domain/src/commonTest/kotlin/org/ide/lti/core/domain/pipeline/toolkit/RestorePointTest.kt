/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RestorePointTest {

    @Test
    fun testValidation() {
        assertFailsWith<IllegalArgumentException> {
            RestorePoint(
                id = "",
                tool = "debloat",
                itemPath = "system/app/YouTube",
                createdAt = "2026-10-25T10:00:00Z",
                backupPath = "restore/backups/YouTube.bak",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RestorePoint(
                id = "rp-1",
                tool = "   ",
                itemPath = "system/app/YouTube",
                createdAt = "2026-10-25T10:00:00Z",
                backupPath = "restore/backups/YouTube.bak",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RestorePoint(
                id = "rp-1",
                tool = "debloat",
                itemPath = "",
                createdAt = "2026-10-25T10:00:00Z",
                backupPath = "restore/backups/YouTube.bak",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RestorePoint(
                id = "rp-1",
                tool = "debloat",
                itemPath = "system/app/YouTube",
                createdAt = "",
                backupPath = "restore/backups/YouTube.bak",
            )
        }
        assertFailsWith<IllegalArgumentException> {
            RestorePoint(
                id = "rp-1",
                tool = "debloat",
                itemPath = "system/app/YouTube",
                createdAt = "2026-10-25T10:00:00Z",
                backupPath = "",
            )
        }
    }

    @Test
    fun testStateTransitionsAndIndex() {
        val rp1 = RestorePoint(
            id = "rp-1",
            tool = "debloat",
            itemPath = "system/app/YouTube",
            createdAt = "2026-10-25T10:00:00Z",
            backupPath = "restore/backups/YouTube.bak",
            state = RestorePointState.Available,
        )
        val rp2 = RestorePoint(
            id = "rp-2",
            tool = "debloat",
            itemPath = "system/priv-app/Velvet",
            createdAt = "2026-10-25T10:05:00Z",
            backupPath = "restore/backups/Velvet.bak",
            state = RestorePointState.Available,
        )

        var index = RestoreIndex(listOf(rp1, rp2))
        assertEquals(2, index.items.size)
        assertEquals(RestorePointState.Available, index.findById("rp-1")?.state)

        // Transition rp-1 to Restored
        index = index.withState("rp-1", RestorePointState.Restored)
        assertEquals(RestorePointState.Restored, index.findById("rp-1")?.state)

        // Transition rp-2 to BackupMissing
        index = index.withState("rp-2", RestorePointState.BackupMissing)
        assertEquals(RestorePointState.BackupMissing, index.findById("rp-2")?.state)

        // Non-existent ID returns null and leaves index unchanged
        assertNull(index.findById("unknown"))
        val untouched = index.withState("unknown", RestorePointState.Restored)
        assertEquals(index, untouched)
    }

    @Test
    fun testJsonRoundTrip() {
        val original = RestoreIndex(
            items = listOf(
                RestorePoint(
                    id = "rp-1",
                    tool = "debloat",
                    itemPath = "system/app/YouTube",
                    createdAt = "2026-10-25T10:00:00Z",
                    backupPath = "restore/backups/YouTube.bak",
                    state = RestorePointState.Available,
                ),
                RestorePoint(
                    id = "rp-2",
                    tool = "debloat",
                    itemPath = "system/priv-app/Velvet",
                    createdAt = "2026-10-25T10:05:00Z",
                    backupPath = "restore/backups/Velvet.bak",
                    state = RestorePointState.Restored,
                ),
            ),
        )

        val json = original.toJson()
        val decoded = RestoreIndex.fromJson(json)
        assertEquals(original, decoded)
    }
}
