package io.ltirom.tooling.client.wsl

import org.ide.lti.core.model.setup.DistroStatus
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WslEnvironmentDetectorTest {
    private class FakeWslCliExecutor : WslCliExecutor() {
        var hostResponses = mutableMapOf<List<String>, CliExecutionResult>()
        var distroResponses = mutableMapOf<Pair<String, List<String>>, CliExecutionResult>()
        var defaultHostResponse = CliExecutionResult(0, "", "")
        var defaultDistroResponse = CliExecutionResult(0, "", "")

        override fun executeHost(command: List<String>, timeoutSeconds: Long, charset: Charset): CliExecutionResult =
            hostResponses[command] ?: defaultHostResponse

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult = distroResponses[distro to command] ?: defaultDistroResponse
    }

    @Test
    fun `no wsl exe gives WslUnavailable`() {
        val cli =
            FakeWslCliExecutor().apply {
                defaultHostResponse =
                    CliExecutionResult(
                        exitCode = -1,
                        output = "",
                        error = "wsl.exe: command not found",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify()
        assertTrue(status is DistroStatus.WslUnavailable, "Expected WslUnavailable but got $status")
    }

    @Test
    fun `empty list output gives NoDistro even if status exits 0`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "\n\n", "")
                hostResponses[listOf("wsl.exe", "--status")] =
                    CliExecutionResult(0, "Default Distribution: none\nDefault Version: 2", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME      STATE      VERSION\n", "")
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify()
        assertEquals(DistroStatus.NoDistro, status)
    }

    @Test
    fun `timeout during distro startup gives StartupFailed not NoUsableUser`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu-24.04\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME            STATE           VERSION\n* Ubuntu-24.04    Stopped         2\n", "")
                // Distro command times out
                defaultDistroResponse =
                    CliExecutionResult(
                        exitCode = -1,
                        output = "",
                        error = "Command timed out after 10 seconds",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Ubuntu-24.04")
        assertTrue(status is DistroStatus.StartupFailed, "Expected StartupFailed on timeout but got $status")
        assertEquals("Ubuntu-24.04", status.distro)
    }

    @Test
    fun `WSL version 1 gives Unsupported`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu-24.04\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME            STATE           VERSION\n* Ubuntu-24.04    Running         1\n", "")
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Ubuntu-24.04")
        assertTrue(status is DistroStatus.Unsupported, "Expected Unsupported for WSL version 1, got $status")
        assertTrue(status.reason.contains("version 1") || status.reason.contains("WSL 2"))
    }

    @Test
    fun `non-Ubuntu distro ID=debian gives Unsupported`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Debian\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME      STATE      VERSION\n* Debian    Running    2\n", "")
                distroResponses["Debian" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "ID=debian\nVERSION_ID=\"12\"\nVERSION=\"12 (bookworm)\"\n",
                        error = "",
                    )
                distroResponses["Debian" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "user\n/home/user",
                        error = "",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Debian")
        assertTrue(status is DistroStatus.Unsupported, "Expected Unsupported for ID=debian, got $status")
        assertTrue(status.reason.contains("debian"))
    }

    @Test
    fun `non-LTS Ubuntu version gives Unsupported`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu-24.10\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME            STATE           VERSION\n* Ubuntu-24.10    Running         2\n", "")
                distroResponses["Ubuntu-24.10" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "ID=ubuntu\nVERSION_ID=\"24.10\"\nVERSION=\"24.10 (Oracular Oriole)\"\n",
                        error = "",
                    )
                distroResponses["Ubuntu-24.10" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "user\n/home/user",
                        error = "",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Ubuntu-24.10")
        assertTrue(status is DistroStatus.Unsupported, "Expected Unsupported for 24.10 non-LTS, got $status")
        assertTrue(status.reason.contains("24.10"))
    }

    @Test
    fun `supported LTS releases 22_04, 24_04, and future 26_04 LTS are supported`() {
        for ((verId, verStr) in listOf(
            "22.04" to "22.04.4 LTS (Jammy Jellyfish)",
            "24.04" to "24.04 LTS (Noble Numbat)",
            "26.04" to "26.04 LTS (Future Numbat)",
        )) {
            val distro = "Ubuntu-$verId"
            val cli =
                FakeWslCliExecutor().apply {
                    hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "$distro\n", "")
                    hostResponses[listOf("wsl.exe", "-l", "-v")] =
                        CliExecutionResult(0, "  NAME            STATE           VERSION\n* $distro    Running         2\n", "")
                    distroResponses[distro to listOf("cat", "/etc/os-release")] =
                        CliExecutionResult(
                            exitCode = 0,
                            output = "ID=ubuntu\nVERSION_ID=\"$verId\"\nVERSION=\"$verStr\"\n",
                            error = "",
                        )
                    distroResponses[distro to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                        CliExecutionResult(
                            exitCode = 0,
                            output = "developer\n/home/developer",
                            error = "",
                        )
                }
            val detector = WslEnvironmentDetector(cli)
            val status = detector.classify(distro)
            assertTrue(status is DistroStatus.Usable, "Expected Usable for Ubuntu $verId ($verStr), got $status")
            val env = status.environment
            assertEquals(distro, env.distro)
            assertEquals(2, env.wslVersion)
            assertEquals("ubuntu", env.osId)
            assertEquals(verId, env.osVersionId)
            assertEquals("developer", env.user)
            assertEquals("/home/developer", env.home)
        }
    }

    @Test
    fun `root default user gives NoUsableUser`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu-24.04\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME            STATE           VERSION\n* Ubuntu-24.04    Running         2\n", "")
                distroResponses["Ubuntu-24.04" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "ID=ubuntu\nVERSION_ID=\"24.04\"\nVERSION=\"24.04 LTS\"\n",
                        error = "",
                    )
                distroResponses["Ubuntu-24.04" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "root\n/root",
                        error = "",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Ubuntu-24.04")
        assertTrue(status is DistroStatus.NoUsableUser, "Expected NoUsableUser for root user, got $status")
        assertEquals("Ubuntu-24.04", status.distro)
    }

    @Test
    fun `normal user gives Usable with parsed home`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu\n", "")
                hostResponses[listOf("wsl.exe", "-l", "-v")] =
                    CliExecutionResult(0, "  NAME      STATE      VERSION\n* Ubuntu    Running    2\n", "")
                distroResponses["Ubuntu" to listOf("cat", "/etc/os-release")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "ID=ubuntu\nVERSION_ID=\"24.04\"\nVERSION=\"24.04 LTS\"\n",
                        error = "",
                    )
                distroResponses["Ubuntu" to listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")] =
                    CliExecutionResult(
                        exitCode = 0,
                        output = "android-dev\n/home/android-dev",
                        error = "",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val status = detector.classify("Ubuntu")
        assertTrue(status is DistroStatus.Usable, "Expected Usable for normal user, got $status")
        val env = status.environment
        assertEquals("Ubuntu", env.distro)
        assertEquals(2, env.wslVersion)
        assertEquals("ubuntu", env.osId)
        assertEquals("24.04", env.osVersionId)
        assertEquals("android-dev", env.user)
        assertEquals("/home/android-dev", env.home)
    }

    private val userCommand = listOf("sh", "-c", "id -un; printf '%s' \"\$HOME\"")

    private fun spacedNameCli(listing: CliExecutionResult) = FakeWslCliExecutor().apply {
        hostResponses[listOf("wsl.exe", "-l", "-q")] = CliExecutionResult(0, "Ubuntu Test\nDebian\n", "")
        hostResponses[listOf("wsl.exe", "-l", "-v")] = listing
        distroResponses["Ubuntu Test" to listOf("cat", "/etc/os-release")] =
            CliExecutionResult(0, "ID=ubuntu\nVERSION_ID=\"24.04\"\nVERSION=\"24.04 LTS\"\n", "")
        distroResponses["Ubuntu Test" to userCommand] = CliExecutionResult(0, "dev\n/home/dev", "")
    }

    private val spacedListing =
        CliExecutionResult(
            0,
            "  NAME           STATE           VERSION\n" +
                "* Ubuntu Test    Running         2\n" +
                "  Debian         Stopped         2\n",
            "",
        )

    @Test
    fun `a distribution name with spaces is kept whole as the default and in the versions`() {
        val detector = WslEnvironmentDetector(spacedNameCli(spacedListing))

        assertEquals("Ubuntu Test", detector.getDefaultDistro())
        assertEquals(mapOf("Ubuntu Test" to 2, "Debian" to 2), detector.listDistrosWithVersions())
    }

    @Test
    fun `a distribution name with spaces is classified under its full name`() {
        val detector = WslEnvironmentDetector(spacedNameCli(spacedListing))

        val status = detector.classify()

        assertTrue(status is DistroStatus.Usable, "got $status")
        assertEquals("Ubuntu Test", status.environment.distro)
    }

    @Test
    fun `an unreadable or failed version listing is not reported as WSL 2`() {
        val listings =
            listOf(
                CliExecutionResult(1, "", "wsl.exe -l -v failed"),
                CliExecutionResult(0, "  NAME STATE VERSION\n* Ubuntu Test Running\n", ""), // no version column
                CliExecutionResult(0, "  NAME STATE VERSION\n* Ubuntu Test Running x\n", ""), // not a number
            )
        for (listing in listings) {
            val status = WslEnvironmentDetector(spacedNameCli(listing)).classify("Ubuntu Test")

            assertTrue(status is DistroStatus.StartupFailed, "a version nobody read must not pass as 2: $status")
            assertTrue("WSL 2" in status.detail, status.detail)
        }
    }

    @Test
    fun `setDefaultDistro runs wsl set-default and returns success on exit 0`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "--set-default", "Ubuntu-24.04")] = CliExecutionResult(0, "", "")
            }
        val detector = WslEnvironmentDetector(cli)
        val result = detector.setDefaultDistro("Ubuntu-24.04")
        assertTrue(result.isSuccess, "Expected success when wsl --set-default exits 0")
    }

    @Test
    fun `setDefaultDistro returns failure on non-zero exit`() {
        val cli =
            FakeWslCliExecutor().apply {
                hostResponses[listOf("wsl.exe", "--set-default", "Ubuntu-24.04")] =
                    CliExecutionResult(
                        exitCode = 1,
                        output = "",
                        error = "There is no distribution with the supplied name.",
                    )
            }
        val detector = WslEnvironmentDetector(cli)
        val result = detector.setDefaultDistro("Ubuntu-24.04")
        assertTrue(result.isFailure, "Expected failure when wsl --set-default exits non-zero")
        assertTrue(result.exceptionOrNull()?.message?.contains("Failed to set default distro") == true)
    }
}
