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

    public fun resolveCacheBase(distro: String): String {
        if (!cacheBaseDir.startsWith("~")) return cacheBaseDir
        val homeRes = cli.execute(distro, listOf("printenv", "HOME"))
        val home = homeRes.output.trim().takeIf { homeRes.exitCode == 0 && it.startsWith("/") }
            ?: throw IllegalStateException("Could not resolve WSL HOME for Git cache in '$distro': ${homeRes.error}")
        return cacheBaseDir.replaceFirst("~", home)
    }

    public fun getMirrorPath(distro: String, repoUrl: String): String {
        val base = resolveCacheBase(distro)
        val key = cacheKey(repoUrl)
        return "$base/$key"
    }

    public fun getMirrorPath(repoUrl: String): String {
        val key = cacheKey(repoUrl)
        return "$cacheBaseDir/$key"
    }

    public fun getLockPath(distro: String, repoUrl: String): String {
        val base = resolveCacheBase(distro)
        val key = cacheKey(repoUrl)
        return "$base/$key.lock"
    }

    public fun getLockPath(repoUrl: String): String {
        val key = cacheKey(repoUrl)
        return "$cacheBaseDir/$key.lock"
    }

    public fun syncMirror(distro: String, repoUrl: String): CliExecutionResult {
        val base = resolveCacheBase(distro)
        val mirrorPath = getMirrorPath(distro, repoUrl)
        val lockPath = getLockPath(distro, repoUrl)

        val mkdir = cli.execute(distro, listOf("mkdir", "-p", base))
        if (mkdir.exitCode != 0) return mkdir

        val check = cli.execute(distro, listOf("test", "-d", mirrorPath))
        val gitCommand = if (check.exitCode == 0) {
            listOf("git", "-C", mirrorPath, "fetch", "--all", "--prune", "--tags")
        } else {
            listOf("git", "clone", "--mirror", repoUrl, mirrorPath)
        }

        val flockCommand = listOf("flock", "-x", lockPath) + gitCommand
        return cli.execute(distro, flockCommand)
    }

    public fun cloneReference(
        distro: String,
        repoUrl: String,
        targetDir: String,
        ref: String? = null,
    ): CliExecutionResult {
        val mirrorPath = getMirrorPath(distro, repoUrl)
        val sync = syncMirror(distro, repoUrl)
        if (sync.exitCode != 0) return sync

        val cloneCmd = mutableListOf("git", "clone", "--reference", mirrorPath, repoUrl, targetDir)
        if (ref != null) {
            cloneCmd.addAll(listOf("--branch", ref))
        }
        return cli.execute(distro, cloneCmd)
    }
}
