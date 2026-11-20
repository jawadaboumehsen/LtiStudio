/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.patch

import java.nio.channels.FileChannel
import java.nio.file.AccessDeniedException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import kotlin.concurrent.thread
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** On Windows a file held open cannot be replaced (the refusal a build met on plat_sepolicy.cil). */
class MoveReplacingTest {
    private val dir = Files.createTempDirectory("move-replacing")
    private val target = dir.resolve("plat_sepolicy.cil").apply { writeText("old") }
    private val source = dir.resolve("text-patch-1.tmp").apply { writeText("new") }

    @Test
    fun aReplaceRefusedForAMomentIsTriedAgain() {
        val holder = FileChannel.open(target, StandardOpenOption.READ)
        val release = thread {
            Thread.sleep(HOLD_MS)
            holder.close()
        }
        moveReplacing(source, target)
        release.join()
        assertEquals("new", target.readText())
    }

    @Test
    fun aReplaceRefusedEveryTimeSaysWhy() {
        if (!System.getProperty("os.name").startsWith("Windows")) return
        FileChannel.open(target, StandardOpenOption.READ).use {
            val e = assertFailsWith<AccessDeniedException> { moveReplacing(source, target, attempts = 2) }
            assertTrue("refused to replace it 2 times" in e.message.orEmpty(), e.message)
        }
        assertEquals("old", target.readText())
    }

    private companion object {
        const val HOLD_MS = 300L
    }
}
