package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitCacheTest {

    private class RecordingCli(private val mirrorExists: Boolean = false) : WslCliExecutor() {
        val executedCommands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            executedCommands += command
            return when {
                command.contains("test") && command.contains("-d") -> {
                    CliExecutionResult(if (mirrorExists) 0 else 1, "", "")
                }
                else -> CliExecutionResult(0, "ok", "")
            }
        }
    }

    @Test
    fun `mirror and lock paths derive from sha256 of url`() {
        val cli = RecordingCli()
        val cache = GitCache(cacheBaseDir = "/home/user/LtiRomTools/cache/git", cli = cli)
        val url = "https://android.googlesource.com/platform/system/core"
        val expectedHash = GitCache.cacheKey(url)

        val mirrorPath = cache.getMirrorPath(url)
        val lockPath = cache.getLockPath(url)

        assertEquals("/home/user/LtiRomTools/cache/git/$expectedHash", mirrorPath)
        assertEquals("/home/user/LtiRomTools/cache/git/$expectedHash.lock", lockPath)
    }

    @Test
    fun `syncMirror uses flock on lock file to clone mirror if absent`() = runTest {
        val cli = RecordingCli(mirrorExists = false)
        val cache = GitCache(cacheBaseDir = "/cache", cli = cli)
        val url = "https://github.com/erofs/erofs-utils.git"

        val res = cache.syncMirror("Ubuntu", url)
        assertEquals(0, res.exitCode)

        val flockCmd = cli.executedCommands.firstOrNull { it.firstOrNull() == "flock" }
        assertTrue(flockCmd != null, "Must execute with flock")
        assertEquals("-x", flockCmd[1])
        assertEquals("/cache/${GitCache.cacheKey(url)}.lock", flockCmd[2])
        assertEquals("-c", flockCmd[3])
        assertTrue(flockCmd[4].contains("git clone --mirror"))
    }

    @Test
    fun `syncMirror uses flock on lock file to fetch if mirror exists`() = runTest {
        val cli = RecordingCli(mirrorExists = true)
        val cache = GitCache(cacheBaseDir = "/cache", cli = cli)
        val url = "https://github.com/erofs/erofs-utils.git"

        val res = cache.syncMirror("Ubuntu", url)
        assertEquals(0, res.exitCode)

        val flockCmd = cli.executedCommands.firstOrNull { it.firstOrNull() == "flock" }
        assertTrue(flockCmd != null, "Must execute with flock")
        assertEquals("-x", flockCmd[1])
        assertEquals("/cache/${GitCache.cacheKey(url)}.lock", flockCmd[2])
        assertEquals("-c", flockCmd[3])
        assertTrue(flockCmd[4].contains("git -C") && flockCmd[4].contains("fetch --all"))
    }

    @Test
    fun `cloneReference clones using mirror as reference`() = runTest {
        val cli = RecordingCli(mirrorExists = true)
        val cache = GitCache(cacheBaseDir = "/cache", cli = cli)
        val url = "https://github.com/erofs/erofs-utils.git"
        val target = "/workspace/erofs"

        val res = cache.cloneReference("Ubuntu", url, target, ref = "v1.7")
        assertEquals(0, res.exitCode)

        val cloneCmd = cli.executedCommands.firstOrNull { it.contains("clone") && it.contains("--reference") }
        assertTrue(cloneCmd != null, "Must execute clone with --reference")
        val mirrorPath = cache.getMirrorPath(url)
        val refIndex = cloneCmd.indexOf("--reference")
        assertEquals(mirrorPath, cloneCmd[refIndex + 1])
    }
}
