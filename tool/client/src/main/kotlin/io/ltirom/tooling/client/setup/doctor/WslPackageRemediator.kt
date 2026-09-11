package io.ltirom.tooling.client.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Service responsible for automated headless package remediation in WSL 2.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Resolves, batches, and installs missing packages via root elevation.
 * - Non-Interactive Automation: Uses WSL 2's `-u root` execution flag to execute `apt-get` and configure
 *   elevations without TTY password prompt blocking.
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

        // 1. Batch APT Package Installation via WSL Root
        if (aptPackages.isNotEmpty()) {
            onProgress("Updating package index (apt-get update)...")
            cli.execute(distro, listOf("apt-get", "update"), timeoutSeconds = 120, user = "root")

            onProgress("Installing ${aptPackages.size} package(s) via root: ${aptPackages.sorted().joinToString(", ")}...")
            val installRes = cli.execute(
                distro = distro,
                command = listOf("apt-get", "install", "-y") + aptPackages.sorted(),
                timeoutSeconds = 600,
                user = "root"
            )
            if (installRes.exitCode != 0) {
                onProgress("Warning: apt-get encountered errors: ${installRes.error.take(200)}")
            } else {
                onProgress("Successfully installed system packages.")
            }
        }

        // 2. Python Cryptography & PyASN1 via pip3
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
                    command = listOf("apt-get", "install", "-y", "python3-cryptography", "python3-pyasn1"),
                    timeoutSeconds = 180,
                    user = "root"
                )
            }
        }

        // 3. Loop Mount Sudoers Elevation Configuration
        if (needsLoopSudoers) {
            onProgress("Configuring loop mount elevation in /etc/sudoers.d/ltirom-mount...")
            cli.execute(
                distro = distro,
                command = listOf(
                    "bash", "-c",
                    "echo \"ALL ALL=(ALL) NOPASSWD: /bin/mount, /bin/umount\" > /etc/sudoers.d/ltirom-mount && chmod 0440 /etc/sudoers.d/ltirom-mount"
                ),
                timeoutSeconds = 30,
                user = "root"
            )
            onProgress("Loop mount elevation configured.")
        }

        // 4. Toolchain Binaries Synchronization
        if (needsToolchainSync) {
            onProgress("Materializing missing toolchain binaries in ~/LtiRomTools/bin...")
            val toolsRoot = "${context.home}/LtiRomTools"
            val binDir = context.binDir
            val extDir = "$toolsRoot/external"
            cli.execute(distro, listOf("mkdir", "-p", binDir))
            cli.execute(distro, listOf("bash", "-c", "test -d $extDir/bin && cp -a $extDir/bin/* $binDir/ 2>/dev/null || true"))
            cli.execute(distro, listOf("bash", "-c", "test -f $extDir/payload-dumper-go/payload-dumper-go && cp -a $extDir/payload-dumper-go/payload-dumper-go $binDir/ 2>/dev/null || true"))
            cli.execute(distro, listOf("bash", "-c", "test -f $extDir/signapk/signapk/build/libs/signapk.jar && cp -a $extDir/signapk/signapk/build/libs/signapk.jar $binDir/signapk.jar 2>/dev/null || true"))
            cli.execute(distro, listOf("bash", "-c", "test -f $binDir/fuse.erofs && ln -sf fuse.erofs $binDir/erofsfuse || true"))
            cli.execute(distro, listOf("bash", "-c", "test -f $binDir/mkbootimg.py && ln -sf mkbootimg.py $binDir/mkbootimg || true"))
            cli.execute(distro, listOf("bash", "-c", "test -f $binDir/mke2fs.android && ln -sf mke2fs.android $binDir/mke2fs || true"))
            cli.execute(distro, listOf("bash", "-c", "chmod +x $binDir/* 2>/dev/null || true"))
            onProgress("Toolchain binaries synchronized.")
        }

        return true
    }
}
