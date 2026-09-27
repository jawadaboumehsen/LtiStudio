package io.ltirom.tooling.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ltirom.tooling.core.ports.PathTranslatorPort
import io.ltirom.tooling.core.remote.PathTranslationDirection
import io.ltirom.tooling.core.remote.PathTranslationRequest
import io.ltirom.tooling.core.remote.PathTranslationResponse
import org.ide.lti.core.model.setup.SetupEnvironment

import java.util.Collections
import java.util.LinkedHashMap

public class WslPathTranslator(
    private val distroName: String? = null,
    private val httpClient: HttpClient? = null,
    private val serverBaseUrl: String? = null,
    private val authToken: String? = null
) : PathTranslatorPort {

    override fun toRemote(localPath: String): String = toWslPath(localPath)
    override fun toLocal(remotePath: String): String = toWindowsPath(remotePath)
    private val drivePattern = Regex("""^([a-zA-Z]):[/\\]?(.*)$""")
    private val wslUncPattern = Regex("""^\\\\(?:wsl\$|wsl\.localhost)\\[^\\]+[/\\]?(.*)$""", RegexOption.IGNORE_CASE)
    private val mntPattern = Regex("""^/mnt/([a-zA-Z])(?:/(.*))?$""")

    private val toWslCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2048
        }
    )

    private val toWinCache: MutableMap<String, String> = Collections.synchronizedMap(
        object : LinkedHashMap<String, String>(256, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 2048
        }
    )

    public fun toWslPath(windowsPath: String): String {
        if (windowsPath.isBlank()) return windowsPath
        return toWslCache.computeIfAbsent(windowsPath) { computeToWslPath(it) }
    }

    private fun computeToWslPath(windowsPath: String): String {
        val normalized = windowsPath.trim()

        // Handle WSL UNC paths e.g. \\wsl.localhost\Ubuntu\home\user
        val uncMatch = wslUncPattern.matchEntire(normalized)
        if (uncMatch != null) {
            val rest = uncMatch.groupValues[1].replace('\\', '/')
            return if (rest.startsWith("/")) rest else "/$rest"
        }

        // Handle standard Windows drive letters C:\path or C:/path
        val driveMatch = drivePattern.matchEntire(normalized)
        if (driveMatch != null) {
            val drive = driveMatch.groupValues[1].lowercase()
            val rest = driveMatch.groupValues[2].replace('\\', '/')
            return if (rest.isEmpty()) "/mnt/$drive" else "/mnt/$drive/$rest"
        }

        // Already a Linux path
        if (normalized.startsWith("/")) {
            return normalized
        }

        // Relative path: preserve relative path with forward slashes
        return normalized.replace('\\', '/')
    }

    public fun toWindowsPath(environment: SetupEnvironment, wslPath: String): String =
        toWindowsPath(wslPath, environment.distro)

    public fun toWindowsPath(wslPath: String, distro: String): String {
        if (wslPath.isBlank()) return wslPath
        val key = "$distro:$wslPath"
        return toWinCache.computeIfAbsent(key) { computeToWindowsPath(wslPath, distro) }
    }

    public fun toWindowsPath(wslPath: String): String {
        val distro = distroName ?: error("distroName not configured in WslPathTranslator and no distro specified")
        return toWindowsPath(wslPath, distro)
    }

    private fun computeToWindowsPath(wslPath: String, distro: String): String {
        val normalized = wslPath.trim()

        // Handle /mnt/<drive>/...
        val mntMatch = mntPattern.matchEntire(normalized)
        if (mntMatch != null) {
            val drive = mntMatch.groupValues[1].uppercase()
            val rest = mntMatch.groupValues[2].replace('/', '\\')
            return if (rest.isEmpty()) "$drive:\\" else "$drive:\\$rest"
        }

        // Handle Linux internal paths /home/..., /tmp/..., etc.
        if (normalized.startsWith("/")) {
            val linuxRel = normalized.removePrefix("/").replace('/', '\\')
            return "\\\\wsl.localhost\\$distro\\$linuxRel"
        }

        // Relative path
        return normalized.replace('/', '\\')
    }

    override suspend fun translateRemote(path: String, direction: PathTranslationDirection): String {
        val client = httpClient
        val baseUrl = serverBaseUrl
        val token = authToken
        if (client == null || baseUrl == null || token == null) {
            return if (direction == PathTranslationDirection.WINDOWS_TO_WSL) toWslPath(path) else toWindowsPath(path)
        }

        return try {
            val response = client.post("$baseUrl/api/v1/path/translate") {
                header(HttpHeaders.Authorization, "Bearer $token")
                contentType(ContentType.Application.Json)
                setBody(PathTranslationRequest(path = path, direction = direction))
            }
            if (response.status.isSuccess()) {
                response.body<PathTranslationResponse>().translatedPath
            } else {
                if (direction == PathTranslationDirection.WINDOWS_TO_WSL) toWslPath(path) else toWindowsPath(path)
            }
        } catch (_: Exception) {
            if (direction == PathTranslationDirection.WINDOWS_TO_WSL) toWslPath(path) else toWindowsPath(path)
        }
    }
}
