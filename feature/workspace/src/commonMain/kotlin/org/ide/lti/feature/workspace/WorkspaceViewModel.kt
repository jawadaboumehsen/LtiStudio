/*
 * Copyright 2025-2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.event.PanelId
import org.ide.lti.core.domain.panels.PanelManager
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
import org.ide.lti.core.domain.usecase.target.GetSelectedTargetUseCase
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
import org.ide.lti.core.domain.usecase.target.SelectTargetUseCase
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.ide.lti.core.model.panels.PanelState
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceConnectionType
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.TargetFirmware
import org.ide.lti.core.model.target.TargetRegion
import org.ide.lti.core.model.target.TargetStatus
import org.ide.lti.core.model.target.WirelessPairingRequest
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/**
 * Presentation ViewModel for the LtiRom Studio Workspace.
 *
 * Responsibilities:
 * - Layout, panel toggling, and tab orchestration (Explorer, Terminal, Editor, AI, Target Cockpit).
 * - Reactive workspace metadata & branch synchronization.
 * - Bound target profile resolution (workspace binding first, global selection as fallback).
 * - Hardware ADB / Fastboot device discovery, pairing, and reboot transitions.
 */
class WorkspaceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val panelManager: PanelManager,
    private val getAvailableTargetsUseCase: GetAvailableTargetsUseCase,
    private val getSelectedTargetUseCase: GetSelectedTargetUseCase,
    private val selectTargetUseCase: SelectTargetUseCase,
    private val snapshotRepository: SnapshotRepository? = null,
    private val addTargetUseCase: AddTargetUseCase? = null,
    private val getConnectedDevicesUseCase: GetConnectedDevicesUseCase? = null,
    private val getSelectedConnectedDeviceUseCase: GetSelectedConnectedDeviceUseCase? = null,
    private val selectConnectedDeviceUseCase: SelectConnectedDeviceUseCase? = null,
    private val refreshConnectedDevicesUseCase: RefreshConnectedDevicesUseCase? = null,
    private val rebootConnectedDeviceUseCase: RebootConnectedDeviceUseCase? = null,
    private val pairWirelessDeviceUseCase: PairWirelessDeviceUseCase? = null,
    private val connectWirelessDeviceUseCase: ConnectWirelessDeviceUseCase? = null,
    private val disconnectDeviceUseCase: DisconnectDeviceUseCase? = null,
) : ViewModel() {

    companion object {
        private val DEFAULT_GLOBAL_FIRMWARE = TargetFirmware(
            version = "REDMAGICOS10.5.15_NP05J_GB",
            buildId = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
            androidVersion = "15",
            securityPatch = "2026-02-01",
            otaUrl = "https://rom.download.nubia.com/Europe&Asia/PQ84P01(NP05J)/REDMAGICOS10.5.15_NP05J_GB/update.zip",
            isOfficial = true,
            isRecommended = true,
        )

        val DEFAULT_TARGET = TargetDevice(
            id = "PQ84P01",
            name = "REDMAGIC Astra Gaming Tablet",
            codename = "PQ84P01",
            availableRegions = listOf(TargetRegion.GLOBAL, TargetRegion.CHINA),
            availableFirmwares = mapOf(
                TargetRegion.GLOBAL to listOf(DEFAULT_GLOBAL_FIRMWARE),
            ),
            socPlatform = "Snapdragon 8 Elite (SM8750 / sun)",
            filesystemType = "erofs",
            superPartitionBytes = 17179869184L,
            dynamicPartitions = listOf("system", "vendor", "product", "system_ext", "odm", "vendor_dlkm", "system_dlkm"),
            bootPartitions = listOf("boot", "init_boot", "vendor_boot", "dtbo"),
            status = TargetStatus.QUALIFIED,
            description = "Official Global baseline",
            isDefault = true,
        )
    }

    val currentWorkspace: StateFlow<Workspace?> = workspaceManager.currentWorkspace

    val effectiveSnapshot: StateFlow<ConfigurationSnapshot?> = workspaceManager.currentWorkspace
        .map { ws -> ws?.effectiveSnapshotId?.let { snapshotRepository?.getSnapshot(it) } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null,
        )

    val workspaceName: StateFlow<String> = workspaceManager.currentWorkspace
        .map { it?.name ?: "Lti" }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = workspaceManager.currentWorkspace.value?.name ?: "Lti",
        )

    val branch: StateFlow<String> = MutableStateFlow("main")

    val panelsState = panelManager.panels

    // Target Selection State
    val availableTargets: StateFlow<List<TargetDevice>> = getAvailableTargetsUseCase()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = listOf(DEFAULT_TARGET),
        )

    /**
     * The profile this workspace is bound to. Records without a binding (NEEDS_TARGET) fall back
     * to the global selection until US5 lets the user bind them.
     */
    val selectedTarget: StateFlow<TargetDevice> = combine(
        workspaceManager.currentWorkspace,
        getAvailableTargetsUseCase(),
        getSelectedTargetUseCase(),
    ) { workspace, targets, globalSelection ->
        val boundId = workspace?.targetBinding?.profileId
        targets.firstOrNull { it.id == boundId } ?: globalSelection
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DEFAULT_TARGET,
    )

    // Connected ADB / Fastboot Physical & Emulated Devices
    private val _fallbackConnectedDevices = MutableStateFlow<List<ConnectedTargetDevice>>(emptyList())
    val connectedDevices: StateFlow<List<ConnectedTargetDevice>> = getConnectedDevicesUseCase?.invoke()
        ?: _fallbackConnectedDevices.asStateFlow()

    private val _fallbackSelectedDevice = MutableStateFlow<ConnectedTargetDevice?>(null)
    val selectedConnectedDevice: StateFlow<ConnectedTargetDevice?> = getSelectedConnectedDeviceUseCase?.invoke()
        ?: _fallbackSelectedDevice.asStateFlow()

    private val _isRefreshingDevices = MutableStateFlow(false)
    val isRefreshingDevices: StateFlow<Boolean> = _isRefreshingDevices.asStateFlow()

    private val _deviceOperationStatus = MutableStateFlow<String?>(null)
    val deviceOperationStatus: StateFlow<String?> = _deviceOperationStatus.asStateFlow()

    fun selectConnectedDevice(serial: String?) {
        viewModelScope.launch {
            if (selectConnectedDeviceUseCase != null) {
                selectConnectedDeviceUseCase(serial)
            } else {
                _fallbackSelectedDevice.value = _fallbackConnectedDevices.value.find { it.serial == serial }
            }
        }
    }

    fun refreshConnectedDevices() {
        if (_isRefreshingDevices.value) return
        _isRefreshingDevices.value = true
        viewModelScope.launch {
            try {
                if (refreshConnectedDevicesUseCase != null) {
                    refreshConnectedDevicesUseCase()
                } else {
                    kotlinx.coroutines.delay(350)
                }
            } finally {
                _isRefreshingDevices.value = false
            }
        }
    }

    fun rebootConnectedDevice(serial: String, mode: DeviceRebootMode) {
        viewModelScope.launch {
            _deviceOperationStatus.value = "Rebooting device $serial to ${mode.displayName}..."
            if (rebootConnectedDeviceUseCase != null) {
                val res = rebootConnectedDeviceUseCase(serial, mode)
                res.fold(
                    onSuccess = { op ->
                        _deviceOperationStatus.value = if (op.isSuccess) {
                            "Reboot to ${mode.displayName} sent successfully."
                        } else {
                            "Reboot failed: ${op.output.ifBlank { op.message }}"
                        }
                    },
                    onFailure = { err ->
                        _deviceOperationStatus.value = "Reboot error: ${err.message}"
                    }
                )
            } else {
                val targetState = when (mode) {
                    DeviceRebootMode.BOOTLOADER, DeviceRebootMode.FASTBOOTD -> AdbDeviceState.FASTBOOT
                    DeviceRebootMode.RECOVERY -> AdbDeviceState.RECOVERY
                    DeviceRebootMode.SIDELOAD -> AdbDeviceState.SIDELOAD
                    else -> AdbDeviceState.ONLINE
                }
                _fallbackConnectedDevices.update { devices ->
                    devices.map { device ->
                        if (device.serial == serial) device.copy(state = targetState) else device
                    }
                }
                _fallbackSelectedDevice.update { current ->
                    if (current?.serial == serial) current.copy(state = targetState) else current
                }
            }
        }
    }

    fun rebootConnectedDevice(serial: String, mode: String) {
        rebootConnectedDevice(serial, DeviceRebootMode.fromString(mode))
    }

    fun pairWirelessDevice(request: WirelessPairingRequest, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _deviceOperationStatus.value = "Pairing with ${request.host}:${request.port}..."
            if (pairWirelessDeviceUseCase != null) {
                val res = pairWirelessDeviceUseCase(request)
                res.fold(
                    onSuccess = { op ->
                        _deviceOperationStatus.value = op.message
                        onResult(op.isSuccess, op.message)
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Pairing failed"
                        _deviceOperationStatus.value = msg
                        onResult(false, msg)
                    }
                )
            } else {
                onResult(true, "Pairing succeeded (simulated)")
            }
        }
    }

    fun connectWirelessDevice(host: String, port: Int, onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            _deviceOperationStatus.value = "Connecting to $host:$port..."
            if (connectWirelessDeviceUseCase != null) {
                val res = connectWirelessDeviceUseCase(host, port)
                res.fold(
                    onSuccess = { op ->
                        _deviceOperationStatus.value = op.message
                        onResult(op.isSuccess, op.message)
                    },
                    onFailure = { err ->
                        val msg = err.message ?: "Connection failed"
                        _deviceOperationStatus.value = msg
                        onResult(false, msg)
                    }
                )
            } else {
                onResult(true, "Connected to $host:$port (simulated)")
            }
        }
    }

    fun disconnectDevice(target: String) {
        viewModelScope.launch {
            _deviceOperationStatus.value = "Disconnecting $target..."
            disconnectDeviceUseCase?.invoke(target)
        }
    }

    fun clearDeviceOperationStatus() {
        _deviceOperationStatus.value = null
    }


    fun onSelectTarget(targetId: String) {
        viewModelScope.launch {
            selectTargetUseCase(targetId)
        }
    }

    fun onAddTarget(target: TargetDevice) {
        viewModelScope.launch {
            addTargetUseCase?.invoke(target)
            selectTargetUseCase(target.id)
        }
    }

    fun onSaveTarget(target: TargetDevice) {
        viewModelScope.launch {
            addTargetUseCase?.invoke(target)
        }
    }



    fun getPanelState(panelId: PanelId): StateFlow<PanelState?> {
        return panelsState.map { it[panelId] }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = panelManager.getPanelState(panelId),
            )
    }

    fun togglePanelVisibility(panelId: PanelId) {
        panelManager.togglePanelVisibility(panelId)
    }

    fun setPanelSize(panelId: PanelId, size: Float) {
        panelManager.setPanelSize(panelId, size)
    }

    fun setActiveTab(panelId: PanelId, tabIndex: Int) {
        panelManager.setActiveTab(panelId, tabIndex)
    }

    fun closeTab(panelId: PanelId, tabId: String) {
        panelManager.removeTab(panelId, tabId)
    }

    fun closeWorkspace() {
        viewModelScope.launch {
            workspaceManager.closeWorkspace()
        }
    }
}
