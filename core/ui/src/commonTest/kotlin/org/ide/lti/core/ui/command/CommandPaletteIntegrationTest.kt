/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.ui.command

import org.ide.lti.core.domain.command.CommandCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CommandPaletteIntegrationTest {

    @Test
    fun defaultStudioCommands_containsAllCoreCategories() {
        var settingsOpened = false
        var terminalToggled = false
        var copilotOpened = false
        var superBudgeterOpened = false

        val registry = DefaultStudioCommands.createDefaultRegistry(
            onNavigateSettings = { settingsOpened = true },
            onToggleTerminal = { terminalToggled = true },
            onOpenCopilot = { copilotOpened = true },
            onOpenSuperBudgeter = { superBudgeterOpened = true },
        )

        val allCommands = registry.getAll()
        assertFalse(allCommands.isEmpty())

        // Check that all categories are represented
        val categories = allCommands.map { it.category }.toSet()
        assertTrue(categories.contains(CommandCategory.PREFERENCES))
        assertTrue(categories.contains(CommandCategory.NAVIGATION))
        assertTrue(categories.contains(CommandCategory.DIAGNOSTICS))
        assertTrue(categories.contains(CommandCategory.STAGE_ACTIONS))
        assertTrue(categories.contains(CommandCategory.DEVICE_OPERATIONS))

        // Check command actions trigger correctly
        val settingsCmd = allCommands.first { it.id == "nav.settings" }
        settingsCmd.action()
        assertTrue(settingsOpened)

        val copilotCmd = allCommands.first { it.id == "diag.copilot" }
        copilotCmd.action()
        assertTrue(copilotOpened)

        val superCmd = allCommands.first { it.id == "diag.super.budget" }
        superCmd.action()
        assertTrue(superBudgeterOpened)
    }

    @Test
    fun defaultStudioCommands_searchFindsIntentKeywords() {
        val registry = DefaultStudioCommands.createDefaultRegistry()

        // Search by keyword "bootloop" -> should return copilot
        val bootloopResults = registry.search("bootloop")
        assertTrue(bootloopResults.isNotEmpty())
        assertEquals("diag.copilot", bootloopResults.first().id)

        // Search by keyword "kernelsu" -> should return root patch
        val rootResults = registry.search("kernelsu")
        assertTrue(rootResults.isNotEmpty())
        assertEquals("stage.patch.root", rootResults.first().id)

        // Search by keyword "fastboot" -> should return device operations
        val fastbootResults = registry.search("fastboot")
        assertTrue(fastbootResults.isNotEmpty())
        assertTrue(fastbootResults.any { it.category == CommandCategory.DEVICE_OPERATIONS })

        // Search by keyword "erofs" -> should return partition explorer
        val erofsResults = registry.search("erofs")
        assertTrue(erofsResults.isNotEmpty())
        assertTrue(erofsResults.any { it.id == "diag.partition.explorer" })

        // Search by keyword "build.prop" -> should return config diff
        val diffResults = registry.search("build.prop")
        assertTrue(diffResults.isNotEmpty())
        assertTrue(diffResults.any { it.id == "diag.config.diff" })
    }
}
