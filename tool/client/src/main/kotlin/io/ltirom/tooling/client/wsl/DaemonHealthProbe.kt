package io.ltirom.tooling.client.wsl

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ltirom.tooling.core.ports.ServerConnectionDescriptor
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds

/** What a shutdown request established; a refusal is an answer, unlike a timeout or a transport error. */
enum class ShutdownOutcome {
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
open class DaemonHealthProbe(
    private val httpClient: HttpClient? = null
) {
    /** [block] within [timeoutMs] (request and body read), or null on timeout or any failure but cancellation. */
    private suspend fun <T> bounded(timeoutMs: Long, block: suspend () -> T?): T? = try {
        withTimeoutOrNull(timeoutMs.milliseconds) { block() }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    open suspend fun isHealthy(info: ServerConnectionDescriptor, timeoutMs: Long = 5000L): Boolean {
        val client = httpClient ?: return false
        return bounded(timeoutMs) { client.get("${info.httpBaseUrl}/api/v1/health").status.isSuccess() } ?: false
    }

    open suspend fun getServerInfo(info: ServerConnectionDescriptor): WslServerInfo? {
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
    open suspend fun requestShutdown(info: ServerConnectionDescriptor, force: Boolean): ShutdownOutcome {
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

    companion object {
        const val SHUTDOWN_TIMEOUT_MS: Long = 5_000L
        const val FORCED_SHUTDOWN_TIMEOUT_MS: Long = 9_000L
        const val TELEMETRY_TIMEOUT_MS: Long = 5_000L
    }
}
