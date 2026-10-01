/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.cli

/**
 * Provides command, subcommand, and option completions for in-app terminal input.
 */
interface CommandCompleter {
    /**
     * Given an input string, returns a list of suggested completions.
     */
    fun complete(input: String): List<String>
}

class DefaultCommandCompleter(
    private val extraCommands: Set<String> = setOf("cd"),
) : CommandCompleter {

    private val commandTree: Map<String, CommandNode> = buildCommandTree()

    data class CommandNode(
        val name: String,
        val subcommands: Map<String, CommandNode> = emptyMap(),
        val options: List<String> = emptyList(),
        val optionValues: Map<String, List<String>> = emptyMap(),
    )

    private fun buildCommandTree(): Map<String, CommandNode> {
        val themeNode = CommandNode(
            name = "theme",
            subcommands = mapOf(
                "toggle" to CommandNode(name = "toggle"),
                "list" to CommandNode(name = "list"),
                "set" to CommandNode(
                    name = "set",
                    options = listOf("--name", "-n"),
                    optionValues = mapOf(
                        "--name" to listOf("dark", "blue", "light"),
                        "-n" to listOf("dark", "blue", "light"),
                    ),
                ),
            ),
        )

        val settingsNode = CommandNode(
            name = "settings",
            subcommands = mapOf(
                "list" to CommandNode(name = "list"),
                "set" to CommandNode(
                    name = "set",
                    options = listOf("--key", "-k", "--value", "-v"),
                    optionValues = mapOf(
                        "--key" to listOf("theme", "effectsEnabled", "reducedMotion"),
                        "-k" to listOf("theme", "effectsEnabled", "reducedMotion"),
                    ),
                ),
            ),
        )

        val workspaceNode = CommandNode(
            name = "workspace",
            subcommands = mapOf(
                "info" to CommandNode(name = "info"),
                "list" to CommandNode(name = "list"),
            ),
        )

        val systemNode = CommandNode(
            name = "system",
            subcommands = mapOf(
                "memory" to CommandNode(name = "memory"),
                "info" to CommandNode(name = "info"),
            ),
        )

        val echoNode = CommandNode(
            name = "echo",
            options = listOf("-n", "-e", "--err"),
        )

        val versionNode = CommandNode(
            name = "version",
            options = listOf("-s", "--short"),
        )

        val clearNode = CommandNode(name = "clear")

        val helpNode = CommandNode(
            name = "help",
            subcommands = mapOf(
                "theme" to themeNode,
                "settings" to settingsNode,
                "workspace" to workspaceNode,
                "system" to systemNode,
                "echo" to echoNode,
                "version" to versionNode,
                "clear" to clearNode,
            ),
        )

        val rootCommands = mutableMapOf(
            "help" to helpNode,
            "clear" to clearNode,
            "echo" to echoNode,
            "version" to versionNode,
            "theme" to themeNode,
            "settings" to settingsNode,
            "workspace" to workspaceNode,
            "system" to systemNode,
            "cd" to CommandNode(name = "cd"),
        )

        for (cmd in extraCommands) {
            if (!rootCommands.containsKey(cmd)) {
                rootCommands[cmd] = CommandNode(name = cmd)
            }
        }

        // "lti" root wrapper has all child subcommands
        rootCommands["lti"] = CommandNode(
            name = "lti",
            subcommands = rootCommands.filterKeys { it != "cd" && it != "lti" },
        )

        return rootCommands
    }

    override fun complete(input: String): List<String> {
        val trimmedInput = input.trimStart()
        val endsWithSpace = input.endsWith(" ")

        val tokens = if (trimmedInput.isEmpty()) {
            emptyList()
        } else {
            tokenizeCommandLine(trimmedInput)
        }

        if (tokens.isEmpty()) {
            return commandTree.keys.sorted()
        }

        if (tokens.size == 1 && !endsWithSpace) {
            val prefix = tokens[0]
            return commandTree.keys.filter { it.startsWith(prefix, ignoreCase = true) }.sorted()
        }

        var currentNode: CommandNode? = commandTree[tokens[0]] ?: return emptyList()
        var tokenIndex = 1

        while (tokenIndex < tokens.size && currentNode != null) {
            val currentToken = tokens[tokenIndex]
            val isLastToken = tokenIndex == tokens.size - 1

            if (isLastToken && !endsWithSpace) {
                val prevToken = tokens[tokenIndex - 1]
                if (currentNode.optionValues.containsKey(prevToken)) {
                    return currentNode.optionValues[prevToken]
                        ?.filter { it.startsWith(currentToken, ignoreCase = true) }
                        ?.sorted() ?: emptyList()
                }

                val candidates = mutableListOf<String>()
                candidates.addAll(currentNode.subcommands.keys.filter { it.startsWith(currentToken, ignoreCase = true) })
                candidates.addAll(currentNode.options.filter { it.startsWith(currentToken, ignoreCase = true) })
                return candidates.sorted()
            }

            val prevToken = tokens[tokenIndex - 1]
            if (currentNode.optionValues.containsKey(prevToken)) {
                // Value was provided for option, advance
            }

            val nextSub = currentNode.subcommands[currentToken]
            if (nextSub != null) {
                currentNode = nextSub
            }
            tokenIndex++
        }

        if (currentNode == null) return emptyList()

        val lastToken = tokens.last()
        if (endsWithSpace && currentNode.optionValues.containsKey(lastToken)) {
            return currentNode.optionValues[lastToken]?.sorted() ?: emptyList()
        }

        val completions = mutableListOf<String>()
        completions.addAll(currentNode.subcommands.keys)
        completions.addAll(currentNode.options)
        return completions.sorted()
    }
}
