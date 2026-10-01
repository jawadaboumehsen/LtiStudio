package io.ltirom.tooling.core

public class ToolSafetyPolicy(
    private val interactivePrompt: (String) -> Boolean = { false }
) {
    public fun checkSafety(
        command: ToolCommand<*>,
        context: ExecutionContext
    ) {
        val risk = command.risk
        val id = command.toolId

        // DEVICE_WRITE and IRREVERSIBLE require serial
        if (risk == ToolRisk.DEVICE_WRITE || risk == ToolRisk.IRREVERSIBLE) {
            if (id == ToolId.ADB && context.adbSerial == null) {
                throw ToolSafetyException(
                    id,
                    "MISSING_SERIAL",
                    "ADB serial is required for device-write operations"
                )
            }
            if (id == ToolId.FASTBOOT && context.fastbootSerial == null) {
                throw ToolSafetyException(
                    id,
                    "MISSING_SERIAL",
                    "Fastboot serial is required for device-write operations"
                )
            }
        }

        // IRREVERSIBLE requires confirmation or token approval
        if (risk == ToolRisk.IRREVERSIBLE) {
            val operationToken = command.operationToken()
            if (context.autoConfirm) {
                if (context.confirmationToken != operationToken &&
                    !context.approvedTokens.contains(operationToken)) {
                    throw ToolSafetyException(
                        id,
                        "TOKEN_NOT_APPROVED",
                        "Operation token '$operationToken' must be explicitly approved"
                    )
                }
            } else {
                if (context.isInteractive) {
                    val confirmed = interactivePrompt(
                        "Are you sure you want to run irreversible operation '${id.logicalName} ${command.getArguments().joinToString(" ")}'? [y/N]"
                    )
                    if (!confirmed) {
                        throw ToolSafetyException(
                            id,
                            "USER_ABORT",
                            "User declined confirmation for irreversible command"
                        )
                    }
                } else {
                    throw ToolSafetyException(
                        id,
                        "NON_INTERACTIVE_ABORT",
                        "Irreversible command requires confirmation, but environment is non-interactive"
                    )
                }
            }
        }
    }
}
