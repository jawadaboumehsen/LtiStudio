---
name: clikt
description: Build modern, multiplatform command-line interfaces and in-app terminal dispatchers in Kotlin using the Clikt 5 library. Use this skill whenever building or modifying CLI commands, subcommands, options, arguments, coroutine-based suspending commands (SuspendingCliktCommand), custom parameter validation, autocomplete, terminal formatting via Mordant, or embedding command dispatching in Compose Multiplatform desktop/mobile IDE terminals.
---

# Clikt 5 — Kotlin Multiplatform Command Line Interfaces

Clikt is a multiplatform Kotlin library for creating intuitive, type-safe command line interfaces. It uses property delegates to declare options and arguments, supports nested subcommand hierarchies, provides coroutine support via `SuspendingCliktCommand`, and seamlessly integrates with Mordant for rich terminal output.

---

## 1. Mental Model: Core Architecture

Clikt's execution model revolves around three main components:

1. **Commands (`CliktCommand` / `SuspendingCliktCommand`)**:
   - Represents a runnable command or subcommand.
   - Declares options and arguments as delegated properties (`by option()`, `by argument()`).
   - Implements `run()` (or `suspend fun run()`).
   - Can register child subcommands via `subcommands(...)`.

2. **Parameters (Options & Arguments)**:
   - **Options**: Named flags (e.g. `-o`, `--output`, `--count=3`). Defined via `option()`.
   - **Arguments**: Positional parameters (e.g. `cp <src> <dest>`). Defined via `argument()`.
   - Property delegates automatically handle type conversion (`.int()`, `.path()`, `.choice()`), cardinality (`.multiple()`, `.pair()`), defaults (`.default(...)`), and nullability (`.optional()`, `.required()`).

3. **Context & Terminal (`Context`, `Mordant Terminal`)**:
   - Every command has a `Context` containing configuration, environment variables, help formatters, and a Mordant `Terminal` instance.
   - Context is inherited from parent commands to subcommands, allowing shared configuration, dependency injection, and centralized terminal routing.

---

## 2. Basic Command Setup

### Minimal Command

```kotlin
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.main
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.int

class GreetCommand : CliktCommand(name = "greet") {
    val name: String by option("-n", "--name", help = "Person to greet").required()
    val count: Int by option("-c", "--count", help = "Number of greetings").int().default(1)

    override fun run() {
        repeat(count) {
            echo("Hello, $name!")
        }
    }
}

fun main(args: Array<String>) = GreetCommand().main(args)
```

---

## 3. Options & Arguments Recipes

### Common Option Conversions
```kotlin
// Primitive types
val count by option().int().default(0)
val ratio by option().double().default(1.0)
val verbose by option("-v", "--verbose").flag(default = false)

// Choices / Enums
enum class OutputFormat { JSON, XML, TEXT }
val format by option().enum<OutputFormat> { it.name.lowercase() }.default(OutputFormat.TEXT)
val mode by option().choice("dev", "staging", "prod").default("dev")

// Multiple values
val tags by option("-t", "--tag").multiple()

// Validation & Preconditions
val port by option().int()
    .check("Port must be between 1024 and 65535") { it in 1024..65535 }
```

### Positional Arguments
```kotlin
// Required single argument
val sourcePath by argument("source", help = "Source directory")

// Optional argument
val destinationPath by argument("destination", help = "Optional target").optional()

// Variable number of arguments
val files by argument("files", help = "Input files to process").multiple(required = true)
```

---

## 4. Subcommands & Hierarchies

Clikt makes nested subcommands intuitive:

```kotlin
class RootCommand : CliktCommand(name = "lti") {
    init {
        subcommands(
            StatusSubcommand(),
            BuildSubcommand(),
            ThemeSubcommand(),
        )
    }

    override fun run() {
        // Runs before the subcommand executes
    }
}

class StatusSubcommand : CliktCommand(name = "status") {
    override fun run() {
        echo("All systems operational.")
    }
}

class BuildSubcommand : CliktCommand(name = "build") {
    val release by option("--release").flag(default = false)

    override fun run() {
        echo("Building (release=$release)...")
    }
}
```

### Sharing State Between Commands
Use `currentContext.findOrSetObject` or pass shared state down via custom Context objects:

```kotlin
data class SessionConfig(val workingDir: String, val debug: Boolean)

class CliRoot : CliktCommand(name = "app") {
    val debug by option("--debug").flag()

    override fun run() {
        currentContext.findOrSetObject { SessionConfig(workingDir = ".", debug = debug) }
    }
}

class SubTask : CliktCommand(name = "task") {
    val config by requireObject<SessionConfig>()

    override fun run() {
        if (config.debug) echo("Running in debug mode")
    }
}
```

---

## 5. Coroutines with SuspendingCliktCommand

For asynchronous work (network API calls, long-running database queries, background jobs):

```kotlin
import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main

class SyncCommand(
    private val apiClient: ApiClient,
) : SuspendingCliktCommand(name = "sync") {

    val branch by option("-b", "--branch").default("main")

    override suspend fun run() {
        echo("Syncing branch $branch...")
        val result = apiClient.fetchUpdates(branch)
        echo("Fetched ${result.commitsCount} commits.")
    }
}

suspend fun main(args: Array<String>) = SyncCommand(ApiClient()).main(args)
```

---

## 6. Embedding Clikt in Graphical Terminals (IDE In-App Terminal)

In an in-process terminal (such as `LtiRomStudio`'s bottom terminal panel), commands run without terminating the process (never call `exitProcess()` or `System.exit()`):

1. **Custom Terminal Binding**:
   Route output through Clikt's `context { terminal = customTerminal }`.

2. **In-Process Parsing without Exit**:
   Use `parse(argv)` inside a `try-catch` block:

```kotlin
fun executeCommand(commandLine: String, root: CliktCommand, console: TerminalConsole) {
    val tokens = CommandTokenizer.tokenize(commandLine)
    if (tokens.isEmpty()) return

    try {
        root.parse(tokens)
    } catch (e: PrintHelpMessage) {
        console.writeOutput(e.command.getFormattedHelp())
    } catch (e: UsageError) {
        console.writeError(e.formatMessage(root.currentContext.errorSurface))
    } catch (e: CliktError) {
        console.writeError(e.message ?: "Command failed")
    } catch (e: Exception) {
        console.writeError("Execution error: ${e.message}")
    }
}
```

---

## 7. Testing Clikt Commands

Clikt includes a built-in testing harness (`clikt-testing` / `command.test(...)`):

```kotlin
import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GreetCommandTest {
    @Test
    fun testGreeting() {
        val command = GreetCommand()
        val result = command.test("-n World -c 2")

        assertEquals(0, result.statusCode)
        assertEquals("Hello, World!\nHello, World!\n", result.output)
    }

    @Test
    fun testMissingRequiredOption() {
        val command = GreetCommand()
        val result = command.test("")

        assertEquals(1, result.statusCode)
        assertTrue(result.stderr.contains("missing option --name"))
    }
}
```

---

## 8. Reference Documentation Index

Comprehensive official documentation files are available in the `references/` folder:

| Topic | Reference File | Summary |
|---|---|---|
| **Quickstart** | [`references/quickstart.md`](references/quickstart.md) | Installation, basic commands, `echo`, and syntax |
| **Commands** | [`references/commands.md`](references/commands.md) | Subcommands, command lifecycles, and execution flows |
| **Parameters** | [`references/parameters.md`](references/parameters.md) | Fundamentals of parameter declaration and binding |
| **Options** | [`references/options.md`](references/options.md) | Flags, values, choices, envvars, and multi-value options |
| **Arguments** | [`references/arguments.md`](references/arguments.md) | Positional parameters, variable arguments, optional args |
| **Documenting** | [`references/documenting.md`](references/documenting.md) | Help text formatting, themes, colors, epilogs |
| **Autocomplete** | [`references/autocomplete.md`](references/autocomplete.md) | Bash, Zsh, and Fish shell completion generation |
| **Exceptions** | [`references/exceptions.md`](references/exceptions.md) | Error handling, custom exit codes, and message formatting |
| **Testing** | [`references/testing.md`](references/testing.md) | Unit testing commands with `test()` harness |
| **Advanced** | [`references/advanced.md`](references/advanced.md) | Context chaining, custom parameter types, composability |
| **Migration** | [`references/migration.md`](references/migration.md) | Migrating across Clikt major versions (including Clikt 5) |
| **Why Clikt** | [`references/whyclikt.md`](references/whyclikt.md) | Design rationale and comparison with other CLI libraries |
