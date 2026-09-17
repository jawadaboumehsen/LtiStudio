/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.common.event.PanelEventBus
import org.ide.lti.core.domain.panels.PanelManager
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.repository.target.TargetRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.usecase.device.ConnectWirelessDeviceUseCase
import org.ide.lti.core.domain.usecase.device.DisconnectDeviceUseCase
import org.ide.lti.core.domain.usecase.device.GetConnectedDevicesUseCase
import org.ide.lti.core.domain.usecase.device.GetSelectedConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.device.PairWirelessDeviceUseCase
import org.ide.lti.core.domain.usecase.device.RebootConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.device.RefreshConnectedDevicesUseCase
import org.ide.lti.core.domain.usecase.device.SelectConnectedDeviceUseCase
import org.ide.lti.core.domain.usecase.target.AddTargetUseCase
import org.ide.lti.core.domain.usecase.target.GetAvailableTargetsUseCase
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceConnectionType
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode
import org.ide.lti.core.model.target.TargetBinding
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.target.WirelessPairingRequest
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.RecentProject
import org.ide.lti.core.model.workspace.Workspace
import org.ide.lti.core.model.workspace.WorkspaceSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceViewModelTest {

    private class FakeSnapshotRepository : SnapshotRepository {
        override val snapshots = MutableStateFlow<List<ConfigurationSnapshot>>(emptyList())

        override fun snapshotsForWorkspace(workspaceId: String): Flow<List<ConfigurationSnapshot>> = snapshots

        override suspend fun getSnapshot(id: String): ConfigurationSnapshot? =
            snapshots.value.find { snapshot -> snapshot.id == id }

        override suspend fun saveSnapshot(snapshot: ConfigurationSnapshot) {
            snapshots.value = snapshots.value + snapshot
        }
    }

    private class FakeWorkspaceRepository : WorkspaceRepository {
        override fun getWorkspaces(): Flow<List<Workspace>> = emptyFlow()
        override suspend fun getWorkspace(id: String): Workspace? = null
        override suspend fun saveWorkspace(workspace: Workspace) {}
        override suspend fun deleteWorkspace(id: String) {}
        override suspend fun getWorkspaceSession(workspaceId: String): WorkspaceSession? = null
        override suspend fun saveWorkspaceSession(session: WorkspaceSession) {}
        override fun getRecentProjects(): Flow<List<RecentProject>> = emptyFlow()
        override suspend fun addRecentProject(project: RecentProject) {}
        override suspend fun removeRecentProject(workspaceId: String) {}
        override suspend fun clearRecentProjects() {}
        override suspend fun discoverWorkspacesInWorkDir(basePath: String?): List<RecentProject> = emptyList()
    }

    private class FakeTargetRepository(
        val targets: List<TargetDevice>,
        initialSelected: TargetDevice,
    ) : TargetRepository {
        val targetsFlow = MutableStateFlow(targets)
        val selectedTargetFlow = MutableStateFlow(initialSelected)

        override fun getAvailableTargets(): Flow<List<TargetDevice>> = targetsFlow.asStateFlow()
        override fun getSelectedTarget(): Flow<TargetDevice> = selectedTargetFlow.asStateFlow()

        override suspend fun selectTarget(targetId: String): Result<TargetDevice> {
            val found = targetsFlow.value.find { it.id == targetId }
            return if (found != null) {
                selectedTargetFlow.value = found
                Result.success(found)
            } else {
                Result.failure(IllegalArgumentException("Target not found: $targetId"))
            }
        }

        override suspend fun addTarget(target: TargetDevice): Result<TargetDevice> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == target.id }
            list.add(target)
            targetsFlow.value = list
            return Result.success(target)
        }

        override suspend fun updateTarget(target: TargetDevice): Result<TargetDevice> {
            return addTarget(target)
        }

        override suspend fun deleteTarget(targetId: String): Result<Unit> {
            val list = targetsFlow.value.toMutableList()
            list.removeAll { it.id == targetId }
            targetsFlow.value = list
            return Result.success(Unit)
        }
    }

    private fun createTestFixture(): Triple<WorkspaceViewModel, FakeTargetRepository, WorkspaceManager> {
        val scope = CoroutineScope(Dispatchers.Unconfined)
        val repo = FakeWorkspaceRepository()
        val workspaceManager = WorkspaceManager(repo, scope)
        val eventBus = PanelEventBus()
        val panelManager = PanelManager(eventBus, scope)

        val globalFw = TargetFirmware(
            version = "REDMAGICOS10.5.15_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
            isOfficial = true,
            isRecommended = true,
        )
        val chinaFw = TargetFirmware(
            version = "REDMAGICOS10.5.16_MR01A",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260315.191244",
            isOfficial = true,
            isRecommended = true,
        )

        val target = TargetDevice(
            id = "PQ84P01",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
            availableFirmwares = mapOf(
                TargetRegion.GLOBAL to listOf(globalFw),
                TargetRegion.CHINA to listOf(chinaFw),
            ),
            socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor"),
            bootPartitions = listOf("boot", "init_boot"),
            status = TargetStatus.QUALIFIED,
            description = "Official Global baseline",
            isDefault = true,
        )

        val fakeTargetRepo = FakeTargetRepository(listOf(target), target)
        val snapshotRepository = FakeSnapshotRepository()
        val getAvailableTargetsUseCase = GetAvailableTargetsUseCase(fakeTargetRepo)
        val selectTargetUseCase = SelectTargetUseCase(fakeTargetRepo)
        val addTargetUseCase = AddTargetUseCase(fakeTargetRepo)

        val fakeDeviceRepo = FakeConnectedDeviceRepository()
        val getConnectedDevicesUseCase = GetConnectedDevicesUseCase(fakeDeviceRepo)
        val getSelectedConnectedDeviceUseCase = GetSelectedConnectedDeviceUseCase(fakeDeviceRepo)
        val selectConnectedDeviceUseCase = SelectConnectedDeviceUseCase(fakeDeviceRepo)
        val refreshConnectedDevicesUseCase = RefreshConnectedDevicesUseCase(fakeDeviceRepo)
        val rebootConnectedDeviceUseCase = RebootConnectedDeviceUseCase(fakeDeviceRepo)
        val pairWirelessDeviceUseCase = PairWirelessDeviceUseCase(fakeDeviceRepo)
        val connectWirelessDeviceUseCase = ConnectWirelessDeviceUseCase(fakeDeviceRepo)
        val disconnectDeviceUseCase = DisconnectDeviceUseCase(fakeDeviceRepo)

        val viewModel = WorkspaceViewModel(
            workspaceManager = workspaceManager,
            panelManager = panelManager,
            getAvailableTargetsUseCase = getAvailableTargetsUseCase,
            selectTargetUseCase = selectTargetUseCase,
            snapshotRepository = snapshotRepository,
            addTargetUseCase = addTargetUseCase,
            getConnectedDevicesUseCase = getConnectedDevicesUseCase,
            getSelectedConnectedDeviceUseCase = getSelectedConnectedDeviceUseCase,
            selectConnectedDeviceUseCase = selectConnectedDeviceUseCase,
            refreshConnectedDevicesUseCase = refreshConnectedDevicesUseCase,
            rebootConnectedDeviceUseCase = rebootConnectedDeviceUseCase,
            pairWirelessDeviceUseCase = pairWirelessDeviceUseCase,
            connectWirelessDeviceUseCase = connectWirelessDeviceUseCase,
            disconnectDeviceUseCase = disconnectDeviceUseCase,
        )

        return Triple(viewModel, fakeTargetRepo, workspaceManager)
    }

    private class FakeConnectedDeviceRepository(
        initialDevices: List<ConnectedTargetDevice> = listOf(
            ConnectedTargetDevice(
                serial = "PQ84P01-ASTRA-01",
                modelName = "REDMAGIC Astra",
                state = AdbDeviceState.ONLINE,
                connectionType = DeviceConnectionType.USB,
            ),
        ),
    ) : ConnectedDeviceRepository {
        val devicesFlow = MutableStateFlow(initialDevices)
        val selectedDeviceFlow = MutableStateFlow(initialDevices.firstOrNull())

        override fun getConnectedDevices(): StateFlow<List<ConnectedTargetDevice>> = devicesFlow.asStateFlow()
        override fun getSelectedConnectedDevice(): StateFlow<ConnectedTargetDevice?> = selectedDeviceFlow.asStateFlow()

        override suspend fun selectConnectedDevice(serial: String?) {
            selectedDeviceFlow.value = devicesFlow.value.find { it.serial == serial }
        }

        override suspend fun refreshConnectedDevices() {}

        override suspend fun rebootConnectedDevice(serial: String, mode: DeviceRebootMode): Result<DeviceOperationResult> {
            val targetState = when (mode) {
                DeviceRebootMode.BOOTLOADER, DeviceRebootMode.FASTBOOTD -> AdbDeviceState.FASTBOOT
                DeviceRebootMode.RECOVERY -> AdbDeviceState.RECOVERY
                DeviceRebootMode.SIDELOAD -> AdbDeviceState.SIDELOAD
                else -> AdbDeviceState.ONLINE
            }
            devicesFlow.value = devicesFlow.value.map { dev ->
                if (dev.serial == serial) dev.copy(state = targetState) else dev
            }
            if (selectedDeviceFlow.value?.serial == serial) {
                selectedDeviceFlow.value = selectedDeviceFlow.value?.copy(state = targetState)
            }
            return Result.success(DeviceOperationResult(isSuccess = true, message = "Rebooted to ${mode.displayName}"))
        }

        override suspend fun pairWirelessDevice(request: WirelessPairingRequest): Result<DeviceOperationResult> {
            val newDevice = ConnectedTargetDevice(
                serial = "${request.host}:${request.port}",
                modelName = "Wireless Device (${request.host})",
                state = AdbDeviceState.ONLINE,
                connectionType = DeviceConnectionType.WIRELESS,
            )
            devicesFlow.value = devicesFlow.value + newDevice
            selectedDeviceFlow.value = newDevice
            return Result.success(DeviceOperationResult(isSuccess = true, message = "Paired successfully"))
        }

        override suspend fun connectWirelessDevice(host: String, port: Int): Result<DeviceOperationResult> {
            return Result.success(DeviceOperationResult(isSuccess = true, message = "Connected to $host:$port"))
        }

        override suspend fun disconnectDevice(target: String) {
            devicesFlow.value = devicesFlow.value.filterNot { it.serial == target }
            if (selectedDeviceFlow.value?.serial == target) {
                selectedDeviceFlow.value = devicesFlow.value.firstOrNull()
            }
        }
    }

    @Test
    fun testInitialTargetSelection() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _, _) = createTestFixture()
            val collectTarget = backgroundScope.launch(testDispatcher) { viewModel.selectedTarget.collect() }

            testScheduler.advanceUntilIdle()

            assertEquals("Unknown", viewModel.selectedTarget.value.name)
            assertEquals("Unknown", viewModel.selectedTarget.value.codename)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testBoundWorkspaceTargetWinsOverGlobalSelection() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, fakeTargetRepo, workspaceManager) = createTestFixture()
            backgroundScope.launch(testDispatcher) { viewModel.selectedTarget.collect() }
            backgroundScope.launch(testDispatcher) { viewModel.availableTargets.collect() }

            val second = viewModel.selectedTarget.value.copy(
                id = "NX769J",
                codename = "NX769J",
                name = "REDMAGIC 9 Pro",
            )
            fakeTargetRepo.addTarget(second)
            workspaceManager.setCurrentWorkspace(
                Workspace(
                    id = "ws-1",
                    name = "Bound",
                    path = "/home/lti/LtiRomWorkDir/workspaces/bound",
                    targetBinding = TargetBinding("NX769J", 1),
                ),
            )
            testScheduler.advanceUntilIdle()

            // The global selection is still PQ84P01, but the workspace binding decides.
            assertEquals("NX769J", viewModel.selectedTarget.value.id)

            workspaceManager.setCurrentWorkspace(null)
            testScheduler.advanceUntilIdle()
            assertEquals("Unknown", viewModel.selectedTarget.value.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testAddTargetAndSwitching() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _, workspaceManager) = createTestFixture()
            val collectAvailable = backgroundScope.launch(testDispatcher) { viewModel.availableTargets.collect() }
            val collectTarget = backgroundScope.launch(testDispatcher) { viewModel.selectedTarget.collect() }

            val customFw = TargetFirmware(
                version = "REDMAGICOS10.5.20_NX769J_GB",
                buildId = "NX769J:15/AQ3A.240812.002/20260401.120000",
                isOfficial = true,
                isRecommended = true,
            )
            val newDevice = TargetDevice(
                id = "NX769J",
                name = "REDMAGIC 9 Pro",
                codename = "NX769J",
                availableRegions = listOf(TargetRegion.GLOBAL),
                availableFirmwares = mapOf(TargetRegion.GLOBAL to listOf(customFw)),
                socPlatform = "Snapdragon 8 Gen 3 (SM8650 / pineapple)",
                filesystemType = "erofs",
                superPartitionBytes = 15032385536L,
                dynamicPartitions = listOf("system", "vendor", "product"),
                bootPartitions = listOf("boot", "init_boot"),
                status = TargetStatus.QUALIFIED,
                description = "REDMAGIC 9 Pro Global",
            )

            viewModel.onAddTarget(newDevice)
            testScheduler.advanceUntilIdle()

            // Catalog selection never becomes a workspace header fallback. A saved
            // workspace binding is required before the target is shown as active.
            workspaceManager.setCurrentWorkspace(
                Workspace(
                    id = "ws-new-target",
                    name = "New target",
                    path = "/home/lti/LtiRomWorkDir/workspaces/new-target",
                    targetBinding = TargetBinding("NX769J", 1),
                ),
            )
            testScheduler.advanceUntilIdle()

            assertEquals("NX769J", viewModel.selectedTarget.value.id)
            assertEquals("REDMAGIC 9 Pro", viewModel.selectedTarget.value.name)
            assertTrue(viewModel.availableTargets.value.any { it.id == "NX769J" })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testSaveTargetConfiguration() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, fakeTargetRepo, workspaceManager) = createTestFixture()
            val collectAvailable = backgroundScope.launch(testDispatcher) { viewModel.availableTargets.collect() }
            val collectTarget = backgroundScope.launch(testDispatcher) { viewModel.selectedTarget.collect() }

            workspaceManager.setCurrentWorkspace(
                Workspace(
                    id = "ws-save-target",
                    name = "Save target",
                    path = "/home/lti/LtiRomWorkDir/workspaces/save-target",
                    targetBinding = TargetBinding("PQ84P01", 1),
                ),
            )
            testScheduler.advanceUntilIdle()

            val updated = viewModel.selectedTarget.value.copy(
                name = "REDMAGIC Astra Modified",
                socPlatform = "SM8750 Extreme",
            )

            viewModel.onSaveTarget(updated)
            testScheduler.advanceUntilIdle()

            val saved = fakeTargetRepo.targetsFlow.value.firstOrNull { it.id == "PQ84P01" }
            assertEquals("REDMAGIC Astra Modified", saved?.name)
            assertEquals("SM8750 Extreme", saved?.socPlatform)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testConnectedDevicesInitialStateAndSelection() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _, _) = createTestFixture()
            val collectDevices = backgroundScope.launch(testDispatcher) { viewModel.connectedDevices.collect() }
            val collectSelected = backgroundScope.launch(testDispatcher) { viewModel.selectedConnectedDevice.collect() }

            // Initially has default device
            assertEquals(1, viewModel.connectedDevices.value.size)
            assertEquals("PQ84P01-ASTRA-01", viewModel.selectedConnectedDevice.value?.serial)
            assertEquals(org.ide.lti.core.model.target.AdbDeviceState.ONLINE, viewModel.selectedConnectedDevice.value?.state)

            // Select null
            viewModel.selectConnectedDevice(null)
            assertEquals(null, viewModel.selectedConnectedDevice.value)

            // Re-select by serial
            viewModel.selectConnectedDevice("PQ84P01-ASTRA-01")
            assertEquals("PQ84P01-ASTRA-01", viewModel.selectedConnectedDevice.value?.serial)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testRebootConnectedDeviceModeTransition() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _, _) = createTestFixture()
            val collectDevices = backgroundScope.launch(testDispatcher) { viewModel.connectedDevices.collect() }
            val collectSelected = backgroundScope.launch(testDispatcher) { viewModel.selectedConnectedDevice.collect() }

            // Reboot to bootloader / fastboot
            viewModel.rebootConnectedDevice("PQ84P01-ASTRA-01", "bootloader")
            testScheduler.advanceUntilIdle()

            assertEquals(org.ide.lti.core.model.target.AdbDeviceState.FASTBOOT, viewModel.selectedConnectedDevice.value?.state)

            // Reboot to recovery
            viewModel.rebootConnectedDevice("PQ84P01-ASTRA-01", "recovery")
            testScheduler.advanceUntilIdle()

            assertEquals(org.ide.lti.core.model.target.AdbDeviceState.RECOVERY, viewModel.selectedConnectedDevice.value?.state)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun testWirelessPairingWorkflow() = runTest {
        val testDispatcher = UnconfinedTestDispatcher(testScheduler)
        Dispatchers.setMain(testDispatcher)
        try {
            val (viewModel, _, _) = createTestFixture()
            val collectDevices = backgroundScope.launch(testDispatcher) { viewModel.connectedDevices.collect() }
            val collectSelected = backgroundScope.launch(testDispatcher) { viewModel.selectedConnectedDevice.collect() }

            var callbackInvoked = false
            viewModel.pairWirelessDevice(
                WirelessPairingRequest(
                    host = "192.168.1.55",
                    port = 38521,
                    pairingCode = "654321",
                ),
            ) { success, _ ->
                callbackInvoked = true
                assertTrue(success)
            }
            testScheduler.advanceUntilIdle()

            assertTrue(callbackInvoked)
            assertEquals(2, viewModel.connectedDevices.value.size)
            assertEquals("192.168.1.55:38521", viewModel.selectedConnectedDevice.value?.serial)
            assertEquals(DeviceConnectionType.WIRELESS, viewModel.selectedConnectedDevice.value?.connectionType)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
