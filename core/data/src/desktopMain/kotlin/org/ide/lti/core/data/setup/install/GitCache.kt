package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import java.security.MessageDigest
import java.util.Locale

public class GitCache(private val cacheBaseDir: String = "~/LtiRomTools/cache/git", private val cli: WslCliExecutor) {
    public companion object {
        public fun sha256(text: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }

        public fun normalizeRepoUrl(repoUrl: String): String =
            org.ide.lti.core.model.setup.ToolSelection.normalizeRepoUrl(repoUrl)

        public fun cacheKey(repoUrl: String): String = sha256(normalizeRepoUrl(repoUrl))
    }

    public fun getMirrorPath(repoUrl: String): String {
        val key = cacheKey(repoUrl)
        return "$cacheBaseDir/$key"
    }

    public fun getLockPath(repoUrl: String): String {
        val key = cacheKey(repoUrl)
        return "$cacheBaseDir/$key.lock"
    }

    public fun syncMirror(distro: String, repoUrl: String): CliExecutionResult {
        val mirrorPath = getMirrorPath(repoUrl)
        val lockPath = getLockPath(repoUrl)

        cli.execute(distro, listOf("mkdir", "-p", cacheBaseDir))

        val check = cli.execute(distro, listOf("test", "-d", mirrorPath))
        val gitCmd = if (check.exitCode == 0) {
            "git -C \"$mirrorPath\" fetch --all --prune --tags"
        } else {
            "git clone --mirror \"$repoUrl\" \"$mirrorPath\""
        }

        val flockCommand = listOf("flock", "-x", lockPath, "-c", gitCmd)
        return cli.execute(distro, flockCommand)
    }

    public fun cloneReference(
        distro: String,
        repoUrl: String,
        targetDir: String,
        ref: String? = null,
    ): CliExecutionResult {
        val mirrorPath = getMirrorPath(repoUrl)
        syncMirror(distro, repoUrl)

        val cloneCmd = mutableListOf("git", "clone", "--reference", mirrorPath, repoUrl, targetDir)
        if (ref != null) {
            cloneCmd.addAll(listOf("--branch", ref))
        }
        return cli.execute(distro, cloneCmd)
    }
}
