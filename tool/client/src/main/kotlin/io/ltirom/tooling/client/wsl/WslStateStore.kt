package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.ServerConnectionDescriptor

/**
 * Reads the LtiRomServer daemon's lockfile written on its own (Linux) side, enabling
 * instant zero-latency reconnection to an already-running daemon (VS Code Remote pattern).
 *
 * Format is defined by `LockfileStateStore` in the `LtiRomServer` project:
 * `~/.ltirom/server.state` — `{"port":N,"token":"...","pid":N,"distro":"...","startTimeEpochMs":N}`.
 * This store never writes the file; only the server process does.
 */
public class WslStateStore(
    private val cli: WslCliExecutor = WslCliExecutor(),
) {
    public fun readState(distro: String, wslIp: String): ServerConnectionDescriptor? {
        val result = cli.execute(distro, listOf("cat", "\$HOME/.ltirom/server.state"), timeoutSeconds = 5)
        if (result.exitCode != 0 || result.output.isBlank()) return null

        return runCatching {
            val port = extractInt(result.output, "port") ?: return null
            val token = extractString(result.output, "token") ?: return null
            val pid = extractLong(result.output, "pid") ?: return null
            val stateDistro = extractString(result.output, "distro") ?: distro
            ServerConnectionDescriptor(
                host = wslIp,
                port = port,
                token = token,
                pid = pid,
                distro = stateDistro,
            )
        }.getOrNull()
    }

    private fun extractInt(json: String, key: String): Int? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toIntOrNull()

    private fun extractLong(json: String, key: String): Long? =
        Regex("\"$key\"\\s*:\\s*(\\d+)").find(json)?.groupValues?.get(1)?.toLongOrNull()

    private fun extractString(json: String, key: String): String? =
        Regex("\"$key\"\\s*:\\s*\"([^\"]*)\"").find(json)?.groupValues?.get(1)
}
