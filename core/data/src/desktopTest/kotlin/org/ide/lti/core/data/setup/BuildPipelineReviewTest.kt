/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.client.wsl.CliExecutionResult
import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.remote.ToolPublicationAdapter
import org.ide.lti.core.domain.ports.ToolSource
import org.ide.lti.core.domain.ports.ToolStatus
import org.ide.lti.core.domain.setup.ToolCapabilities
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Regression tests for the rebuild / repair / test pipeline review (8 findings). */
class BuildPipelineReviewTest {

    private val bin = "/h/LtiRomTools/bin"
    private val ext = "/h/LtiRomTools/external"

    /**
     * A distro where every path exists except [missing], commands succeed unless [failing] matches, and pinned
     * binaries hash to their pins unless [staleDigest].
     */
    private open class Distro(
        val missing: MutableSet<String> = mutableSetOf(),
        val failing: (List<String>) -> Boolean = { false },
        val staleDigest: Boolean = false,
    ) : WslCliExecutor() {
        val commands = mutableListOf<List<String>>()

        override fun execute(
            distro: String,
            command: List<String>,
            timeoutSeconds: Long,
            charset: Charset,
            user: String?,
        ): CliExecutionResult {
            commands += command
            return when {
                failing(command) -> CliExecutionResult(1, "", "No space left on device")
                command.first() == "test" -> CliExecutionResult(if (command.last() in missing) 1 else 0, "", "")
                command.first() == "sha256sum" && staleDigest -> CliExecutionResult(0, "${"0".repeat(64)}  x", "")
                command.first() == "sha256sum" -> fakeSha256(command.getOrNull(1).orEmpty())
                else -> CliExecutionResult(0, "", "")
            }
        }

        fun ran(tool: String) = commands.filter { it.first() == tool }
    }

    // 1. Android-tool repairs publish every ID of the group: all of them must be catalog tools.
    @Test
    fun `the capability inventory and the publication catalog list the same tools`() {
        val inventory = ToolCapabilities.SUPPORTED_TOOL_IDS
        assertEquals(inventory, ToolCatalog.ALL_TOOL_IDS, "a repair would request IDs publication rejects")
    }

    @Test
    fun `an android-tools repair is accepted by the real publisher`() = runTest {
        val group = ToolCapabilities.toolsForRecipeGroup(ToolCapabilities.GROUP_ANDROID_TOOLS)
        val transport = FakeRemoteTransport(publishedTools = group.map(::projectTool).toMutableList())

        val report = ToolPublicationAdapter(transport).publish(FAKE_BIN_DIR, group)

        assertTrue(report.failed.isEmpty(), "rejected: ${report.failed}")
        assertEquals(group, report.registered)
    }

    // 2. A group is skipped only while every tool of it is present.
    @Test
    fun `stage retry rebuilds android-tools when the sentinel exists but a sibling is missing`() = runTest {
        val repository = inMemoryToolchainRepository()
        repository.updateTool("adb") { it.copy(isCompiled = true) }
        val cli = Distro(missing = mutableSetOf("$bin/lpunpack"))

        ToolchainBuildEngine(cli = cli, repository = repository).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_ANDROID_TOOLS),
        )

        // Configure runs as `env GIT_...=... cmake ...` (the git identity for its vendor patches).
        assertTrue(
            cli.ran("env").any { "cmake" in it },
            "lpunpack is missing, so android-tools must be compiled",
        )
    }

    @Test
    fun `a tool that lost its executable bit is rebuilt, not skipped as present`() = runTest {
        val repository = inMemoryToolchainRepository()
        repository.updateTool("adb") { it.copy(isCompiled = true) }
        val cli = object : Distro() {
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult = if (command == listOf("test", "-x", "$bin/lpunpack")) {
                commands += command
                CliExecutionResult(1, "", "") // present, but chmod -x
            } else {
                super.execute(distro, command, timeoutSeconds, charset, user)
            }
        }

        ToolchainBuildEngine(cli = cli, repository = repository).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_ANDROID_TOOLS),
        )

        assertTrue(cli.ran("env").any { "cmake" in it }, "a present but unusable tool must not count as built")
    }

    // 3. Repairing one auxiliary tool touches nothing else.
    @Test
    fun `repairing gh copies nothing for other tools`() = runTest {
        val cli = Distro()

        ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_GH),
        )

        val copies = cli.ran("cp") + cli.ran("install")
        assertTrue(
            copies.none { cmd ->
                cmd.any { "payload-dumper-go" in it || "apktool" in it || "signapk" in it }
            },
            "$copies",
        )
        assertTrue(cli.commands.none { cmd -> cmd.any { it == "$ext/bin/." } }, "no blanket copy of a pre-staged bin")
    }

    // 4. A failed install of fresh outputs fails the rebuild even though old binaries are still there.
    @Test
    fun `a failed copy of build outputs fails the rebuild despite old binaries`() = runTest {
        val cli = Distro(failing = { it.first() == "cp" })

        val result = ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_EROFS_UTILS),
            forceRebuild = true,
        )

        assertTrue(result.isFailure)
        assertTrue("installing its outputs failed" in result.exceptionOrNull()?.message.orEmpty(), "$result")
    }

    // 5. A pinned release that can't be verified fails the build instead of leaving a stale binary.
    @Test
    fun `a stale binary plus a failed download fails the repair`() = runTest {
        val cli = Distro(staleDigest = true, failing = { it.first() == "curl" })

        val result = ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_GH),
        )

        assertTrue(result.isFailure, "the stale gh must not count as installed")
        assertTrue("Could not install gh 2.97.0" in result.exceptionOrNull()?.message.orEmpty(), "$result")
    }

    // 6. Cancelling a repair during a download stops it: nothing is installed afterwards.
    @Test
    fun `cancelling during a download installs nothing afterwards`() = runBlocking<Unit> {
        val downloading = CompletableDeferred<Unit>()
        val cli = object : Distro(staleDigest = true) {
            override suspend fun run(distro: String, command: List<String>, timeoutSeconds: Long, user: String?) =
                if (command.first() == "curl") {
                    commands += command
                    downloading.complete(Unit)
                    awaitCancellation()
                } else {
                    execute(distro, command, timeoutSeconds, Charsets.UTF_8, user)
                }
        }
        val repair = async {
            ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
                "Ubuntu",
                ext,
                bin,
                targetRecipeGroups = setOf(ToolCapabilities.GROUP_GH),
            )
        }

        downloading.await()
        repair.cancel()
        repair.join()

        assertTrue(cli.ran("install").isEmpty() && cli.ran("mv").isEmpty(), "${cli.commands}")
    }

    // 7. A fallback copy of the same name never counts as the project's published tool.
    @Test
    fun `only the project's manifest at its bin path counts as published`() {
        fun status(source: ToolSource?, path: String) = ToolStatus("lpmake", true, path, source, null)

        assertTrue(ToolCatalog.isProjectPublished(status(ToolSource.DYNAMIC, "$bin/lpmake"), bin))
        assertFalse(ToolCatalog.isProjectPublished(status(ToolSource.SYSTEM_PATH, "/usr/bin/lpmake"), bin))
        assertFalse(ToolCatalog.isProjectPublished(status(ToolSource.DYNAMIC, "/opt/other/lpmake"), bin))
        assertFalse(ToolCatalog.isProjectPublished(status(ToolSource.PINNED, "$bin/lpmake"), bin))
    }

    // 8. A readable JAR passes the existence check and runs through java -jar.
    @Test
    fun `tool tests check a JAR for readability and run it with java -jar`() {
        val jar = ToolCatalog.probePlan("signapk", bin, "signapk")
        val native = ToolCatalog.probePlan("lpmake", bin, "lpmake")

        assertEquals(listOf("test", "-r", "$bin/signapk.jar"), jar.existsCheck)
        assertEquals(listOf("java", "-jar", "$bin/signapk.jar"), jar.argv.take(3))
        assertEquals(listOf("test", "-x", "$bin/lpmake"), native.existsCheck)
        assertEquals("$bin/lpmake", native.argv.first())
    }

    // The JAR tools are built from their submodules with Gradle, not taken from a manual build.
    @Test
    fun `a missing signapk jar is built with the submodule's Gradle wrapper and installed`() = runTest {
        val output = "$ext/signapk/signapk/build/libs/signapk-all.jar"
        val cli = object : Distro(missing = mutableSetOf(output)) {
            // The Gradle run produces the jar.
            override fun execute(
                distro: String,
                command: List<String>,
                timeoutSeconds: Long,
                charset: Charset,
                user: String?,
            ): CliExecutionResult {
                if (command.firstOrNull() == "sh") (missing as MutableSet).remove(output)
                return super.execute(distro, command, timeoutSeconds, charset, user)
            }
        }

        val result = ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_SIGNAPK),
        )

        assertTrue(result.isSuccess, "$result")
        assertEquals(
            listOf("sh", "$ext/signapk/gradlew", "-p", "$ext/signapk", "--no-daemon", ":signapk:shadowJar"),
            cli.ran("sh").single(),
        )
        assertTrue(cli.ran("cp").any { it == listOf("cp", "-a", output, "$bin/signapk.jar") }, "${cli.commands}")
    }

    @Test
    fun `an existing jar is kept, a rebuild always runs Gradle`() = runTest {
        val cli = Distro()
        // Built earlier from the current sources (a sync would clear this record).
        val repository = inMemoryToolchainRepository()
        repository.updateTool("apktool") { it.copy(isCompiled = true) }
        val engine = ToolchainBuildEngine(cli = cli, repository = repository)
        val apktool = setOf(ToolCapabilities.GROUP_APKTOOL)

        engine.buildAndDistributeAll("Ubuntu", ext, bin, targetRecipeGroups = apktool)
        assertTrue(cli.ran("sh").isEmpty(), "the built jar is reused")

        engine.buildAndDistributeAll("Ubuntu", ext, bin, targetRecipeGroups = apktool, forceRebuild = true)
        assertEquals(":brut.apktool:apktool-cli:shadowJar", cli.ran("sh").single().last())
    }

    @Test
    fun `a failed Gradle build fails the step`() = runTest {
        val cli = Distro(
            missing = mutableSetOf("$ext/apktool/brut.apktool/apktool-cli/build/libs/apktool-cli.jar"),
            failing = { it.first() == "sh" },
        )

        val result = ToolchainBuildEngine(cli = cli).buildAndDistributeAll(
            "Ubuntu",
            ext,
            bin,
            targetRecipeGroups = setOf(ToolCapabilities.GROUP_APKTOOL),
        )

        assertTrue("apktool Gradle build failed" in result.exceptionOrNull()?.message.orEmpty(), "$result")
    }
}
