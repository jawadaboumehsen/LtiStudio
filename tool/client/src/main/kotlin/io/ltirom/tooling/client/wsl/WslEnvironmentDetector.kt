package io.ltirom.tooling.client.wsl

import org.ide.lti.core.model.setup.DistroStatus
import org.ide.lti.core.model.setup.SetupEnvironment

/**
 * Single Responsibility: Inspection and classification of the WSL host environment
 * and installed distributions.
 */
public class WslEnvironmentDetector(private val cli: WslCliExecutor = WslCliExecutor()) {
    public fun listDistros(): List<String> {
        val res = cli.executeHost(listOf("wsl.exe", "-l", "-q"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (res.exitCode != 0) return emptyList()
        return res.output.lines()
            .map { it.trim().replace("\u0000", "") }
            .filter { it.isNotBlank() }
    }

    public fun listDistrosWithVersions(): Map<String, Int> {
        val res = cli.executeHost(listOf("wsl.exe", "-l", "-v"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (res.exitCode != 0) return emptyMap()
        val result = mutableMapOf<String, Int>()
        for (line in res.output.lines()) {
            val clean = line.trim().replace("\u0000", "")
            if (clean.isBlank() || clean.startsWith("NAME", ignoreCase = true)) continue
            val parts = clean.split(Regex("""\s+""")).filter { it.isNotBlank() }
            if (parts.isEmpty()) continue
            val name = if (parts.first() == "*") parts.getOrNull(1) else parts.first()
            val versionStr = parts.lastOrNull()
            if (name != null && versionStr != null) {
                val version = versionStr.toIntOrNull() ?: 2
                result[name] = version
            }
        }
        return result
    }

    public fun readOsRelease(distro: String): Map<String, String> {
        val res = cli.execute(distro, listOf("cat", "/etc/os-release"), timeoutSeconds = 5)
        if (res.exitCode != 0) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (line in res.output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                result[key] = value
            }
        }
        return result
    }

    public fun readUser(distro: String): Pair<String, String>? {
        val res = cli.execute(distro, listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\""), timeoutSeconds = 5)
        if (res.exitCode != 0) return null
        val lines = res.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val user = lines.firstOrNull() ?: return null
        val home = if (lines.size > 1) lines[1] else ""
        return user to home
    }

    public fun classify(distro: String? = null): DistroStatus {
        val hostCheck = cli.executeHost(listOf("wsl.exe", "-l", "-q"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (hostCheck.exitCode != 0) {
            val detail = hostCheck.error.ifBlank {
                hostCheck.output
            }.ifBlank { "wsl.exe is not available or returned an error." }
            return DistroStatus.WslUnavailable(detail)
        }

        val distros = hostCheck.output.lines()
            .map { it.trim().replace("\u0000", "") }
            .filter { it.isNotBlank() }

        if (distros.isEmpty()) {
            return DistroStatus.NoDistro
        }

        val targetDistro = distro ?: getDefaultDistro() ?: distros.first()
        if (distro != null && targetDistro !in distros) {
            return DistroStatus.Unsupported(targetDistro, "Distribution '$targetDistro' is not installed in WSL.")
        }

        val versions = listDistrosWithVersions()
        val wslVersion = versions[targetDistro] ?: 2
        if (wslVersion != 2) {
            return DistroStatus.Unsupported(targetDistro, "requires WSL 2, found version $wslVersion")
        }

        // First contact may cold-boot the distro, which can take well over 5 s.
        val osReleaseRes = cli.execute(
            targetDistro,
            listOf("cat", "/etc/os-release"),
            timeoutSeconds = DISTRO_BOOT_TIMEOUT_SECONDS,
        )
        if (osReleaseRes.exitCode == -1 || osReleaseRes.error.contains("timed out", ignoreCase = true)) {
            return DistroStatus.StartupFailed(targetDistro, osReleaseRes.error)
        }
        if (osReleaseRes.exitCode != 0) {
            return DistroStatus.StartupFailed(
                targetDistro,
                osReleaseRes.error.ifBlank { "Failed to read /etc/os-release (exit code ${osReleaseRes.exitCode})" },
            )
        }

        val osRelease = parseOsReleaseOutput(osReleaseRes.output)
        val id = osRelease["ID"]?.lowercase() ?: ""
        val versionId = osRelease["VERSION_ID"] ?: ""
        val version = osRelease["VERSION"] ?: ""

        val isUbuntu = id == "ubuntu"
        val isLts = isSupportedLts(versionId, version)

        if (!isUbuntu || !isLts) {
            return DistroStatus.Unsupported(targetDistro, "found $id $versionId")
        }

        val userRes = cli.execute(
            targetDistro,
            listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\""),
            timeoutSeconds = 5,
        )
        if (userRes.exitCode == -1 || userRes.error.contains("timed out", ignoreCase = true)) {
            return DistroStatus.StartupFailed(targetDistro, userRes.error)
        }
        if (userRes.exitCode != 0) {
            return DistroStatus.StartupFailed(
                targetDistro,
                userRes.error.ifBlank { "Failed to resolve default user (exit code ${userRes.exitCode})" },
            )
        }

        val userLines = userRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val user = userLines.firstOrNull() ?: ""
        val home = if (userLines.size > 1) userLines[1] else ""

        if (user == "root") {
            return DistroStatus.NoUsableUser(targetDistro, "default user is root")
        }
        if (user.isBlank() || home.isBlank() || !home.startsWith("/")) {
            return DistroStatus.NoUsableUser(targetDistro, "no valid home directory found")
        }

        return DistroStatus.Usable(
            SetupEnvironment(
                distro = targetDistro,
                wslVersion = 2,
                osId = id,
                osVersionId = versionId,
                user = user,
                home = home,
            ),
        )
    }

    public fun detectAll(): List<DistroStatus> {
        val hostCheck = cli.executeHost(listOf("wsl.exe", "-l", "-q"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (hostCheck.exitCode != 0) {
            return listOf(DistroStatus.WslUnavailable(hostCheck.error.ifBlank { "wsl.exe is not available." }))
        }
        val distros = hostCheck.output.lines()
            .map { it.trim().replace("\u0000", "") }
            .filter { it.isNotBlank() }
        if (distros.isEmpty()) {
            return listOf(DistroStatus.NoDistro)
        }
        return distros.map { classify(it) }
    }

    public fun resolveWslIp(distro: String): String {
        val res = cli.execute(distro, listOf("hostname", "-I"), timeoutSeconds = 5)
        val ip = res.output.split(Regex("\\s+")).firstOrNull()?.trim()
        return if (!ip.isNullOrBlank() && ip.matches(Regex("""^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$"""))) {
            ip
        } else {
            "127.0.0.1"
        }
    }

    public fun resolveWslUserHome(distro: String): String {
        val userRes = cli.execute(distro, listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\""), timeoutSeconds = 5)
        if (userRes.exitCode != 0) {
            error("Failed to resolve user home in '$distro': ${userRes.error}")
        }
        val lines = userRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        val home = if (lines.size > 1) lines[1] else ""
        if (home.isNotBlank() && home.startsWith("/")) {
            return home
        }
        error("No usable home directory found in '$distro'")
    }

    public fun isWslInstalled(): Boolean = try {
        val res = cli.executeHost(listOf("wsl.exe", "-l", "-q"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        res.exitCode == 0
    } catch (_: Exception) {
        false
    }

    /** The distro WSL marks as default (`*` in `wsl -l -v`); null when WSL reports none. Never guessed. */
    public fun getDefaultDistro(): String? {
        val res = cli.executeHost(listOf("wsl.exe", "-l", "-v"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (res.exitCode != 0) return null
        return res.output.lines()
            .map { it.replace("\u0000", "").trim() }
            .firstOrNull { it.startsWith("*") }
            ?.removePrefix("*")?.trim()
            ?.split(Regex("""\s+"""))?.firstOrNull()
    }

    private fun parseOsReleaseOutput(output: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value = trimmed.substring(eqIdx + 1).trim()
                    .removeSurrounding("\"")
                    .removeSurrounding("'")
                result[key] = value
            }
        }
        return result
    }

    private fun isSupportedLts(versionId: String, version: String): Boolean {
        if (versionId in setOf("22.04", "24.04")) return true
        val match = Regex("""^(\d{2})\.04.*""").matchEntire(versionId) ?: return false
        val yy = match.groupValues[1].toIntOrNull() ?: 0
        return yy >= 26 && yy % 2 == 0 && version.contains("LTS")
    }

    private companion object {
        const val DISTRO_BOOT_TIMEOUT_SECONDS = 30L
    }
}
