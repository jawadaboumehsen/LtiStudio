package io.ltirom.tooling.client.wsl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.withTimeout

/**
 * Single Responsibility: Performs HTTP health probing and system telemetry checks
 * against an active daemon server instance.
 */
public open class DaemonHealthProber(
    private val httpClient: HttpClient? = null
) {
    public open suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long = 5000L): Boolean {
        val client = httpClient ?: return false
        return try {
            withTimeout(timeoutMs) {
                val response = httpClient.get("${info.httpBaseUrl}/api/v1/health")
                response.status.isSuccess()
            }
        } catch (_: Exception) {
            false
        }
    }

    public open suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? {
        val client = httpClient ?: return null
        return try {
            val response = client.get("${info.httpBaseUrl}/api/v1/health")
            if (response.status.isSuccess()) {
                response.body<WslServerInfo>()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Asks the service to shut down. Without [force] the service refuses (409) while any run is active
     * and, once it accepts, admits no new runs. True only when the service accepted.
     */
    public open suspend fun requestShutdown(info: ServerConnectionDescriptor, force: Boolean): Boolean {
        val client = httpClient ?: return false
        return try {
            client.post("${info.httpBaseUrl}/api/v1/system/shutdown?force=$force") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }.status.isSuccess()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            false
        }
    }

    public open suspend fun getAvailableTools(info: ServerConnectionDescriptor): List<ToolStatusInfo> {
        val client = httpClient ?: return emptyList()
        return try {
            val response = client.get("${info.httpBaseUrl}/api/v1/tools") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }
            if (response.status.isSuccess()) {
                response.body<List<ToolStatusInfo>>()
            } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
