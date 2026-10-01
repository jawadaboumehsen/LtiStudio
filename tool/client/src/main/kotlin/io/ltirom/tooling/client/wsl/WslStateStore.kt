package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import kotlinx.serialization.json.*

/**
 * Single Responsibility: Reading and parsing ~/.ltirom/server.state lockfile from WSL
 * for zero-latency warm client re-attachment.
 */
public open class WslStateStore(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    public open fun readState(distro: String, wslIp: String): ServerConnectionDescriptor? {
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

    /**
     * Whether the service process [pid] still exists in [distro]: true or false when that is known, null
     * when it cannot be told (unknown pid, or the WSL call failed). A failed health check is not proof of
     * exit; this is.
     */
    public open fun isProcessAlive(distro: String, pid: Long): Boolean? {
        if (pid <= 0) return null
        val res = cli.execute(
            distro,
            listOf("sh", "-c", "if [ -e /proc/$pid ]; then echo alive; else echo gone; fi"),
            timeoutSeconds = 3,
        )
        return when (res.output.trim()) {
            "alive" -> true
            "gone" -> false
            else -> null
        }
    }
}
