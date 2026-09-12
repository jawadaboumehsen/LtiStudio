package io.ltirom.tooling.client.wsl

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.UUID

public data class WslProcessLaunchResult(
    val process: Process,
    val port: Int,
    val token: String,
    val pid: Long
)

/**
 * Single Responsibility: Spawns the LtiRomServer process in WSL and parses the handshake.
 */
public open class WslProcessLauncher(
    private val startupTimeoutMs: Long = 20_000L,
) {
    public var lastLogTail: String? = null
        private set

    public open suspend fun launch(
        distro: String,
        binaryPath: String,
    ): WslProcessLaunchResult = withContext(Dispatchers.IO) {
        val token = UUID.randomUUID().toString().replace("-", "")

        val cmd = listOf(
            "wsl.exe", "-d", distro, "-e",
            binaryPath,
            "--port", "0",
            "--token", token,
            "--host", "0.0.0.0",
            "--distro", distro,
        )

        val pb = ProcessBuilder(cmd)
        pb.redirectErrorStream(true)
        val p = pb.start()

        val reader = BufferedReader(InputStreamReader(p.inputStream))
        var actualPort = -1
        var pid = -1L
        val capturedLines = mutableListOf<String>()

        val readyRegex = Regex("""LTI_WSL_SERVER_READY\s+port=(\d+)\s+token=(\S+)\s+pid=(\d+)""")

        val deadline = System.currentTimeMillis() + startupTimeoutMs
        while (System.currentTimeMillis() < deadline && p.isAlive) {
            if (reader.ready()) {
                val line = reader.readLine() ?: break
                capturedLines.add(line)
                val match = readyRegex.find(line)
                if (match != null) {
                    actualPort = match.groupValues[1].toInt()
                    pid = match.groupValues[3].toLong()
                    break
                }
            } else {
                Thread.sleep(50)
            }
        }

        while (reader.ready()) {
            val remaining = reader.readLine() ?: break
            capturedLines.add(remaining)
        }

        if (actualPort == -1) {
            p.destroyForcibly()
            val tail = capturedLines.takeLast(10).joinToString("\n").trim()
            lastLogTail = tail.ifEmpty { null }
            val msg = if (tail.isNotEmpty()) {
                "Timed out waiting for LtiRomServer daemon to start in WSL distro '$distro':\n$tail"
            } else {
                "Timed out waiting for LtiRomServer daemon to start in WSL distro '$distro'"
            }
            throw IllegalStateException(msg)
        }

        // Read background logs asynchronously to prevent stdout buffer blocking
        CoroutineScope(Dispatchers.IO).launch {
            try {
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    // stdout drainage
                }
            } catch (_: Exception) {}
        }

        WslProcessLaunchResult(
            process = p,
            port = actualPort,
            token = token,
            pid = pid
        )
    }
}
