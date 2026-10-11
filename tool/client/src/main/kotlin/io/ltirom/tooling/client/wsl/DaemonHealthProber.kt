package io.ltirom.tooling.client.wsl

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.ToolStatusInfo
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

/** What a shutdown request established; a refusal is an answer, unlike a timeout or a transport error. */
public enum class ShutdownOutcome {
    /** The service agreed to stop. */
    ACCEPTED,

    /** The service answered that it stays up (409: work it could not stop); it still owns that work. */
    REFUSED,

    /** No usable answer: unreachable, timed out, or an unexpected status. */
    NO_ANSWER,
}

/**
 * Single Responsibility: Performs HTTP health probing and system telemetry checks
 * against an active daemon server instance.
 *
 * The shared HTTP client has no default request timeout, so every request here bounds itself, response body
 * included. A caller's own cancellation is never swallowed.
 */
public open class DaemonHealthProber(
    private val httpClient: HttpClient? = null
) {
    /** [block] within [timeoutMs] (request and body read), or null on timeout or any failure but cancellation. */
    private suspend fun <T> bounded(timeoutMs: Long, block: suspend () -> T?): T? = try {
        withTimeoutOrNull(timeoutMs) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    public open suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long = 5000L): Boolean {
        val client = httpClient ?: return false
        return bounded(timeoutMs) { client.get("${info.httpBaseUrl}/api/v1/health").status.isSuccess() } ?: false
    }

    public open suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? {
        val client = httpClient ?: return null
        return bounded(TELEMETRY_TIMEOUT_MS) {
            val response = client.get("${info.httpBaseUrl}/api/v1/health")
            if (response.status.isSuccess()) response.body<WslServerInfo>() else null
        }
    }

    /**
     * Asks the service to shut down. Without [force] the service refuses (409) while any run is active
     * and, once it accepts, admits no new runs.
     */
    public open suspend fun requestShutdown(info: ServerConnectionDescriptor, force: Boolean): ShutdownOutcome {
        val client = httpClient ?: return ShutdownOutcome.NO_ANSWER
        // A forced stop is answered only after the service cancelled its work (up to ~7 s), so it waits
        // longer than that, yet less than the app's 10 s shutdown deadline.
        val status = bounded(if (force) FORCED_SHUTDOWN_TIMEOUT_MS else SHUTDOWN_TIMEOUT_MS) {
            client.post("${info.httpBaseUrl}/api/v1/system/shutdown?force=$force") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }.status
        }
        return when {
            status == null -> ShutdownOutcome.NO_ANSWER
            status.isSuccess() -> ShutdownOutcome.ACCEPTED
            status == HttpStatusCode.Conflict -> ShutdownOutcome.REFUSED
            else -> ShutdownOutcome.NO_ANSWER
        }
    }

    public companion object {
        public const val SHUTDOWN_TIMEOUT_MS: Long = 5_000L
        public const val FORCED_SHUTDOWN_TIMEOUT_MS: Long = 9_000L
        public const val TELEMETRY_TIMEOUT_MS: Long = 5_000L
    }

    public open suspend fun getAvailableTools(info: ServerConnectionDescriptor): List<ToolStatusInfo> {
        val client = httpClient ?: return emptyList()
        return bounded(TELEMETRY_TIMEOUT_MS) {
            val response = client.get("${info.httpBaseUrl}/api/v1/tools") {
                header(HttpHeaders.Authorization, "Bearer ${info.token}")
            }
            if (response.status.isSuccess()) response.body<List<ToolStatusInfo>>() else null
        } ?: emptyList()
    }
}
