package io.ltirom.tooling.core

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

public data class CommandExecutionResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val stdoutBytes: ByteArray? = null
)

public enum class StdinMode {
    EMPTY,
    INHERIT,
    PIPE_TEXT
}

public enum class StdoutMode {
    CAPTURE_TEXT,
    INHERIT,
    DISCARD
}

public enum class ToolRisk {
    READ_ONLY,
    HOST_WRITE,
    DEVICE_WRITE,
    IRREVERSIBLE
}

public enum class ArtifactKind { FILE, DIRECTORY }

public enum class ArtifactDirection { INPUT, OUTPUT, IN_PLACE }

public data class ArtifactRequirement(
    val name: String,
    val path: File,
    val kind: ArtifactKind,
    val direction: ArtifactDirection,
    val required: Boolean = true
)

public object ArtifactValidator {
    public fun validateInputs(toolId: ToolId, requirements: List<ArtifactRequirement>) {
        for (requirement in requirements) {
            if (!requirement.required || requirement.direction == ArtifactDirection.OUTPUT) continue
            if (!matchesKind(requirement.path, requirement.kind) || hasSymlinkComponent(requirement.path)) {
                throw ToolArtifactException(toolId, requirement.path,
                    "Required ${requirement.kind.name.lowercase()} input '${requirement.name}' does not exist: ${requirement.path}")
            }
        }
        validateDistinct(toolId, requirements)
    }

    public fun validateOutputs(toolId: ToolId, requirements: List<ArtifactRequirement>) {
        for (requirement in requirements) {
            if (!requirement.required || requirement.direction == ArtifactDirection.INPUT) continue
            if (!matchesKind(requirement.path, requirement.kind) || hasSymlinkComponent(requirement.path)) {
                throw ToolArtifactException(toolId, requirement.path,
                    "Expected ${requirement.kind.name.lowercase()} output '${requirement.name}' was not created: ${requirement.path}")
            }
        }
    }

    private fun validateDistinct(toolId: ToolId, requirements: List<ArtifactRequirement>) {
        val inputs = requirements.filter { it.direction != ArtifactDirection.OUTPUT }
        val inputPaths = inputs.map { it.path.toPath().toAbsolutePath().normalize() }.toSet()
        for (output in requirements.filter { it.direction != ArtifactDirection.INPUT }) {
            val normalized = output.path.toPath().toAbsolutePath().normalize()
            if (normalized in inputPaths && output.direction != ArtifactDirection.IN_PLACE) {
                throw ToolArtifactException(toolId, output.path,
                    "Input and output artifacts must be distinct unless direction is IN_PLACE: ${output.path}")
            }
        }
    }

    private fun matchesKind(path: File, kind: ArtifactKind): Boolean = when (kind) {
        ArtifactKind.FILE -> path.isFile && !Files.isSymbolicLink(path.toPath())
        ArtifactKind.DIRECTORY -> path.isDirectory && !Files.isSymbolicLink(path.toPath())
    }

    private fun hasSymlinkComponent(path: File): Boolean {
        var current = path.toPath().toAbsolutePath().normalize().parent
        while (current != null) {
            if (Files.isSymbolicLink(current)) return true
            current = current.parent
        }
        return false
    }
}

public interface ToolCommand<out R> {
    val toolId: ToolId
    val risk: ToolRisk
    
    fun getArguments(): List<String>

    /** Stable, operation-specific token used for non-interactive confirmation. */
    public fun operationToken(): String {
        val canonical = buildString {
            append(toolId.logicalName)
            append('\u0000')
            append(getArguments().joinToString("\u0000"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return "lti-confirm-" + digest.joinToString("") { "%02x".format(it) }
    }
    
    val stdinMode: StdinMode get() = StdinMode.EMPTY
    val stdoutMode: StdoutMode get() = StdoutMode.CAPTURE_TEXT
    val stderrMode: StdoutMode get() = StdoutMode.CAPTURE_TEXT
    
    val stdinText: String? get() = null
    val timeoutMs: Long get() = 900000L // Default 15 mins

    /** Exit statuses that represent a successful invocation for this command. */
    val acceptedExitCodes: Set<Int> get() = setOf(0)

    /** Paths declared by the reviewed tool specification and checked around execution. */
    val artifactRequirements: List<ArtifactRequirement> get() = emptyList()

    public fun validateInputArtifacts() = ArtifactValidator.validateInputs(toolId, artifactRequirements)
    public fun validateOutputArtifacts() = ArtifactValidator.validateOutputs(toolId, artifactRequirements)
    
    fun parseResult(result: CommandExecutionResult): R
}

/** Binds the selected device at the last possible point before process execution. */
public class SerialBoundCommand<R>(
    private val delegate: ToolCommand<R>,
    private val serial: String
) : ToolCommand<R> by delegate {
    init {
        require(serial.isNotBlank()) { "Device serial must not be blank" }
    }

    override fun getArguments(): List<String> {
        val original = delegate.getArguments()
        val withoutSelector = original.toMutableList()
        var index = 0
        while (index < withoutSelector.lastIndex) {
            if (withoutSelector[index] == "-s") {
                withoutSelector.subList(index, index + 2).clear()
                continue
            }
            index++
        }
        return listOf("-s", serial) + withoutSelector
    }

    override fun operationToken(): String {
        val canonical = buildString {
            append(toolId.logicalName)
            append('\u0000')
            append(serial)
            append('\u0000')
            append(getArguments().joinToString("\u0000"))
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
        return "lti-confirm-" + digest.joinToString("") { "%02x".format(it) }
    }
}

/** Command contract for stdout that must not be decoded as UTF-8 text. */
public interface BinaryToolCommand<out R> : ToolCommand<R> {
    public fun parseBinaryResult(stdout: ByteArray, stderr: String): R

    override fun parseResult(result: CommandExecutionResult): R =
        parseBinaryResult(result.stdoutBytes ?: result.stdout.toByteArray(Charsets.ISO_8859_1), result.stderr)
}

public class RawToolCommand(
    override val toolId: ToolId,
    private val args: List<String>,
    override val risk: ToolRisk = ToolRisk.READ_ONLY,
    override val timeoutMs: Long = 900000L
) : ToolCommand<CommandExecutionResult> {
    override fun getArguments(): List<String> = args
    override fun parseResult(result: CommandExecutionResult): CommandExecutionResult = result
}

public class RemoteCommand(
    public val shellCommand: String,
    override val risk: ToolRisk = ToolRisk.DEVICE_WRITE
) : ToolCommand<CommandExecutionResult> {
    override val toolId: ToolId get() = ToolId.ADB
    override fun getArguments(): List<String> {
        // Escaping check: verify command contains no unescaped control characters
        require(!shellCommand.contains("\r") && !shellCommand.contains("\n")) {
            "shellCommand must not contain raw carriage return or newline characters"
        }
        return listOf("shell", shellCommand)
    }
    override fun parseResult(result: CommandExecutionResult): CommandExecutionResult = result
}

public class OemCommand(
    public val oemArgs: List<String>,
    override val risk: ToolRisk = ToolRisk.IRREVERSIBLE
) : ToolCommand<CommandExecutionResult> {
    override val toolId: ToolId get() = ToolId.FASTBOOT
    override fun getArguments(): List<String> {
        return listOf("oem") + oemArgs
    }
    override fun parseResult(result: CommandExecutionResult): CommandExecutionResult = result
}

public class GhExtensionCommand(
    public val extensionName: String,
    public val extensionArgs: List<String>,
    override val risk: ToolRisk = ToolRisk.READ_ONLY
) : ToolCommand<CommandExecutionResult> {
    override val toolId: ToolId get() = ToolId.GH
    override fun getArguments(): List<String> {
        require(extensionName.startsWith("ext-") || extensionName.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            "extensionName must be alphanumeric with dashes or underscores"
        }
        return listOf(extensionName) + extensionArgs
    }
    override fun parseResult(result: CommandExecutionResult): CommandExecutionResult = result
}
