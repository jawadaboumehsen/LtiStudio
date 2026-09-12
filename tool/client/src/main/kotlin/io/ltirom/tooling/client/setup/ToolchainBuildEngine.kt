package io.ltirom.tooling.client.setup

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.model.setup.PersistedToolState

/**
 * Compiles and distributes the native ROM-engineering toolchain binaries into `binDir`.
 *
 * Reconstruction note: the ~30-tool expected binary matrix (`ToolchainProvisioningService`'s
 * default tool catalog: lpmake/lpunpack/mkfs.erofs/mkbootimg/avbtool/signapk/...) spans several
 * independent native build systems (cmake/make projects under each submodule, a Gradle build for
 * signapk, prebuilt platform-tools for adb/fastboot). No surviving caller or transcript pins the
 * exact per-tool build commands, so this does NOT fabricate them. It does the one thing that IS
 * evidence-backed: copies the already-known signapk Gradle output (pinned verbatim by
 * `WslPackageRemediator.kt`'s existing toolchain-sync logic) and reports exactly which of the
 * remaining expected binaries are present vs. still missing in `binDir`, so setup surfaces an
 * honest, actionable gap instead of a false "all built" success.
 */
public class ToolchainBuildEngine(
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val repository: ToolchainSetupRepository? = null,
) {
    public suspend fun buildAndDistributeAll(
        distro: String,
        extDir: String,
        binDir: String,
        onProgress: (String) -> Unit,
    ): Result<Unit> {
        cli.execute(distro, listOf("mkdir", "-p", binDir))

        onProgress("Distributing prebuilt/already-compiled binaries into $binDir...")
        cli.execute(distro, listOf("bash", "-c", "test -d $extDir/bin && cp -a $extDir/bin/* $binDir/ 2>/dev/null || true"))
        cli.execute(
            distro,
            listOf(
                "bash", "-c",
                "test -f $extDir/signapk/signapk/build/libs/signapk.jar && " +
                    "cp -a $extDir/signapk/signapk/build/libs/signapk.jar $binDir/signapk.jar 2>/dev/null || true",
            ),
        )

        onProgress("Attempting Gradle build for signapk...")
        val signapkBuilt = cli.execute(
            distro,
            listOf("bash", "-c", "test -f $extDir/signapk/gradlew"),
        ).exitCode == 0
        if (signapkBuilt) {
            cli.execute(
                distro,
                listOf("bash", "-c", "cd $extDir/signapk && chmod +x gradlew && ./gradlew build -x test"),
                timeoutSeconds = 600,
            )
            cli.execute(
                distro,
                listOf(
                    "bash", "-c",
                    "test -f $extDir/signapk/signapk/build/libs/signapk.jar && " +
                        "cp -a $extDir/signapk/signapk/build/libs/signapk.jar $binDir/signapk.jar 2>/dev/null || true",
                ),
            )
        }

        val listing = cli.execute(distro, listOf("bash", "-c", "ls -1 $binDir 2>/dev/null"))
        val present = listing.output.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toSet()

        val expectedTools = EXPECTED_BINARIES
        val missing = expectedTools.filter { it !in present }

        for (toolId in expectedTools) {
            val isPresent = toolId in present
            repository?.updateTool(toolId) { current: PersistedToolState ->
                current.copy(isCompiled = isPresent)
            }
        }

        return if (missing.isEmpty()) {
            onProgress("All ${expectedTools.size} core tool binaries present in $binDir.")
            Result.success(Unit)
        } else {
            onProgress(
                "Automated compilation is not implemented for: ${missing.joinToString(", ")}. " +
                    "Build these manually from their sources under $extDir and place the " +
                    "resulting binaries in $binDir.",
            )
            Result.failure(
                IllegalStateException("Some core tool binaries are missing in $binDir after build: ${missing.joinToString(", ")}"),
            )
        }
    }

    private companion object {
        val EXPECTED_BINARIES = listOf(
            "lpmake", "lpunpack", "lpdump", "lpadd", "lpflash",
            "mkfs.erofs", "dump.erofs", "fsck.erofs", "erofsfuse",
            "mke2fs", "e2fsdroid", "mkuserimg_mke2fs",
            "make_f2fs", "mkf2fsuserimg", "sload_f2fs",
            "simg2img", "img2simg", "ext2simg", "append2simg", "img2sdat",
            "mkbootimg", "unpack_bootimg", "repack_bootimg", "mkbootfs", "mkdtboimg",
            "avbtool", "signapk", "fec",
        )
    }
}
