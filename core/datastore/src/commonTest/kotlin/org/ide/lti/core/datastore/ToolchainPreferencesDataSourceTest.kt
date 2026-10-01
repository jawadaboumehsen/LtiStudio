/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.datastore

import com.russhwolf.settings.MapSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.setup.PersistedSubmoduleState
import org.ide.lti.core.model.setup.PersistedToolState
import org.ide.lti.core.model.setup.ToolchainPersistenceState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolchainPreferencesDataSourceTest {

    @Test
    fun testDefaultStateIsEmpty() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val state = dataSource.toolchainState.first()
        assertFalse(state.isSetupCompleted)
        assertTrue(state.completedStages.isEmpty())
        assertTrue(state.tools.isEmpty())
        assertTrue(state.submodules.isEmpty())
        assertFalse(state.isAvbKeyProvisioned)
    }

    @Test
    fun testSaveAndRestoreCompleteState() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        val targetState = ToolchainPersistenceState(
            isSetupCompleted = true,
            lastCompletedTimestamp = 1725800000L,
            completedStages = setOf("WSL_DETECTION", "SERVER_CONNECTIVITY", "TOOLCHAIN_COMPILATION"),
            isAvbKeyProvisioned = true,
            avbKeyPath = "/home/user/LtiRomWorkDir/security/avb/lti_rsa4096.pem",
            tools = mapOf(
                "adb" to PersistedToolState(
                    id = "adb",
                    binaryName = "adb",
                    isCompiled = true,
                    isVerified = true,
                    version = "34.0.5",
                    binaryPath = "/home/user/LtiRomTools/bin/adb",
                ),
            ),
            submodules = mapOf(
                "android-tools" to PersistedSubmoduleState(
                    name = "android-tools",
                    isSynced = true,
                    commit = "2c5e1b7c",
                ),
            ),
        )

        dataSource.saveToolchainState(targetState)

        val loaded = dataSource.currentToolchainState
        assertTrue(loaded.isSetupCompleted)
        assertEquals(1725800000L, loaded.lastCompletedTimestamp)
        assertEquals(3, loaded.completedStages.size)
        assertTrue(loaded.isAvbKeyProvisioned)
        assertEquals("34.0.5", loaded.tools["adb"]?.version)
        assertTrue(loaded.isSubmoduleSynced("android-tools"))
        assertTrue(loaded.isToolCompiled("adb"))
    }

    @Test
    fun testIncrementalToolStateUpdate() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        dataSource.updateTool("mkfs.erofs") { tool ->
            tool.copy(
                isCompiled = true,
                binaryPath = "/home/user/LtiRomTools/bin/mkfs.erofs",
                version = "1.7.1",
            )
        }

        val stateAfterCompile = dataSource.currentToolchainState
        val tool = stateAfterCompile.tools["mkfs.erofs"]
        assertNotNull(tool)
        assertTrue(tool.isCompiled)
        assertFalse(tool.isVerified)
        assertEquals("/home/user/LtiRomTools/bin/mkfs.erofs", tool.binaryPath)

        dataSource.updateTool("mkfs.erofs") {
            it.copy(isVerified = true, lastVerifiedTimestamp = 9999L)
        }

        val stateAfterVerify = dataSource.currentToolchainState
        assertTrue(stateAfterVerify.tools["mkfs.erofs"]!!.isVerified)
        assertEquals(9999L, stateAfterVerify.tools["mkfs.erofs"]!!.lastVerifiedTimestamp)
    }

    @Test
    fun testIncrementalSubmoduleUpdate() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        dataSource.updateSubmodule("erofs-utils") { sub ->
            sub.copy(isSynced = true, commit = "24ebe947")
        }

        val state = dataSource.currentToolchainState
        assertTrue(state.isSubmoduleSynced("erofs-utils"))
        assertEquals("24ebe947", state.submodules["erofs-utils"]?.commit)
    }

    @Test
    fun testStageCompletionAndReset() = runTest {
        val settings = MapSettings()
        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)

        dataSource.markStageCompleted("WSL_DETECTION", true)
        dataSource.markStageCompleted("REPO_SYNCHRONIZATION", true)

        var state = dataSource.currentToolchainState
        assertTrue(state.isStageCompleted("WSL_DETECTION"))
        assertTrue(state.isStageCompleted("REPO_SYNCHRONIZATION"))
        assertFalse(state.isStageCompleted("TOOLCHAIN_COMPILATION"))

        dataSource.markStageCompleted("REPO_SYNCHRONIZATION", false)
        state = dataSource.currentToolchainState
        assertFalse(state.isStageCompleted("REPO_SYNCHRONIZATION"))

        dataSource.clearToolchainState()
        val cleared = dataSource.currentToolchainState
        assertTrue(cleared.completedStages.isEmpty())
        assertFalse(cleared.isSetupCompleted)
    }

    @Test
    fun testCorruptedJsonRecoversCleanlyWithoutCrash() = runTest {
        val settings = MapSettings()
        settings.putString(ToolchainPreferencesDataSource.KEY_TOOLCHAIN_PERSISTENCE_STATE, "{ corrupt_invalid_json: true ...")

        val dataSource = ToolchainPreferencesDataSource(settings = settings, ioDispatcher = Dispatchers.Unconfined)
        val state = dataSource.currentToolchainState
        assertNotNull(state)
        assertFalse(state.isSetupCompleted)
    }
}
