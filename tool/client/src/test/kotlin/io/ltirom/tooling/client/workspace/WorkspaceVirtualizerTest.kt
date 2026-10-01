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
            return if (command == listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"") ||
                command == listOf("bash", "-c", "echo -n \$HOME")) {
                CliExecutionResult(0, "lti\n/home/lti", "")
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

    @Test
    fun `allocates workspace with explicit SetupEnvironment`() {
        val cli = FakeCliExecutor()
        val virtualizer = WorkspaceVirtualizer(cli = cli)
        val env = org.ide.lti.core.model.setup.SetupEnvironment(
            distro = "Ubuntu-24.04",
            wslVersion = 2,
            osId = "ubuntu",
            osVersionId = "24.04",
            user = "developer",
            home = "/home/developer"
        )

        val descriptor = virtualizer.allocateWorkspace("test-rom-456", env)

        assertEquals("test-rom-456", descriptor.workspaceId)
        assertEquals("/home/developer/.ltirom/workspaces/test-rom-456", descriptor.linuxExt4Path)
        assertEquals("""\\wsl.localhost\Ubuntu-24.04\home\developer\.ltirom\workspaces\test-rom-456""", descriptor.windowsUncPath)
        assertTrue(descriptor.isNativeExt4)
    }
}
