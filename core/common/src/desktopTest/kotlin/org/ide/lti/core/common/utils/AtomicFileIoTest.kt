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

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AtomicFileIoTest {
    @Test
    fun `atomicWrite creates parent directories and overwrites safely`() {
        val root = Files.createTempDirectory("atomic-io-test")
        try {
            val target = root.resolve("nested/dir/data.json")
            atomicWrite(target, """{"v":1}""")
            assertEquals("""{"v":1}""", readIfPresent(target))

            atomicWrite(target, """{"v":2}""", fsync = true)
            assertEquals("""{"v":2}""", readIfPresent(target))

            assertEquals(1L, Files.list(target.parent).use { it.count() })
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun `readIfPresent returns null for missing or blank files`() {
        val root = Files.createTempDirectory("atomic-io-read")
        try {
            assertNull(readIfPresent(null))
            assertNull(readIfPresent(root.resolve("missing.txt")))
            val blank = root.resolve("blank.txt")
            atomicWrite(blank, "   \n  ")
            assertNull(readIfPresent(blank))
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
