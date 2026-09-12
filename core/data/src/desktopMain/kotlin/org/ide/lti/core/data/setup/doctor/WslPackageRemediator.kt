package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Service responsible for automated headless package remediation in WSL 2.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Resolves, batches, and installs missing packages via root elevation.
 * - Non-Interactive Automation & Security: Uses passwordless `sudo -n` elevation to execute `apt-get`
 *   and system configuration without TTY password prompts. Refuses execution if passwordless sudo is not configured,
 *   and NEVER falls back to `wsl -u root`.
 * - No Shell Chains: All commands execute as discrete arguments without `bash -c` or `&&`.
 */
public class WslPackageRemediator(
    private val cli: WslCliExecutor = WslCliExecutor()
) {
    public suspend fun remediate(
        context: DiagnosticContext,
        diagnosticItems: List<DiagnosticCheckItem>,
        onProgress: (String) -> Unit = {}
    ): Boolean {
        val distro = context.distro
        val failedOrWarning = diagnosticItems.filter {
            it.status == StepStatus.FAILED || it.status == StepStatus.WARNING
        }

        if (failedOrWarning.isEmpty()) {
            onProgress("All diagnostic checks are already satisfied. No remediation needed.")
            return true
        }

        val aptPackages = mutableSetOf<String>()
        var needsPipCrypto = false
        var needsLoopSudoers = false
        var needsToolchainSync = false

        for (item in failedOrWarning) {
            when (item.id) {
                "host_compilers" -> {
                    aptPackages.addAll(listOf("build-essential", "cmake", "make", "clang"))
                }
                "host_libraries" -> {
                    aptPackages.addAll(NativeLibrariesInspector.DEFAULT_REQUIRED_DEV_PACKAGES)
                }
                "python_crypto" -> {
                    aptPackages.addAll(listOf("python3", "python3-pip"))
                    needsPipCrypto = true
                }
                "jdk_dual" -> {
                    aptPackages.addAll(listOf("openjdk-17-jdk", "openjdk-21-jdk"))
                }
                "selinux_attr" -> {
                    aptPackages.add("attr")
                }
                "archive_tools" -> {
                    aptPackages.addAll(listOf("zip", "unzip", "brotli"))
                }
                "transfer_tools" -> {
                    aptPackages.addAll(listOf("curl", "git", "rsync", "tar", "file", "xxd"))
                }
                "dev_fuse" -> {
                    aptPackages.addAll(listOf("fuse3", "libfuse3-dev"))
                }
                "loop_mount" -> {
                    needsLoopSudoers = true
                }
                "openssl_tools" -> {
                    aptPackages.add("openssl")
                }
                "build_cache" -> {
                    aptPackages.add("ccache")
                }
                "toolchain_binaries" -> {
                    needsToolchainSync = true
                }
            }
        }

        val requiresSudo = aptPackages.isNotEmpty() || needsLoopSudoers
        if (requiresSudo) {
            val sudoCheck = cli.execute(distro, listOf("sudo", "-n", "true"), timeoutSeconds = 5)
            if (sudoCheck.exitCode != 0) {
                onProgress("Error: Passwordless non-interactive sudo is not configured in $distro. Cannot run automated package remediation.")
                onProgress("Please run the following one-time command in your WSL terminal to configure sudo:")
                onProgress("echo \"\$USER ALL=(ALL) NOPASSWD: ALL\" | sudo tee /etc/sudoers.d/ltirom")
                return false
            }
        }

        // 1. Batch APT Package Installation via non-interactive sudo (never wsl -u root)
        if (aptPackages.isNotEmpty()) {
            onProgress("Updating package index (apt-get update)...")
            cli.execute(
                distro = distro,
                command = listOf("sudo", "-n", "apt-get", "update"),
                timeoutSeconds = 120
            )

            onProgress("Installing ${aptPackages.size} package(s) via sudo: ${aptPackages.sorted().joinToString(", ")}...")
            val installRes = cli.execute(
                distro = distro,
                command = listOf("sudo", "-n", "apt-get", "install", "-y") + aptPackages.sorted(),
                timeoutSeconds = 600
            )
            if (installRes.exitCode != 0) {
                onProgress("Warning: apt-get encountered errors: ${installRes.error.take(200)}")
            } else {
                onProgress("Successfully installed system packages.")
            }
        }

        // 2. Python Cryptography & PyASN1 via pip3 (with sudo -n apt-get fallback)
        if (needsPipCrypto) {
            onProgress("Installing Python cryptography and pyasn1 packages...")
            val pipRes = cli.execute(
                distro = distro,
                command = listOf("pip3", "install", "--break-system-packages", "cryptography", "pyasn1"),
                timeoutSeconds = 180
            )
            if (pipRes.exitCode == 0) {
                onProgress("Python cryptography packages installed.")
            } else {
                // Fallback attempt via APT
                cli.execute(
                    distro = distro,
                    command = listOf("sudo", "-n", "apt-get", "install", "-y", "python3-cryptography", "python3-pyasn1"),
                    timeoutSeconds = 180
                )
            }
        }

        // 3. Loop Mount Sudoers Elevation Configuration
        if (needsLoopSudoers) {
            onProgress("Configuring loop mount elevation in /etc/sudoers.d/ltirom-mount...")
            cli.execute(
                distro = distro,
                command = listOf(
                    "sudo", "-n", "python3", "-c",
                    "open('/etc/sudoers.d/ltirom-mount', 'w').write('ALL ALL=(ALL) NOPASSWD: /bin/mount, /bin/umount\\n')"
                ),
                timeoutSeconds = 30
            )
            cli.execute(
                distro = distro,
                command = listOf("sudo", "-n", "chmod", "0440", "/etc/sudoers.d/ltirom-mount"),
                timeoutSeconds = 10
            )
            onProgress("Loop mount elevation configured.")
        }

        // 4. Toolchain Binaries Synchronization (no bash -c, pure sequential commands)
        if (needsToolchainSync) {
            onProgress("Materializing missing toolchain binaries in ~/LtiRomTools/bin...")
            val toolsRoot = "${context.home}/LtiRomTools"
            val binDir = context.binDir
            val extDir = "$toolsRoot/external"
            cli.execute(distro, listOf("mkdir", "-p", binDir))

            if (cli.execute(distro, listOf("test", "-d", "$extDir/bin")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/bin/.", "$binDir/"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/payload-dumper-go/payload-dumper-go")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/payload-dumper-go/payload-dumper-go", "$binDir/payload-dumper-go"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$extDir/signapk/signapk/build/libs/signapk.jar")).exitCode == 0) {
                cli.execute(distro, listOf("cp", "-a", "$extDir/signapk/signapk/build/libs/signapk.jar", "$binDir/signapk.jar"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/fuse.erofs")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "fuse.erofs", "$binDir/erofsfuse"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mkbootimg.py")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mkbootimg.py", "$binDir/mkbootimg"))
            }
            if (cli.execute(distro, listOf("test", "-f", "$binDir/mke2fs.android")).exitCode == 0) {
                cli.execute(distro, listOf("ln", "-sf", "mke2fs.android", "$binDir/mke2fs"))
            }
            onProgress("Toolchain binaries synchronized.")
        }

        return true
    }
}
