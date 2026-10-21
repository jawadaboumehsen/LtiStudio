/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.ide.lti.core.designsystem.theme.AppTheme
import org.ide.lti.core.designsystem.theme.LtiTheme
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.WorkspaceDeviceRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.junit.Test
import java.io.File

/** Renders the Device section the way the Studio's ROM Configuration panel hosts it. */
@OptIn(ExperimentalTestApi::class)
class WorkspaceDeviceSectionUiTest {
    private class Workspaces(ws: Workspace) : WorkspaceRepository {
        private val all = MutableStateFlow(listOf(ws))
        override fun getWorkspaces(): Flow<List<Workspace>> = all
        override suspend fun getWorkspace(id: String): Workspace? = all.value.firstOrNull { it.id == id }
        override suspend fun saveWorkspace(workspace: Workspace) {
            all.value = all.value.filterNot { it.id == workspace.id } + workspace
        }
        override suspend fun deleteWorkspace(id: String) = Unit
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) = Unit
        override fun getRecentProjects(): Flow<List<RecentProject>> = emptyFlow()
        override suspend fun addRecentProject(project: RecentProject) = Unit
        override suspend fun removeRecentProject(workspaceId: String) = Unit
        override suspend fun clearRecentProjects() = Unit
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
    }

    private class Snapshots(private val snapshot: ConfigurationSnapshot) : SnapshotRepository {
        override val snapshots: Flow<List<ConfigurationSnapshot>> = flowOf(listOf(snapshot))
        override fun snapshotsForWorkspace(workspaceId: String) = snapshots
        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? = snapshot.takeIf { it.id == id }
        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) = Unit
    }

    private class Devices(device: TargetDevice?) : WorkspaceDeviceRepository {
        private val stored = MutableStateFlow(device)
        override fun observe(workspaceId: String): Flow<TargetDevice?> = stored.map { it }
        override suspend fun get(workspaceId: String): TargetDevice? = stored.value
        override suspend fun save(workspaceId: String, device: TargetDevice): TargetDevice =
            device.also { stored.value = it }
    }

    private fun viewModel(device: TargetDevice?): WorkspaceDeviceViewModel {
        val template = DefaultTargetCatalog.PQ84P01_DEFAULT
        val snapshot = ConfigurationSnapshot.create(
            id = "snap-1",
            workspaceId = "ws-1",
            profileRevision = 1,
            acquisition = AcquisitionSettings(
                region = TargetRegion.GLOBAL,
                firmware = template.availableFirmwares.getValue(TargetRegion.GLOBAL).first(),
            ),
            createdAt = Clock.System.now(),
        )
        val ws = Workspace(id = "ws-1", name = "W", path = "/w", linuxPath = "/w", effectiveSnapshotId = "snap-1")
        val manager = WorkspaceManager(Workspaces(ws), CoroutineScope(Dispatchers.Unconfined))
        runBlocking { manager.setCurrentWorkspace(ws) }
        return WorkspaceDeviceViewModel(manager, Devices(device), Snapshots(snapshot))
    }

    private fun render(device: TargetDevice?, file: String, check: androidx.compose.ui.test.ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = 900, height = 2400) {
            val vm = viewModel(device)
            setContent {
                LtiTheme(appTheme = AppTheme.Dark) {
                    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) { WorkspaceDeviceSection(vm) }
                }
            }
            waitForIdle()
            val png = Image.makeFromBitmap(onRoot().captureToImage().asSkiaBitmap())
                .encodeToData(EncodedImageFormat.PNG)!!.bytes
            File("build/device-section").apply { mkdirs() }.resolve(file).writeBytes(png)
            check()
        }

    @Test
    fun theDeviceOfTheWorkspaceIsShownWithItsFieldsAndActions() =
        render(DefaultTargetCatalog.PQ84P01_DEFAULT, "device.png") {
            onNodeWithText("Device").assertExists()
            onNodeWithText("Fastboot product").assertExists()
            onNodeWithText("Save device").assertExists()
        }

    @Test
    fun aWorkspaceWithoutADeviceSaysSo() = render(null, "no-device.png") {
        onNodeWithText("No device configuration").assertExists()
    }
}
