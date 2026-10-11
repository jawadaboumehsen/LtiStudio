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

    /**
     * The WSL version of each distribution `wsl -l -v` lists. A distribution whose row cannot be read, or a
     * failed query, is simply absent: an unknown version is never reported as a number.
     */
    public fun listDistrosWithVersions(): Map<String, Int> =
        listVerboseRows().mapNotNull { row -> row.version?.let { row.name to it } }.toMap()

    private class DistroRow(val name: String, val isDefault: Boolean, val version: Int?)

    /**
     * Rows of `wsl -l -v`: `[*] NAME  STATE  VERSION`. A name may contain spaces, so the state and version are
     * taken from the right and everything before them is the name.
     */
    private fun listVerboseRows(): List<DistroRow> {
        val res = cli.executeHost(listOf("wsl.exe", "-l", "-v"), timeoutSeconds = 5, charset = Charsets.UTF_16LE)
        if (res.exitCode != 0) return emptyList()
        return res.output.lines().mapNotNull { line ->
            val clean = line.replace("\u0000", "").trim()
            if (clean.isBlank() || clean.startsWith("NAME", ignoreCase = true)) return@mapNotNull null
            val match = VERBOSE_ROW.matchEntire(clean) ?: return@mapNotNull null
            DistroRow(
                name = match.groupValues[2].trim(),
                isDefault = match.groupValues[1].isNotEmpty(),
                version = match.groupValues[4].toIntOrNull(),
            )
        }
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
                val value =
                    trimmed.substring(eqIdx + 1).trim()
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
            val detail =
                hostCheck.error.ifBlank {
                    hostCheck.output
                }.ifBlank { "wsl.exe is not available or returned an error." }
            return DistroStatus.WslUnavailable(detail)
        }

        val distros =
            hostCheck.output.lines()
                .map { it.trim().replace("\u0000", "") }
                .filter { it.isNotBlank() }

        if (distros.isEmpty()) {
            return DistroStatus.NoDistro
        }

        val defaultDistro = getDefaultDistro()
        val targetDistro = distro ?: defaultDistro
        if (targetDistro == null) {
            return DistroStatus.NoDistro
        }
        if (distro == null && defaultDistro != null) {
            // When auto-detecting, the default distro must be Ubuntu LTS
            if (targetDistro !in distros) {
                return DistroStatus.Unsupported(
                    targetDistro,
                    "Default distribution '$targetDistro' is not installed in WSL. Set Ubuntu as default: wsl --set-default Ubuntu-24.04",
                )
            }
        }
        if (distro != null && targetDistro !in distros) {
            return DistroStatus.Unsupported(targetDistro, "Distribution '$targetDistro' is not installed in WSL.")
        }

        val wslVersion = listDistrosWithVersions()[targetDistro]
            ?: return DistroStatus.StartupFailed(
                targetDistro,
                "Could not determine whether '$targetDistro' runs on WSL 2 (wsl.exe -l -v gave no readable row for it).",
            )
        if (wslVersion != 2) {
            return DistroStatus.Unsupported(targetDistro, "requires WSL 2, found version $wslVersion")
        }

        // First contact may cold-boot the distro, which can take well over 5 s.
        val osReleaseRes =
            cli.execute(
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
            val guidance =
                if (distro == null) {
                    "Default distribution '$targetDistro' is $id $versionId, not Ubuntu LTS. Set an Ubuntu LTS distribution as default: wsl --set-default <Ubuntu-Distro>"
                } else {
                    "found $id $versionId"
                }
            return DistroStatus.Unsupported(targetDistro, guidance)
        }

        val userRes =
            cli.execute(
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
        val distros =
            hostCheck.output.lines()
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

    /**
     * Whether what was verified for [environment] still holds, from one `wsl -l -v` (no distro boot): it is still
     * installed, still WSL 2 and - when [requireDefault] - still the default. The OS and user, which cost a boot
     * to read, are what a cached verification is allowed to keep.
     */
    public fun stillCurrent(environment: SetupEnvironment, requireDefault: Boolean): Boolean {
        val row = listVerboseRows().firstOrNull { it.name == environment.distro } ?: return false
        return row.version == 2 && (!requireDefault || row.isDefault)
    }

    /** The distro WSL marks as default (`*` in `wsl -l -v`); null when WSL reports none. Never guessed. */
    public fun getDefaultDistro(): String? = listVerboseRows().firstOrNull { it.isDefault }?.name

    /**
     * Sets the default WSL distribution via `wsl.exe --set-default <name>`.
     */
    public fun setDefaultDistro(name: String): Result<Unit> = runCatching {
        val res = cli.executeHost(listOf("wsl.exe", "--set-default", name), timeoutSeconds = 10)
        if (res.exitCode != 0) {
            val err = res.error.ifBlank { res.output }.ifBlank { "Exit code ${res.exitCode}" }
            error("Failed to set default distro to '$name': $err")
        }
    }

    private fun parseOsReleaseOutput(output: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (line in output.lines()) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) continue
            val eqIdx = trimmed.indexOf('=')
            if (eqIdx > 0) {
                val key = trimmed.substring(0, eqIdx).trim()
                val value =
                    trimmed.substring(eqIdx + 1).trim()
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
        val VERBOSE_ROW = Regex("""^(\*?)\s*(.+?)\s+(\S+)\s+(\d+)$""")
    }
}
