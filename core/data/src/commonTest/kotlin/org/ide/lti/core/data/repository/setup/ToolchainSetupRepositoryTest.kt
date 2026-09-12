/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.repository.setup

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.datastore.ToolchainPreferencesDataSource
import org.ide.lti.core.domain.setup.SetupStepStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolchainSetupRepositoryTest {

    @Test
    fun testRepositoryDelegationAndStateFlow() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val repository = ToolchainSetupRepositoryImpl(dataSource)

        val initial = repository.toolchainState.first()
        assertFalse(initial.isSetupCompleted)

        repository.updateSubmodule("android-tools") {
            it.copy(isSynced = true, commit = "commit123")
        }
        assertEquals(true, repository.currentState.submodules["android-tools"]?.isSynced)

        repository.updateTool("adb") {
            it.copy(isCompiled = true, isVerified = true, binaryPath = "/bin/adb", version = "1.0")
        }
        assertEquals(true, repository.currentState.tools["adb"]?.isVerified)
        assertEquals("/bin/adb", repository.currentState.tools["adb"]?.binaryPath)

        repository.markStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name, true)
        assertTrue(repository.currentState.isStageCompleted(SetupStepStage.REPO_SYNCHRONIZATION.name))

        repository.updateAvbKeyState(provisioned = true, keyPath = "/security/avb/key.pem")
        assertTrue(repository.currentState.isAvbKeyProvisioned)
        assertEquals("/security/avb/key.pem", repository.currentState.avbKeyPath)

        repository.updateDiagnosticsSnapshot(setOf("diag1", "diag2"))
        assertEquals(setOf("diag1", "diag2"), repository.currentState.passedDiagnosticIds)

        repository.markSetupCompleted(true)
        assertTrue(repository.currentState.isSetupCompleted)

        repository.clearToolchainState()
        assertFalse(repository.currentState.isSetupCompleted)
        assertTrue(repository.currentState.tools.isEmpty())
    }
}
