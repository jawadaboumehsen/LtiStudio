/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.debloat

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.toolkit.RemovalList
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class FileDebloatPresetStoreTest {
    @Test
    fun presetsAreSavedAndReadBackAndNoFileMeansNone() = runTest {
        val dir = Files.createTempDirectory("presets").toFile()
        val store = FileDebloatPresetStore(dir.resolve("debloat/presets.json"))
        assertEquals(emptyList(), store.load())

        val presets = listOf(RemovalList("Games", listOf("cn.nubia.gamespace", "product:app/Foo")))
        store.save(presets)
        assertEquals(presets, store.load())
        assertEquals(listOf("presets.json"), dir.resolve("debloat").list()!!.toList())
        dir.deleteRecursively()
    }
}
