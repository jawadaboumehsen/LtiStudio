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

/**
 * Functional category for organizing IDE commands in the Command Palette.
 */
enum class CommandCategory(val displayName: String) {
    NAVIGATION("Navigation"),
    STAGE_ACTIONS("Pipeline Stages"),
    DEVICE_OPERATIONS("Device & Target"),
    DIAGNOSTICS("Copilot & Diagnostics"),
    PREFERENCES("Settings & Preferences"),
}

/**
 * An executable studio command represented in the Command Palette.
 */
data class StudioCommand(
    val id: String,
    val title: String,
    val description: String,
    val category: CommandCategory,
    val shortcut: String? = null,
    val keywords: List<String> = emptyList(),
    val action: () -> Unit,
)

/**
 * Registry and smart search index for all available commands in LtiRom Studio.
 */
class CommandPaletteRegistry {

    private val commands = mutableMapOf<String, StudioCommand>()

    /**
     * Registers a [command] in the palette.
     */
    fun register(command: StudioCommand) {
        commands[command.id] = command
    }

    /**
     * Registers multiple [commandList] in the palette.
     */
    fun registerAll(commandList: List<StudioCommand>) {
        commandList.forEach { register(it) }
    }

    /**
     * Unregisters the command with [id].
     */
    fun unregister(id: String) {
        commands.remove(id)
    }

    /**
     * Returns all registered commands.
     */
    fun getAll(): List<StudioCommand> = commands.values.toList()

    /**
     * Clears all registered commands.
     */
    fun clear() {
        commands.clear()
    }

    /**
     * Searches commands using a prioritized relevance-ranking algorithm.
     * Evaluates exact title matches, prefix matches, substring matches, keyword matches, and descriptions.
     */
    fun search(query: String): List<StudioCommand> {
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            return commands.values.sortedWith(
                compareBy<StudioCommand> { it.category.ordinal }.thenBy { it.title },
            )
        }

        return commands.values
            .mapNotNull { cmd ->
                val score = calculateMatchScore(cmd, trimmed)
                if (score > 0) Pair(cmd, score) else null
            }
            .sortedWith(
                compareByDescending<Pair<StudioCommand, Int>> { it.second }
                    .thenBy { it.first.title },
            )
            .map { it.first }
    }

    private fun calculateMatchScore(cmd: StudioCommand, query: String): Int {
        val titleLower = cmd.title.lowercase()
        val descLower = cmd.description.lowercase()
        val catLower = cmd.category.displayName.lowercase()

        return when {
            titleLower == query -> 100
            titleLower.startsWith(query) -> 80
            titleLower.contains(query) -> 60
            cmd.keywords.any { it.lowercase() == query } -> 50
            cmd.keywords.any { it.lowercase().startsWith(query) } -> 40
            cmd.keywords.any { it.lowercase().contains(query) } -> 30
            descLower.contains(query) -> 20
            catLower.contains(query) -> 10
            else -> 0
        }
    }
}
