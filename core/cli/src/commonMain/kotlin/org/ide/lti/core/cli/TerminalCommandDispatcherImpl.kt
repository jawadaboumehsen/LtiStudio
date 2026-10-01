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

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.parse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import org.ide.lti.core.domain.terminal.ShellExecutor
import org.ide.lti.core.domain.terminal.ShellOutputEvent
import org.ide.lti.core.domain.terminal.TerminalCommandDispatcher
import org.ide.lti.core.model.terminal.TerminalOutputEvent
import java.io.File

class TerminalCommandDispatcherImpl(
    initialCwd: String,
    private val shellExecutor: ShellExecutor,
    private val onToggleTheme: () -> Unit = {},
    private val onSetTheme: (String) -> Boolean = { false },
    private val getSettings: () -> Map<String, Any> = { emptyMap() },
    private val onUpdateSetting: (String, String) -> Boolean = { _, _ -> false },
) : TerminalCommandDispatcher {

    private val _cwd = MutableStateFlow(resolveInitialCwd(initialCwd))
    override val cwd: StateFlow<String> = _cwd.asStateFlow()

    private val builtInCommands: Set<String> by lazy {
        val dummyConsole = TerminalConsole()
        val dummyRoot = LtiRootCommand(console = dummyConsole)
        (dummyRoot.registeredSubcommands().map { it.commandName } + "lti").toSet()
    }

    override fun dispatch(line: String): Flow<TerminalOutputEvent> {
        val trimmed = line.trim()
        val tokens = if (trimmed.isEmpty()) emptyList() else tokenizeCommandLine(trimmed)
        if (tokens.isEmpty()) {
            return emptyFlow()
        }

        val firstToken = tokens.first()
        return when {
            firstToken == "cd" -> handleCd(tokens)
            firstToken in builtInCommands -> handleBuiltIn(tokens)
            else -> handleShell(line)
        }
    }

    private fun handleCd(tokens: List<String>): Flow<TerminalOutputEvent> = flow {
        val rawTarget = if (tokens.size > 1) tokens[1] else ""
        val target = resolveCdTarget(tokens, rawTarget)

        val resolved = try {
            val targetFile = File(target)
            val file = if (targetFile.isAbsolute) targetFile else File(_cwd.value, target)
            file.canonicalFile
        } catch (_: Exception) {
            null
        }

        if (resolved != null && resolved.exists() && resolved.isDirectory) {
            _cwd.value = resolved.absolutePath
        } else {
            emit(TerminalOutputEvent.Error("cd: no such file or directory: $rawTarget"))
        }
    }

    private fun resolveCdTarget(tokens: List<String>, rawTarget: String): String {
        val home = System.getProperty("user.home") ?: ""
        return when {
            tokens.size <= 1 -> home
            rawTarget == "~" -> home
            rawTarget.startsWith("~/") || rawTarget.startsWith("~\\") -> home + rawTarget.substring(1)
            else -> rawTarget
        }
    }

    private fun handleBuiltIn(tokens: List<String>): Flow<TerminalOutputEvent> = flow {
        val console = TerminalConsole()
        var clearRequested = false

        val commandContext = LtiCommandContext(
            console = console,
            getCwd = { _cwd.value },
            setCwd = { newCwd -> _cwd.value = newCwd },
            onToggleTheme = onToggleTheme,
            onSetTheme = onSetTheme,
            onClear = { clearRequested = true },
            getSettings = getSettings,
            onUpdateSetting = onUpdateSetting,
        )

        val root = LtiRootCommand(commandContext = commandContext)

        val parseTokens = if (tokens.first() == "lti") tokens.drop(1) else tokens
        val caughtError = parseCommand(root, parseTokens)

        if (clearRequested) {
            emit(TerminalOutputEvent.Clear)
        } else {
            emitConsoleOutput(console, caughtError)
        }
    }

    private fun parseCommand(root: LtiRootCommand, parseTokens: List<String>): CliktError? {
        return try {
            root.parse(parseTokens)
            null
        } catch (e: CliktError) {
            root.echoFormattedHelp(e)
            e
        }
    }

    private suspend fun FlowCollector<TerminalOutputEvent>.emitConsoleOutput(
        console: TerminalConsole,
        caughtError: CliktError?,
    ) {
        emitLines(console.stdout(), isError = false)
        emitLines(console.stderr(), isError = true)
        if (console.output().isEmpty() && caughtError != null) {
            val fallbackMsg = caughtError.message ?: "Command failed"
            emit(TerminalOutputEvent.Error(fallbackMsg))
        }
    }

    private suspend fun FlowCollector<TerminalOutputEvent>.emitLines(
        text: String,
        isError: Boolean,
    ) {
        if (text.isNotEmpty()) {
            text.trimEnd('\r', '\n').lines().forEach { line ->
                val event = if (isError) {
                    TerminalOutputEvent.Error(line)
                } else {
                    TerminalOutputEvent.Output(line)
                }
                emit(event)
            }
        }
    }

    private fun handleShell(rawLine: String): Flow<TerminalOutputEvent> = flow {
        shellExecutor.run(rawLine, _cwd.value).collect { event ->
            when (event) {
                is ShellOutputEvent.Line -> {
                    val out = if (event.isError) {
                        TerminalOutputEvent.Error(event.text)
                    } else {
                        TerminalOutputEvent.Output(event.text)
                    }
                    emit(out)
                }
                is ShellOutputEvent.Finished -> {
                    if (event.exitCode != 0) {
                        emit(TerminalOutputEvent.Error("Process finished with exit code ${event.exitCode}"))
                    }
                }
            }
        }
    }

    companion object {
        private fun resolveInitialCwd(path: String): String {
            if (path.isNotBlank()) {
                val f = File(path)
                if (f.exists() && f.isDirectory) {
                    return f.canonicalPath
                }
            }
            return System.getProperty("user.home") ?: File(".").canonicalPath
        }
    }
}
