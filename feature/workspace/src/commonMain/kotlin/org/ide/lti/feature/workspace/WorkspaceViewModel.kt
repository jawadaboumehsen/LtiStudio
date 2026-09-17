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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.event.PanelId
import org.ide.lti.core.domain.panels.PanelManager
import org.ide.lti.core.domain.repository.snapshot.SnapshotRepository
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
import org.ide.lti.core.model.panels.PanelState
import org.ide.lti.core.model.target.AdbDeviceState
import org.ide.lti.core.model.target.ConnectedTargetDevice
import org.ide.lti.core.model.target.DeviceConnectionType
import org.ide.lti.core.model.target.DeviceOperationResult
import org.ide.lti.core.model.target.DeviceRebootMode
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.target.WirelessPairingRequest
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/**
 * Presentation ViewModel for the LtiRom Studio Workspace.
 *
 * Responsibilities:
 * - Layout, panel toggling, and tab orchestration (Explorer, Terminal, Editor, AI, Target Cockpit).
 * - Reactive workspace metadata & branch synchronization.
 * - Bound target profile resolution from the workspace's immutable binding only.
 * - Hardware ADB / Fastboot device discovery, pairing, and reboot transitions.
 */
class WorkspaceViewModel(
    private val workspaceManager: WorkspaceManager,
    private val panelManager: PanelManager,
    private val getAvailableTargetsUseCase: GetAvailableTargetsUseCase,
    private val selectTargetUseCase: SelectTargetUseCase,
    private val snapshotRepository: SnapshotRepository,
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
        /** UI-only unresolved state; never a selectable, persisted, or executable target. */
        val UNKNOWN_TARGET: TargetDevice = TargetDevice.EMPTY.copy(
            name = "Unknown",
            codename = "Unknown",
            filesystemType = "unknown",
        )
    }

    val currentWorkspace: StateFlow<Workspace?> = workspaceManager.currentWorkspace

    val effectiveSnapshot: StateFlow<ConfigurationSnapshot?> = workspaceManager.currentWorkspace
        .map { workspace ->
            workspace?.effectiveSnapshotId?.let { snapshotId ->
                snapshotRepository.getSnapshot(snapshotId)
            }
        }
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
            initialValue = emptyList(),
        )

    /**
     * The profile this workspace is bound to. Unbound or unresolved records deliberately expose
     * [UNKNOWN_TARGET]; callers render it as Unknown instead of fabricating a Global target.
     */
    val selectedTarget: StateFlow<TargetDevice> = combine(
        workspaceManager.currentWorkspace,
        getAvailableTargetsUseCase(),
    ) { workspace, targets ->
        val boundId = workspace?.targetBinding?.profileId
        targets.firstOrNull { it.id == boundId } ?: UNKNOWN_TARGET
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UNKNOWN_TARGET,
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
