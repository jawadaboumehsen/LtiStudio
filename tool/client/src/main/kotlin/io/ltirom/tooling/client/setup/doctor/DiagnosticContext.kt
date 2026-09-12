package io.ltirom.tooling.client.setup.doctor

/**
 * Execution context provided to all system diagnostic inspectors.
 *
 * @property distro Target WSL distribution or OS environment identifier.
 * @property home User home directory in the target environment (e.g., "/home/lti").
 * @property binDir Tools binary directory (e.g., "$home/LtiRomTools/bin").
 * @property binariesPresent Indicates whether compiled project binaries already exist.
 */
public data class DiagnosticContext(
    val distro: String,
    val home: String,
    val binDir: String,
    val binariesPresent: Boolean,
    val availableCommands: Set<String> = emptySet(),
)
