package io.ltirom.tooling.core

/** Stable transfer telemetry extracted from adb push/pull diagnostics. */
public data class AdbTransferProgress(
    val percent: Int?,
    val transferredBytes: Long?,
    val totalBytes: Long?,
    val rawLines: List<String>
)

public object AdbTransferProgressParser : ToolOutputParser<AdbTransferProgress> {
    private val percentPattern = Regex("(?<!\\d)(\\d{1,3})%")
    private val bytesPattern = Regex("(?i)(\\d+)\\s+bytes")

    override fun parse(stdout: String, stderr: String): AdbTransferProgress {
        val lines = (stdout + (if (stdout.isNotEmpty() && stderr.isNotEmpty()) "\n" else "") + stderr)
            .lineSequence().filter { it.isNotBlank() }.toList()
        val percentages = lines.mapNotNull { percentPattern.find(it)?.groupValues?.get(1)?.toIntOrNull() }
        require(percentages.all { it in 0..100 }) { "adb transfer percentage is outside 0..100" }
        require(percentages.zipWithNext().all { (left, right) -> right >= left }) {
            "adb transfer percentage regressed"
        }
        val bytes = lines.mapNotNull { bytesPattern.find(it)?.groupValues?.get(1)?.toLongOrNull() }
        return AdbTransferProgress(
            percent = percentages.lastOrNull(),
            transferredBytes = bytes.lastOrNull(),
            totalBytes = null,
            rawLines = lines
        )
    }
}
