package io.ltirom.tooling.codegen.spec

import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.ToolRisk

import io.ltirom.tooling.core.ToolOutputParserRegistry

class SpecValidationException(message: String) : Exception(message)

object SpecValidator {

    fun validate(spec: ToolSpecification) {
        val errors = mutableListOf<String>()

        // 1. Tool Id Validation
        val toolId = ToolId.entries.firstOrNull { it.logicalName == spec.tool }
        if (toolId == null) {
            errors.add("Tool '${spec.tool}' is not a recognized ToolId")
        }

        if (spec.executable.isEmpty()) {
            errors.add("Executable name cannot be empty")
        }

        spec.outputParser?.let { parserId ->
            if (parserId !in ToolOutputParserRegistry.getRegisteredIds()) {
                errors.add("Tool level outputParser '$parserId' is not registered in ToolOutputParserRegistry")
            }
        }

        // 2. Global Options Validation
        validateOptions(spec.globalOptions, "Global options", errors)

        // 3. Commands Validation
        val commandNames = mutableSetOf<String>()
        val commandPaths = mutableSetOf<List<String>>()

        for (cmd in spec.commands) {
            val cmdPath = "Command '${cmd.name}'"
            if (!cmd.name.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*$"))) {
                errors.add("$cmdPath name must be camelCase alphanumeric")
            }
            if (!commandNames.add(cmd.name)) {
                errors.add("Duplicate command name: '${cmd.name}'")
            }
            if (!commandPaths.add(cmd.subcommandPath)) {
                errors.add("Duplicate subcommand path: ${cmd.subcommandPath}")
            }
            if (cmd.subcommandPath.isEmpty()) {
                errors.add("$cmdPath subcommand path cannot be empty")
            }

            validateOptions(cmd.options, "$cmdPath options", errors)
            validateArguments(cmd.arguments, "$cmdPath arguments", errors)

            val parameterNames = (spec.globalOptions + cmd.options).map { it.name } + cmd.arguments.map { it.name }
            for (artifact in cmd.artifacts) {
                if (artifact.parameter !in parameterNames) {
                    errors.add("$cmdPath artifact '${artifact.name}' must refer to an option or argument via '${artifact.parameter}'")
                }
                val parameter = (spec.globalOptions + cmd.options).firstOrNull { it.name == artifact.parameter }
                    ?: cmd.arguments.firstOrNull { it.name == artifact.parameter }
                if (artifact.index != null && parameter is ArgumentSpec && !parameter.repeatable) {
                    errors.add("$cmdPath artifact '${artifact.name}' uses an index on non-repeatable argument '${artifact.parameter}'")
                }
            }

            cmd.outputParser?.let { parserId ->
                if (parserId !in ToolOutputParserRegistry.getRegisteredIds()) {
                    errors.add("Command level outputParser '$parserId' is not registered in ToolOutputParserRegistry")
                }
            }

        }

        // 4. Exit Codes Validation
        if (spec.exitCodes.isNotEmpty()) {
            val codes = mutableSetOf<Int>()
            var hasSuccess = false
            for (ec in spec.exitCodes) {
                if (!codes.add(ec.code)) {
                    errors.add("Duplicate exit code: ${ec.code}")
                }
                if (ec.isSuccess) {
                    hasSuccess = true
                }
            }
            if (!hasSuccess) {
                errors.add("No success exit code defined")
            }
        }

        if (errors.isNotEmpty()) {
            throw SpecValidationException(
                "Tool specification validation failed for '${spec.tool}':\n" +
                errors.joinToString("\n") { "  - $it" }
            )
        }
    }

    private fun validateOptions(options: List<OptionSpec>, context: String, errors: MutableList<String>) {
        val names = mutableSetOf<String>()
        val spellings = mutableSetOf<String>()

        for (opt in options) {
            val optPath = "$context option '${opt.name}'"
            if (!opt.name.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*$"))) {
                errors.add("$optPath name must be camelCase alphanumeric")
            }
            if (!opt.spelling.startsWith("-")) {
                errors.add("$optPath spelling '${opt.spelling}' must start with '-' or '--'")
            }
            if (!names.add(opt.name)) {
                errors.add("Duplicate option name in $context: '${opt.name}'")
            }
            if (!spellings.add(opt.spelling)) {
                errors.add("Duplicate option spelling in $context: '${opt.spelling}'")
            }
            for (alias in opt.aliases) {
                if (!alias.startsWith("-")) {
                    errors.add("$optPath alias '$alias' must start with '-'")
                }
                if (!spellings.add(alias)) {
                    errors.add("Duplicate option alias/spelling in $context: '$alias'")
                }
            }

            if (opt.type == OptionType.CHOICE) {
                val choices = opt.choices
                if (choices.isNullOrEmpty()) {
                    errors.add("$optPath of type CHOICE must define choices")
                } else {
                    if (opt.default != null && opt.default !in choices) {
                        errors.add("$optPath default value '${opt.default}' is not in choices $choices")
                    }
                }
            }
        }
    }

    private fun validateArguments(args: List<ArgumentSpec>, context: String, errors: MutableList<String>) {
        val names = mutableSetOf<String>()
        var sawOptional = false

        for (i in args.indices) {
            val arg = args[i]
            val argPath = "$context argument '${arg.name}'"
            if (!arg.name.matches(Regex("^[a-zA-Z][a-zA-Z0-9]*$"))) {
                errors.add("$argPath name must be camelCase alphanumeric")
            }
            if (!names.add(arg.name)) {
                errors.add("Duplicate argument name in $context: '${arg.name}'")
            }

            if (arg.required) {
                if (sawOptional) {
                    errors.add("Required argument '${arg.name}' cannot follow an optional argument in $context")
                }
            } else {
                sawOptional = true
            }

            if (arg.repeatable && i != args.lastIndex) {
                errors.add("Repeatable argument '${arg.name}' must be the last argument in $context")
            }

            if (arg.type == OptionType.CHOICE) {
                val choices = arg.choices
                if (choices.isNullOrEmpty()) {
                    errors.add("$argPath of type CHOICE must define choices")
                } else {
                    if (arg.default != null && arg.default !in choices) {
                        errors.add("$argPath default value '${arg.default}' is not in choices $choices")
                    }
                }
            }
        }
    }

}
