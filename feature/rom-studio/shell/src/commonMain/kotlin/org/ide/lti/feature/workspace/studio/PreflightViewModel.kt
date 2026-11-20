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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.stages.PreflightFinding
import org.ide.lti.core.domain.pipeline.stages.PreflightFix
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workspace.FirmwareUpgrade
import org.ide.lti.core.domain.workspace.NextBuild
import org.ide.lti.core.domain.workspace.NextBuildFacts
import org.ide.lti.core.domain.workspace.SamePhoneSettings
import org.ide.lti.core.domain.workspace.UpgradeSection
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.Workspace

public data class PreflightUiState(
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
)

/** Will it build? Runs the preflight and applies a finding's settings fix, then checks again. */
public class PreflightViewModel(
    private val workspaceId: String,
    private val preflight: Preflight? = null,
    private val settings: WorkspaceSettingsStore? = null,
    private val samePhone: SamePhoneSettings? = null,
    private val upgrade: FirmwareUpgrade? = null,
    private val nextBuild: NextBuild? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(PreflightUiState())
    public val state: StateFlow<PreflightUiState> = _state.asStateFlow()

    init {
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
                    _state.update { it.copy(message = "Took the settings of ${from.name}.") }
                    check()
                }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The settings could not be copied") } }
        }
    }

    public fun check() {
        readNext()
        val checker = preflight ?: return
        if (_state.value.checking) return
        _state.update { it.copy(checking = true, error = null, findings = null, vintf = null) }
        viewModelScope.launch {
            try {
                val report = checker.report(workspaceId)
                _state.update { it.copy(findings = report.findings, vintf = report.vintf) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                _state.update { it.copy(error = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                _state.update { it.copy(error = e.message ?: e::class.simpleName) }
            } finally {
                _state.update { it.copy(checking = false) }
            }
        }
    }

    /** Reads what building now would do, beside the blockers (a longer read: work/ is hashed like a run start). */
    private fun readNext() {
        val reader = nextBuild ?: return
        if (_state.value.readingNext) return
        _state.update { it.copy(readingNext = true, nextError = null) }
        viewModelScope.launch {
            val result = runCatchingCancellable { reader.of(workspaceId) }
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

    /** Saves the fix's settings change and checks again. */
    public fun apply(fix: PreflightFix.Setting) {
        val store = settings ?: return
        viewModelScope.launch {
            store.update(workspaceId, fix.apply)
                .onSuccess { check() }
                .onFailure { e -> _state.update { it.copy(error = e.message ?: "The setting could not be saved") } }
        }
    }
}
