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

import io.ltirom.tooling.client.wsl.WslEnvironmentDetector
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus
import org.ide.lti.core.model.setup.PersistedEnvironment
import org.ide.lti.core.model.setup.SetupAttemptRecord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Whole-check scenarios from the setup review: inventory, pinned downloads, and the distro after a restart. */
@OptIn(ExperimentalCoroutinesApi::class)
class SetupScenarioTest {

    private fun healthyMachine() = FakeWslCliExecutor().apply {
        ToolCatalog.entries.forEach { existingPaths.add("$FAKE_BIN_DIR/${it.binaryName}") }
    }

    private fun provisioner(
        cli: FakeWslCliExecutor,
        repository: org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository,
    ) = WslToolchainProvisioner(
        supervisor = FakeDaemonSupervisor(),
        transport = FakeRemoteTransport(fakeCli = cli),
        repository = repository,
        detector = WslEnvironmentDetector(cli = cli),
        cli = cli,
        dispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun `a readable jar is a usable tool, an executable-less native file is not`() {
        val listing = "644 apktool.jar\n755 adb\n644 lpmake\n600 signapk.jar\n"

        // One rule for discovery, publication and the probe: JARs run as `java -jar`, the rest must be +x.
        assertEquals(setOf("apktool.jar", "adb", "signapk.jar"), WslToolchainProvisioner.usableToolFiles(listing))
    }

    @Test
    fun `a pinned download that is not its pin is reported outdated and marked for reinstall`() = runTest {
        val cli = healthyMachine().apply { staleDigests += "gh" }

        val state = provisioner(cli, inMemoryToolchainRepository()).verifyEnvironment()

        val toolchain = state.steps.first { it.stage == SetupStepStage.TOOLCHAIN_COMPILATION }
        assertEquals(StepStatus.FAILED, toolchain.status, "${toolchain.description} / ${toolchain.error}")
        assertTrue("gh (expected" in toolchain.error.orEmpty(), "${toolchain.error}")
        assertEquals(StepStatus.FAILED, state.toolsMatrix.first { it.id == "gh" }.status, "the retry reinstalls it")
    }

    @Test
    fun `after a restart the check targets the distro of the pending attempt, not the WSL default`() = runTest {
        val cli = healthyMachine().apply { hostDistros = listOf("Ubuntu", "Ubuntu-22.04") }
        val repository = inMemoryToolchainRepository()
        repository.recordAttemptAuthorized(
            SetupAttemptRecord(
                attemptId = "pending-1",
                environmentKey = "Ubuntu-22.04",
                planId = "plan-1",
                planRevisionHash = "rev-1",
                planKind = "FULL_SETUP",
                environment = PersistedEnvironment("Ubuntu-22.04", 2, "ubuntu", "22.04", "lti", "/home/lti"),
            ),
        ).getOrThrow()

        val state = provisioner(cli, repository).verifyEnvironment()

        assertEquals("Ubuntu-22.04", state.activeDistro)
    }
}
