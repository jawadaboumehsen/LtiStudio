package io.ltirom.tooling.core

public data class ExecutionContext(
    val isInteractive: Boolean = false,
    val autoConfirm: Boolean = false,
    val adbSerial: AdbSerial? = null,
    val fastbootSerial: FastbootSerial? = null,
    val approvedTokens: Set<String> = emptySet(),
    val confirmationToken: String? = null
)
