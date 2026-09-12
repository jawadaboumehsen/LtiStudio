package io.ltirom.tooling.client.workspace

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceVirtualizerTest {

    private class FakeCliExecutor : WslCliExecutor() {
        var lastCommand: List<String> = emptyList()
        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?
        ): CliExecutionResult {
            lastCommand = command
            return if (command == listOf("bash", "-c", "echo -n \$HOME")) {
                CliExecutionResult(0, "/home/lti", "")
            } else {
                CliExecutionResult(0, "", "")
            }
        }
    }

    @Test
    fun `allocates workspace mapping ext4 and UNC paths correctly`() {
        val cli = FakeCliExecutor()
        val detector = WslEnvironmentDetector(cli)
        val virtualizer = WorkspaceVirtualizer(distroName = "Ubuntu", cli = cli, detector = detector)

        val descriptor = virtualizer.allocateWorkspace("test-rom-123")

        assertEquals("test-rom-123", descriptor.workspaceId)
        assertEquals("/home/lti/.ltirom/workspaces/test-rom-123", descriptor.linuxExt4Path)
        assertEquals("""\\wsl.localhost\Ubuntu\home\lti\.ltirom\workspaces\test-rom-123""", descriptor.windowsUncPath)
        assertTrue(descriptor.isNativeExt4)
    }
}
