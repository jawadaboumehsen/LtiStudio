package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying the 10 required C/C++ native development packages via dpkg-query.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating library dependencies.
 */
public class NativeLibrariesInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val requiredPackages: List<String> = DEFAULT_REQUIRED_DEV_PACKAGES
) : SystemDiagnosticInspector {

    public companion object {
        public val DEFAULT_REQUIRED_DEV_PACKAGES: List<String> = listOf(
            "pkg-config",
            "protobuf-compiler",
            "libprotobuf-dev",
            "libbrotli-dev",
            "liblz4-dev",
            "libpcre2-dev",
            "libzstd-dev",
            "libusb-1.0-0-dev",
            "uuid-dev",
            "libfuse3-dev"
        )
    }

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val dpkgRes = cli.execute(
            distro,
            listOf("dpkg-query", "-W", "-f=\${Package} \${Status}\\n") + requiredPackages
        )
        val installedOutput = dpkgRes.output + "\n" + dpkgRes.error
        val installedPkgs = installedOutput.lines()
            .filter { it.contains("install ok installed") }
            .map { it.substringBefore(" ").trim() }
            .toSet()
        val missingLibs = requiredPackages.filter { it !in installedPkgs }
        val libsOk = missingLibs.isEmpty()
        val libsCmd = if (libsOk) null else "sudo apt-get install -y ${missingLibs.joinToString(" ")}"

        return DiagnosticCheckItem(
            id = "host_libraries",
            title = "Native C/C++ Build Dependencies",
            category = DiagnosticCategory.LIBRARIES,
            status = when {
                libsOk -> StepStatus.SUCCESS
                context.binariesPresent -> StepStatus.WARNING
                else -> StepStatus.FAILED
            },
            detail = if (libsOk) {
                "All ${requiredPackages.size} C++ development libraries verified."
            } else {
                "Missing development packages: ${missingLibs.joinToString(", ")}."
            },
            remediation = if (libsOk) null else {
                if (context.binariesPresent) {
                    "Pre-built binaries active in ${context.binDir}. Install libraries if compiling from source: $libsCmd"
                } else {
                    "Run '$libsCmd' in WSL to compile toolchain binaries."
                }
            },
            copyableCommand = libsCmd,
            isOptionalForRuntime = context.binariesPresent
        )
    }
}
