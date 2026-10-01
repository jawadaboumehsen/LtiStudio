package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolRisk
import kotlinx.serialization.Serializable

@Serializable
data class ToolSpecification(
    val schemaVersion: Int,
    val tool: String,
    val executable: String,
    val aliases: List<String> = emptyList(),
    val platform: List<String>,
    val sourceRevision: String? = null,
    val binarySha256: String? = null,
    val versionText: String? = null,
    val helpSha256: String? = null,
    val commands: List<CommandSpec> = emptyList(),
    val globalOptions: List<OptionSpec> = emptyList(),
    val exitCodes: List<ExitCodeSpec> = emptyList(),
    val defaultTimeout: String? = null,
    val risk: ToolRisk,
    val outputParser: String? = null,
    val quirks: List<String> = emptyList(),
    val explicitExclusions: List<String> = emptyList()
)

@Serializable
data class CommandSpec(
    val name: String,
    val subcommandPath: List<String>,
    val description: String,
    val options: List<OptionSpec> = emptyList(),
    val arguments: List<ArgumentSpec> = emptyList(),
    val risk: ToolRisk? = null,
    val timeout: String? = null,
    val outputParser: String? = null,
    val outputType: String = "Unit",
    val artifacts: List<ArtifactSpec> = emptyList()
)

@Serializable
data class OptionSpec(
    val name: String,
    val spelling: String,
    val aliases: List<String> = emptyList(),
    val description: String,
    val type: OptionType,
    val choices: List<String>? = null,
    val default: String? = null,
    val required: Boolean = false,
    val repeatable: Boolean = false,
    val cardinality: String? = null,
    val mutuallyExclusiveGroup: String? = null,
    val requiredTogetherGroup: String? = null,
    val risk: ToolRisk? = null,
    val valueType: String? = null, // e.g. secret, path, device-path, partition, property, url
    val redact: Boolean = false
)

enum class OptionType {
    BOOLEAN, STRING, INT, PATH, CHOICE
}

@Serializable
data class ArgumentSpec(
    val name: String,
    val description: String,
    val type: OptionType,
    val choices: List<String>? = null,
    val default: String? = null,
    val required: Boolean = true,
    val repeatable: Boolean = false,
    val valueType: String? = null
)

@Serializable
data class ExitCodeSpec(
    val code: Int,
    val meaning: String,
    val isSuccess: Boolean
)

@Serializable
data class ArtifactSpec(
    val name: String,
    val type: ArtifactType,
    val direction: ArtifactDirection,
    val required: Boolean = true,
    /** Source command parameter; for repeatable positional arguments, index selects one item. */
    val parameter: String = name,
    val index: Int? = null
)

enum class ArtifactType {
    FILE, DIRECTORY
}

enum class ArtifactDirection {
    INPUT, OUTPUT, IN_PLACE
}
