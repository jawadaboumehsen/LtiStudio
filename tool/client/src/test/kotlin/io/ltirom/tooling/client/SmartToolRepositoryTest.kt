package io.ltirom.tooling.client

import io.ltirom.tooling.core.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SmartToolRepositoryTest {

    private class RecordingRepository(val label: String) : ToolRepository {
        var callCount: Int = 0
        override suspend fun <R> execute(command: ToolCommand<R>, context: ExecutionContext): R {
            callCount++
            @Suppress("UNCHECKED_CAST")
            return CommandExecutionResult(0, "from-$label", "", 10L) as R
        }
    }

    @Test
    fun `forceRemote delegates to remote repository`() = runBlocking {
        val local = RecordingRepository("local")
        val remote = RecordingRepository("remote")
        val smart = SmartToolRepository(localRepository = local, remoteWslRepository = remote, forceRemote = true)

        assertTrue(smart.isUsingRemote)

        val dummyCommand = RawToolCommand(ToolId.MKE2FS, listOf("-V"))
        val result = smart.execute(dummyCommand, ExecutionContext())

        assertEquals(0, local.callCount)
        assertEquals(1, remote.callCount)
        assertEquals("from-remote", result.stdout)
    }

    @Test
    fun `argument translation correctly handles Windows paths`() {
        val translator = WslPathTranslator()
        val repo = RemoteWslToolRepository(
            daemonManager = WslDaemonManager(),
            pathTranslator = translator
        )

        assertEquals("/mnt/c/roms/system.img", repo.translateArgument("""C:\roms\system.img"""))
        assertEquals("-V", repo.translateArgument("-V"))
        assertEquals("--size=1024", repo.translateArgument("--size=1024"))
    }
}
