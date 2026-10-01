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
import kotlinx.coroutines.test.runTest
import java.nio.charset.Charset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Sources are real clones of the pinned upstream commits, measured on disk, never taken from the journal. */
class SourceSyncTest {

    private val ext = "/h/LtiRomTools/external"
    private val img2sdat = SubmoduleSyncEngine.DEFAULT_SUBMODULES.first { it.name == "img2sdat" }
    private val pins = listOf(img2sdat)

    /** A distro where [dir] exists and `git rev-parse` reports [topLevel] and [head]. */
    private class Disk(val topLevel: String, val head: String) : WslCliExecutor() {
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
                "rev-parse" in command && "--show-toplevel" in command -> CliExecutionResult(0, topLevel, "")
                "rev-parse" in command -> CliExecutionResult(0, head, "")
                else -> CliExecutionResult(0, "", "")
            }
        }
    }

    @Test
    fun `an imported folder that is not its own clone is moved aside and cloned at the pin`() = runTest {
        // What this machine had: every source folder inside one "Initial import" repository.
        val cli = Disk(topLevel = "/h/LtiRomTools", head = "ca3c5e96")

        val result = SubmoduleSyncEngine(cli = cli).syncAll("Ubuntu", ext, pins)

        assertTrue(result.isSuccess, "$result")
        val dir = "$ext/img2sdat"
        val move = cli.commands.first { it.first() == "mv" }
        assertEquals(dir, move[1])
        assertTrue(move[2].startsWith("$dir.not-a-clone-"), "moved aside, never deleted: $move")
        assertTrue(cli.commands.none { it.first() == "rm" && dir in it }, "the imported folder is not deleted")
        val order = cli.commands.map { it.joinToString(" ") }
        val clone = order.indexOfFirst { it.startsWith("git clone --recursive ${img2sdat.url} $dir") }
        val checkout = order.indexOfFirst { it == "git -C $dir checkout -f ${img2sdat.commit}" }
        val nested = order.indexOfFirst { it == "git -C $dir submodule update --init --recursive --force" }
        assertTrue(clone in 0 until checkout && checkout < nested, "clone, pin, then the pin's submodules: $order")
    }

    @Test
    fun `a clone already at its pin is left alone`() = runTest {
        val cli = Disk(topLevel = "$ext/img2sdat", head = img2sdat.commit)

        SubmoduleSyncEngine(cli = cli).syncAll("Ubuntu", ext, pins)

        assertFalse(
            cli.commands.any {
                it.firstOrNull() == "mv" || "clone" in it || "checkout" in it
            },
            "${cli.commands}",
        )
    }

    @Test
    fun `changed sources mark their group's tools for rebuild`() = runTest {
        val repository = inMemoryToolchainRepository()
        repository.updateTool("img2sdat") { it.copy(isCompiled = true, isVerified = true) }
        val cli = Disk(topLevel = "$ext/img2sdat", head = "0000000") // own clone, other commit

        SubmoduleSyncEngine(cli = cli, repository = repository).syncAll("Ubuntu", ext, pins)

        assertFalse(repository.currentState.tools["img2sdat"]?.isCompiled == true, "built from other sources")
    }

    @Test
    fun `retrying source repositories syncs, rebuilds and publishes`() {
        val plan = SetupPlanFactory.createPlan(
            kind = org.ide.lti.core.domain.setup.SetupPlanKind.STAGE_RETRY,
            targetId = org.ide.lti.core.domain.setup.SetupStepStage.REPO_SYNCHRONIZATION.name,
            autoDoctorEnabled = true,
            snapshot = org.ide.lti.core.domain.setup.ToolchainSetupState(),
            environmentKey = "Ubuntu",
        )

        assertEquals(
            listOf("SyncSources", "BuildRecipes", "PublishTools"),
            plan.orderedActions.map { it::class.simpleName },
        )
    }
}
