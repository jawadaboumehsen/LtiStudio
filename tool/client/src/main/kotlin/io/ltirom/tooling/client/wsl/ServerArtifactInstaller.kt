package io.ltirom.tooling.client.wsl

import io.ltirom.tooling.client.WslPathTranslator
import org.ide.lti.core.model.setup.InstallState
import org.ide.lti.core.model.setup.ServerArtifact
import org.ide.lti.core.model.setup.SetupEnvironment
import java.io.File

/**
 * Installs the build service bundled with the app into `~/.ltirom/server` of the selected distro.
 *
 * Layout (data-model `ServerArtifact`):
 * ```
 * versions/<v>.partial/   staged copy, never activated
 * versions/<v>/           verified copy
 * current -> versions/<v> activated by one atomic link swap
 * ```
 * The previous version stays active until the swap succeeds, and the version the `current` link
 * points to is never deleted. The bundle is `<resources>/ltirom-server/` containing
 * `server-version.txt` and `checksums.txt` (written by `lti-desktop:stageBundledServer`).
 */
public class ServerArtifactInstaller(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val bundleDirProvider: () -> File? = {
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it, BUNDLE_DIR_NAME) }
            ?.takeIf { it.isDirectory }
    },
    private val pathTranslator: WslPathTranslator = WslPathTranslator(),
) {
    public fun getBundledVersion(): String? {
        val bundle = bundleDirProvider() ?: return null
        val verFile = File(bundle, VERSION_FILE)
        return if (verFile.isFile) verFile.readText().trim().ifBlank { null } else null
    }

    public fun getActiveVersion(environment: SetupEnvironment): String? {
        val linkResult = cli.execute(environment.distro, listOf("readlink", "-f", currentLink(environment)))
        return if (linkResult.exitCode == 0 && linkResult.output.isNotBlank()) {
            linkResult.output.trim().substringAfterLast("/").ifBlank { null }
        } else {
            null
        }
    }

    public fun status(environment: SetupEnvironment): ServerArtifact {
        val bundled = getBundledVersion()
        val active = getActiveVersion(environment)
        val state = when {
            active == null -> InstallState.NotInstalled
            bundled == null || active == bundled -> InstallState.Current
            else -> InstallState.Outdated
        }
        return artifact(bundled, active, state)
    }

    /**
     * Installs the bundled version if it is missing or damaged. Old versions are pruned afterwards, except
     * the new one, the one it replaced and [keepVersions] (the version a still-running service uses); with
     * [prune] false nothing is removed (used when the running version cannot be determined).
     */
    public fun ensureInstalled(
        environment: SetupEnvironment,
        keepVersions: Set<String> = emptySet(),
        prune: Boolean = true,
    ): ServerArtifact {
        val bundle = bundleDirProvider()
        val bundled = getBundledVersion()
        val activeBefore = getActiveVersion(environment)

        if (bundle == null || bundled == null) {
            return artifact(
                null,
                activeBefore,
                InstallState.Failed("bundle", "The build service is missing from this app installation."),
            )
        }

        val serverBase = "${environment.home}/.ltirom/server"
        if (activeBefore == bundled && isIntact(environment, currentLink(environment))) {
            return artifact(bundled, bundled, InstallState.Current)
        }

        val versionsDir = "$serverBase/versions"
        val partialDir = "$versionsDir/$bundled.partial"
        val finalDir = "$versionsDir/$bundled"
        val tmpLink = "$serverBase/current.tmp"
        val source = pathTranslator.toWslPath(bundle.absolutePath)

        val steps = listOf(
            "cleanup" to listOf("rm", "-rf", partialDir, tmpLink),
            "mkdir" to listOf("mkdir", "-p", versionsDir),
            "copy" to listOf("cp", "-r", source, partialDir),
            "chmod" to listOf("sh", "-c", "chmod +x '$partialDir'/bin/*"),
            "verify" to listOf("sh", "-c", "cd '$partialDir' && sha256sum --quiet -c $CHECKSUMS_FILE"),
            "verify" to listOf("test", "-x", "$partialDir/bin/ltirom-server"),
            // finalDir is the active target only when that install is damaged (checked above). It is moved
            // aside, never deleted, until the verified copy has replaced it.
            "stage" to listOf("sh", "-c", "rm -rf '$finalDir.old' && if [ -e '$finalDir' ]; then mv '$finalDir' '$finalDir.old'; fi"),
            "stage" to listOf("mv", partialDir, finalDir),
            "activate" to listOf("ln", "-sfn", finalDir, tmpLink),
            "activate" to listOf("mv", "-T", tmpLink, currentLink(environment)),
        )
        for ((step, command) in steps) {
            // Copying and hashing the bundle across the 9P mount takes far longer than the 10 s default.
            val result = cli.execute(environment.distro, command, timeoutSeconds = STEP_TIMEOUT_SECONDS)
            if (result.exitCode != 0) {
                // Put a set-aside install back so `current` keeps pointing at something (best effort).
                cli.execute(
                    environment.distro,
                    listOf("sh", "-c", "if [ ! -e '$finalDir' ] && [ -e '$finalDir.old' ]; then mv '$finalDir.old' '$finalDir'; fi"),
                )
                val detail = result.error.ifBlank { result.output }.ifBlank { "exit code ${result.exitCode}" }
                return artifact(bundled, activeBefore, InstallState.Failed(step, detail))
            }
        }
        cli.execute(environment.distro, listOf("rm", "-rf", "$finalDir.old"))

        if (prune) {
            // Keep the new version, the one it replaced and any version a service still runs from.
            val keep = (listOfNotNull(bundled, activeBefore) + keepVersions).distinct()
                .joinToString(" ") { "! -name '$it'" }
            cli.execute(
                environment.distro,
                listOf("sh", "-c", "find '$versionsDir' -mindepth 1 -maxdepth 1 $keep -exec rm -rf {} +"),
            )
        }
        return artifact(bundled, bundled, InstallState.Current)
    }

    private fun ok(environment: SetupEnvironment, command: List<String>): Boolean =
        cli.execute(environment.distro, command, timeoutSeconds = STEP_TIMEOUT_SECONDS).exitCode == 0

    /** The launcher is executable and every bundled file (JARs included) still matches its checksum. */
    private fun isIntact(environment: SetupEnvironment, dir: String): Boolean =
        ok(environment, listOf("test", "-x", "$dir/bin/ltirom-server")) &&
            ok(environment, listOf("sh", "-c", "cd '$dir' && sha256sum --quiet -c $CHECKSUMS_FILE"))

    private fun currentLink(environment: SetupEnvironment): String = "${environment.home}/.ltirom/server/current"

    private fun artifact(bundled: String?, active: String?, state: InstallState): ServerArtifact = ServerArtifact(
        bundledVersion = bundled,
        activeVersion = active,
        runningVersion = null,
        runningActiveRuns = 0,
        installState = state,
        javaHome = null,
    )

    public companion object {
        public const val BUNDLE_DIR_NAME: String = "ltirom-server"
        public const val VERSION_FILE: String = "server-version.txt"
        public const val CHECKSUMS_FILE: String = "checksums.txt"
        private const val STEP_TIMEOUT_SECONDS: Long = 180
    }
}
