/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.stages.PreflightReport
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.repository.run.RunRepository
import org.ide.lti.core.domain.workspace.FirmwareUpgrade
import org.ide.lti.core.domain.workspace.NextBuild
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.domain.workspace.SamePhoneSettings
import org.ide.lti.core.domain.workspace.UpgradeSection
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.Workspace

data class PreflightUiState(
    val findings: List<PreflightFinding>? = null,
    val checking: Boolean = false,
    val error: String? = null,
    /** Other workspaces of the same phone, whose settings can be carried here. */
    val samePhone: List<Workspace> = emptyList(),
    val message: String? = null,
    /** The upgrade report for taking [comparedWith]'s settings: what changed and what still fits. */
    val upgrade: List<UpgradeSection>? = null,
    val comparedWith: Workspace? = null,
    val comparing: Boolean = false,
    /** What building now would do: changes since the last build that worked, stages that run, the estimate. */
    val next: NextBuildFacts? = null,
    val readingNext: Boolean = false,
    val nextError: String? = null,
    val vintf: VintfAssessment? = null,
    val inputsChanged: Boolean = false,
)

/** Will it build? Runs the preflight and applies a finding's settings fix, then checks again. */
public class PreflightViewModel internal constructor(
    private val workspaceId: String,
    private val report: (suspend (String) -> PreflightReport)?,
    private val settings: WorkspaceSettingsStore? = null,
    private val samePhone: SamePhoneSettings? = null,
    private val upgrade: FirmwareUpgrade? = null,
    private val nextBuild: NextBuild? = null,
    private val runs: RunRepository? = null,
) : ViewModel() {
    public constructor(
        workspaceId: String,
        preflight: Preflight? = null,
        settings: WorkspaceSettingsStore? = null,
        samePhone: SamePhoneSettings? = null,
        upgrade: FirmwareUpgrade? = null,
        nextBuild: NextBuild? = null,
        runs: RunRepository? = null,
    ) : this(workspaceId, preflight?.let { it::report }, settings, samePhone, upgrade, nextBuild, runs)

    private val _state = MutableStateFlow(PreflightUiState())
    public val state: StateFlow<PreflightUiState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var nextJob: Job? = null

    init {
        settings?.let { store ->
            viewModelScope.launch {
                store.changes.collect { if (it == workspaceId) inputsChanged(recheckPending = true) }
            }
            viewModelScope.launch {
                store.deviceUpdates(workspaceId).distinctUntilChanged().drop(1).collect { inputsChanged() }
            }
        }
        runs?.let { repository ->
            viewModelScope.launch {
                repository.observeRuns(workspaceId)
                    .map { list -> list.map { Triple(it.id, it.state, it.stages) } }
                    .distinctUntilChanged()
                    .drop(1)
                    .collect { inputsChanged() }
            }
        }
        samePhone?.let { finder ->
            viewModelScope.launch {
                val others = runCatchingCancellable { finder.others(workspaceId) }.getOrDefault(emptyList())
                _state.update { it.copy(samePhone = others) }
            }
        }
    }

    /** The upgrade assistant: what of [from]'s settings fits this workspace's firmware, before taking them. */
    public fun compareWith(from: Workspace) {
        val assistant = upgrade ?: return
        _state.update { it.copy(comparing = true, comparedWith = from, upgrade = null, error = null) }
        viewModelScope.launch {
            try {
                val report = assistant.compare(from.id, workspaceId)
                _state.update { if (it.comparedWith?.id == from.id) it.copy(upgrade = report) else it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(comparing = false) }
            }
        }
    }

    /** Copies [from]'s phone settings (assembly, debloat, mods, build) here, then checks again. */
    public fun carryFrom(from: Workspace) {
        val finder = samePhone ?: return
        viewModelScope.launch {
            finder.carry(from.id, workspaceId)
                .onSuccess {
                    inputsChanged()
                    _state.update { it.copy(message = "Took the settings of ${from.name}.") }
                    check()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The settings could not be copied") } }
        }
    }

    public fun check() {
        if (_state.value.checking || _state.value.readingNext) return
        val checker = report ?: return readNext()
        _state.update {
            it.copy(
                checking = true, error = null, findings = null, vintf = null, inputsChanged = false,
                next = null, nextError = null,
            )
        }
        checkJob = viewModelScope.launch {
            try {
                settings?.flush(workspaceId)?.getOrThrow()
                coroutineContext.ensureActive()
                readNext()
                val report = checker(workspaceId)
                coroutineContext.ensureActive()
                _state.update { it.copy(findings = report.findings, vintf = report.vintf, checking = false) }
            } catch (e: CancellationException) {
                if (coroutineContext.isActive) _state.update { it.copy(checking = false) }
                throw e
            } catch (e: RefusalException) {
                coroutineContext.ensureActive()
                _state.update { it.copy(checking = false, error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                coroutineContext.ensureActive()
                _state.update { it.copy(checking = false, error = e.message ?: e::class.simpleName) }
            }
        }
    }

    /** Reads what building now would do, beside the blockers (a longer read: work/ is hashed like a run start). */
    private fun readNext() {
        val reader = nextBuild ?: return
        if (_state.value.readingNext) return
        _state.update { it.copy(readingNext = true, next = null, nextError = null) }
        nextJob = viewModelScope.launch {
            val result = runCatchingCancellable { reader.of(workspaceId) }
            coroutineContext.ensureActive()
            _state.update { state ->
                result.fold(
                    onSuccess = { state.copy(readingNext = false, next = it) },
                    onFailure = { e ->
                        state.copy(readingNext = false, nextError = "What the next build does: ${e.message}")
                    },
                )
            }
        }
    }

    private fun inputsChanged(recheckPending: Boolean = false) {
        val pending = _state.value.checking
        checkJob?.cancel()
        nextJob?.cancel()
        _state.update {
            it.copy(
                inputsChanged = it.inputsChanged || it.checking || it.vintf != null || it.next != null,
                checking = false,
                findings = null,
                vintf = null,
                readingNext = false,
                next = null,
                nextError = null,
            )
        }
        if (pending && recheckPending) check()
    }

    /** Saves the fix's settings change and checks again. */
    public fun apply(fix: PreflightFix.Setting) {
        val store = settings ?: return
        viewModelScope.launch {
            store.update(workspaceId, fix.apply)
                .onSuccess {
                    inputsChanged()
                    check()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The setting could not be saved") } }
        }
    }
}
