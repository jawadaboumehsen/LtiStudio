package io.ltirom.tooling.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
public data class AuditRecord(
    val timestamp: Long,
    val toolId: String,
    val risk: String,
    val arguments: List<String>,
    val durationMs: Long,
    val exitCode: Int,
    val stdoutHash: String,
    val stderrHash: String,
    val binarySha256: String,
    val traceId: String = TraceContext.create().traceId
) {
    public companion object {
        private val json = Json { prettyPrint = false }

        public fun create(
            binary: ToolBinary,
            command: ToolCommand<*>,
            result: CommandExecutionResult,
            redactor: Redactor
        ): AuditRecord {
            // Sanitize arguments by running them through the redactor
            val sanitizedArgs = command.getArguments().map { redactor.redact(it) }

            val stdoutHash = sha256String(result.stdout)
            val stderrHash = sha256String(result.stderr)

            // Compute or get fingerprint hash
            val fingerprint = ToolFingerprint.computeFor(binary.file)

            return AuditRecord(
                timestamp = System.currentTimeMillis(),
                toolId = command.toolId.logicalName,
                risk = command.risk.name,
                arguments = sanitizedArgs,
                durationMs = result.durationMs,
                exitCode = result.exitCode,
                stdoutHash = stdoutHash,
                stderrHash = stderrHash,
                binarySha256 = fingerprint.sha256
            )
        }

        private fun sha256String(input: String): String {
            if (input.isEmpty()) return ""
            val digest = MessageDigest.getInstance("SHA-256")
            val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
            return hash.joinToString("") { "%02x".format(it) }
        }
    }

    public fun toJson(): String {
        return json.encodeToString(serializer(), this)
    }
}
