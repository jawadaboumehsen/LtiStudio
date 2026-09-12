package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.serialization.json.*

/**
 * Single Responsibility: Reading and parsing ~/.ltirom/server.state lockfile from WSL
 * for zero-latency warm client re-attachment.
 */
public class WslStateStore(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    public fun readState(distro: String, wslIp: String): ServerConnectionDescriptor? {
        val res = cli.execute(distro, listOf("bash", "-c", "cat ~/.ltirom/server.state 2>/dev/null"), timeoutSeconds = 3)
        val text = res.output.trim()
        if (text.isBlank() || !text.startsWith("{")) return null

        return runCatching {
            val root = json.parseToJsonElement(text).jsonObject
            val port = root["port"]?.jsonPrimitive?.intOrNull ?: return null
            val token = root["token"]?.jsonPrimitive?.content ?: return null
            val pid = root["pid"]?.jsonPrimitive?.longOrNull ?: -1L

            ServerConnectionDescriptor(
                host = wslIp,
                port = port,
                token = token,
                pid = pid,
                distro = distro
            )
        }.getOrNull()
    }
}
