/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.command

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandPaletteRegistryTest {

    private val registry = CommandPaletteRegistry()

    @Test
    fun searchReturnsAllCommandsGroupedWhenQueryIsEmpty() {
        var actionExecuted = false
        registry.register(
            StudioCommand(
                id = "cmd.extract",
                title = "Extract Payload.bin",
                description = "Extract partitions from payload.bin",
                category = CommandCategory.STAGE_ACTIONS,
                action = { actionExecuted = true },
            ),
        )
        registry.register(
            StudioCommand(
                id = "cmd.settings",
                title = "Open Settings",
                description = "Adjust studio preferences",
                category = CommandCategory.PREFERENCES,
                action = {},
            ),
        )

        val results = registry.search("")
        assertEquals(2, results.size)
        // Verify action executes
        results.first { it.id == "cmd.extract" }.action()
        assertTrue(actionExecuted)
    }

    @Test
    fun searchRanksExactAndPrefixMatchesHigher() {
        registry.register(
            StudioCommand(
                id = "cmd.flash.fastboot",
                title = "Flash Fastboot Partitions",
                description = "Flash images using fastboot",
                category = CommandCategory.DEVICE_OPERATIONS,
                action = {},
            ),
        )
        registry.register(
            StudioCommand(
                id = "cmd.copilot.flash",
                title = "Analyze Flash Crash",
                description = "Flash crash logs investigation",
                category = CommandCategory.DIAGNOSTICS,
                action = {},
            ),
        )
        registry.register(
            StudioCommand(
                id = "cmd.flash",
                title = "Flash",
                description = "Flash device",
                category = CommandCategory.DEVICE_OPERATIONS,
                action = {},
            ),
        )

        val results = registry.search("flash")
        assertEquals(3, results.size)
        // Exact match "Flash" must rank first
        assertEquals("cmd.flash", results[0].id)
        // Prefix match "Flash Fastboot Partitions" must rank second
        assertEquals("cmd.flash.fastboot", results[1].id)
        // Contains match "Analyze Flash Crash" ranks third
        assertEquals("cmd.copilot.flash", results[2].id)
    }

    @Test
    fun searchMatchesKeywords() {
        registry.register(
            StudioCommand(
                id = "cmd.kernelsu",
                title = "Inject Root",
                description = "Patch boot image",
                category = CommandCategory.STAGE_ACTIONS,
                keywords = listOf("kernelsu", "magisk", "root"),
                action = {},
            ),
        )

        val results = registry.search("kernelsu")
        assertEquals(1, results.size)
        assertEquals("cmd.kernelsu", results.first().id)
    }
}
