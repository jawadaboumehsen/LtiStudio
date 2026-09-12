package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.model.setup.PersistedSubmoduleState

/**
 * Synchronizes the LtiRom project's external tool source repositories into the WSL
 * workspace (`<extDir>/<name>`), so [ToolchainBuildEngine] can compile them.
 *
 * The 5 submodules and their URLs are the real, current entries in `LtiRom/.gitmodules`
 * (`external/apktool`, `external/android-tools`, `external/signapk`,
 * `external/erofs-utils`, `external/img2sdat`) — this is not a guessed list.
 */
public class SubmoduleSyncEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
) {
    public data class SubmoduleSpec(val name: String, val url: String)

    public companion object {
        public val SUBMODULES: List<SubmoduleSpec> = listOf(
            SubmoduleSpec("apktool", "https://github.com/iBotPeaches/Apktool.git"),
            SubmoduleSpec("android-tools", "https://github.com/UN1CA/external_android-tools.git"),
            SubmoduleSpec("signapk", "https://github.com/UN1CA/external_signapk.git"),
            SubmoduleSpec("erofs-utils", "https://github.com/sekaiacg/erofs-utils.git"),
            SubmoduleSpec("img2sdat", "https://github.com/UN1CA/external_img2sdat.git"),
        )
    }

    public suspend fun syncAll(
        distro: String,
        extDir: String,
        onProgress: (String) -> Unit,
    ): Result<Unit> {
        for (spec in SUBMODULES) {
            val repoDir = "$extDir/${spec.name}"
            val exists = cli.execute(distro, listOf("test", "-d", "$repoDir/.git")).exitCode == 0

            if (exists) {
                onProgress("Updating ${spec.name}...")
                val fetch = cli.execute(
                    distro,
                    listOf("git", "-C", repoDir, "fetch", "--depth", "1", "origin"),
                    timeoutSeconds = 120,
                )
                if (fetch.exitCode != 0) {
                    return Result.failure(IllegalStateException("Failed to fetch ${spec.name}: ${fetch.error.take(200)}"))
                }
                cli.execute(distro, listOf("git", "-C", repoDir, "submodule", "update", "--init", "--recursive"), timeoutSeconds = 180)
            } else {
                onProgress("Cloning ${spec.name}...")
                val clone = cli.execute(
                    distro,
                    listOf("git", "clone", "--recurse-submodules", "--depth", "1", spec.url, repoDir),
                    timeoutSeconds = 300,
                )
                if (clone.exitCode != 0) {
                    return Result.failure(IllegalStateException("Failed to clone ${spec.name}: ${clone.error.take(200)}"))
                }
            }

            val commitResult = cli.execute(distro, listOf("git", "-C", repoDir, "rev-parse", "--short", "HEAD"))
            val commit = commitResult.output.trim().ifBlank { null }

            repository?.updateSubmodule(spec.name) { current: PersistedSubmoduleState ->
                current.copy(isSynced = true, commit = commit)
            }
            onProgress("${spec.name} ready${commit?.let { " ($it)" } ?: ""}.")
        }

        return Result.success(Unit)
    }
}
