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
public class DaemonHealthProber(
    private val httpClient: HttpClient
) {
    public suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long = 5000L): Boolean {
        return try {
            withTimeout(timeoutMs) {
                val response = httpClient.get("${info.httpBaseUrl}/api/v1/health")
                response.status.isSuccess()
            }
        } catch (_: Exception) {
            false
        }
    }

    public suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? {
        return try {
            val response = httpClient.get("${info.httpBaseUrl}/api/v1/health")
            if (response.status.isSuccess()) {
                response.body<WslServerInfo>()
            } else null
        } catch (_: Exception) {
            null
        }
    }

    public suspend fun getAvailableTools(info: ServerConnectionDescriptor): List<ToolStatusInfo> {
        return try {
            val response = httpClient.get("${info.httpBaseUrl}/api/v1/tools") {
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
