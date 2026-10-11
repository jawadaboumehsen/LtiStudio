package org.ide.lti.desktop.realhost

import io.ltirom.tooling.core.remote.ToolExecutionRequest
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.tools.KotlinRecipeRunner
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.setup.ToolOutput
import org.ide.lti.core.domain.setup.ToolOutputKind
import org.ide.lti.core.domain.setup.ToolProbe
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Real-host integration test for building AndroidTools recipe and verifying options/binaries (T029).
 */
class AndroidToolsRecipeRealTest {
    @Test
    fun androidToolsRecipeBuildsWithExpectedCmakeOptionsAndGitIdentity() = runTest {
        if (!RealToolHost.isAvailable()) return@runTest

        RealToolHost.create().use { host ->
            val repoPath = host.hostPath("android-tools-fixture")
            val setupRes = Fixtures.setupAndroidToolsFixture(host.transport, repoPath)
            assertTrue(
                setupRes.isSuccess,
                "Failed to setup android-tools fixture: ${setupRes.exceptionOrNull()?.message}",
            )

            val group = ToolGroupCatalog.group(ToolGroupId("android-tools"))
            assertNotNull(group, "android-tools group must exist in ToolGroupCatalog")

            // Verify recipe config options
            val recipe = group.recipe
            assertTrue(recipe is RecipeConfig.AndroidTools, "android-tools recipe must be AndroidTools")

            // Verify simg2img is an alias of android-tools output
            val simg2imgOut = ToolGroupCatalog.allOutputs.firstOrNull { it.toolId == "simg2img" }
            assertNotNull(simg2imgOut, "simg2img output must exist")
            assertEquals("android-tools", simg2imgOut.aliasOf ?: "android-tools")

            // Verify apktool has kind = JAR
            val apktoolOut = ToolGroupCatalog.allOutputs.firstOrNull { it.toolId == "apktool" }
            assertNotNull(apktoolOut, "apktool output must exist")
            assertEquals(ToolOutputKind.JAR, apktoolOut.kind)

            val outDir = host.hostPath("android-tools-out")
            val runner = KotlinRecipeRunner(host.transport)
            val outputs =
                listOf(
                    ToolOutput("adb", "adb", ToolOutputKind.NATIVE, ToolProbe(listOf("version"))),
                    ToolOutput("fastboot", "fastboot", ToolOutputKind.NATIVE, ToolProbe(listOf("--version"))),
                    ToolOutput(
                        "mke2fs",
                        "mke2fs",
                        ToolOutputKind.NATIVE,
                        ToolProbe(listOf("-V")),
                        aliases = mapOf("mke2fs.android" to "mke2fs"),
                    ),
                    ToolOutput("avbtool", "avbtool", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                    ToolOutput(
                        "mkbootimg",
                        "mkbootimg",
                        ToolOutputKind.NATIVE,
                        ToolProbe(emptyList()),
                        supportFiles = listOf("gki/"),
                    ),
                    ToolOutput("unpack_bootimg", "unpack_bootimg", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                    ToolOutput("repack_bootimg", "repack_bootimg", ToolOutputKind.NATIVE, ToolProbe(emptyList())),
                    ToolOutput(
                        "mkuserimg_mke2fs",
                        "mkuserimg_mke2fs",
                        ToolOutputKind.NATIVE,
                        ToolProbe(emptyList()),
                    ),
                )
            val buildRes =
                runner.executeRecipe(
                    srcDir = repoPath,
                    outDir = outDir,
                    source =
                    ResolvedSource.Git(
                        "https://github.com/UN1CA/external_android-tools.git",
                        ToolRef.Branch("sixteen"),
                        "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
                    ),
                    recipe = recipe,
                    outputs = outputs,
                    supportFiles = listOf("gki/"),
                    onLog = {},
                )
            assertTrue(buildRes.isSuccess, "executeRecipe failed: ${buildRes.exceptionOrNull()?.message}")

            val optionsReq = ToolExecutionRequest(toolId = "cat", arguments = listOf("$repoPath/build/options.txt"))
            val optionsRes = host.transport.execute(optionsReq)
            assertEquals(0, optionsRes.exitCode, "options.txt must exist: ${optionsRes.stderr}")
            assertTrue(optionsRes.stdout.contains("CMAKE_BUILD_TYPE=Release"))
            assertTrue(optionsRes.stdout.lines().any { it.startsWith("C_COMPILER=") && it.contains("clang") })
            assertTrue(optionsRes.stdout.lines().any { it.startsWith("CXX_COMPILER=") && it.contains("clang++") })
            assertTrue(optionsRes.stdout.contains("USE_BUNDLED_FMT=ON"))
            assertTrue(optionsRes.stdout.contains("USE_BUNDLED_LIBUSB=ON"))
            assertTrue(optionsRes.stdout.contains("PATCH_VENDOR=ON"))
            assertTrue(optionsRes.stdout.contains("GIT_COMMITTER_NAME=LtiRom Studio"))
            assertTrue(optionsRes.stdout.contains("GIT_COMMITTER_EMAIL=studio@ltirom.local"))
            assertTrue(optionsRes.stdout.contains("GIT_AUTHOR_NAME=LtiRom Studio"))
            assertTrue(optionsRes.stdout.contains("GIT_AUTHOR_EMAIL=studio@ltirom.local"))

            for (out in outputs) {
                val checkReq =
                    ToolExecutionRequest(toolId = "test", arguments = listOf("-x", "$outDir/${out.file}"))
                val checkRes = host.transport.execute(checkReq)
                assertEquals(0, checkRes.exitCode, "${out.file} must exist and be executable in $outDir")
            }
            val gkiReq =
                ToolExecutionRequest(toolId = "test", arguments = listOf("-f", "$outDir/gki/gki_kernel.img"))
            val gkiRes = host.transport.execute(gkiReq)
            assertEquals(0, gkiRes.exitCode, "gki/gki_kernel.img must exist in $outDir")
        }
    }
}
