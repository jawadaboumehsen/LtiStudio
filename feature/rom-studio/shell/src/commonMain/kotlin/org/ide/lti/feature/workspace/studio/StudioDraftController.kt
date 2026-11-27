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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings

/** Owns draft revisions and asynchronous save reconciliation for one workspace. */
class StudioDraftController(initialDraft: WorkspacePipelineDraft, private val target: TargetDevice? = null) {
    private val _draft = MutableStateFlow(initialDraft)
    val draft: StateFlow<WorkspacePipelineDraft> = _draft.asStateFlow()

    fun applyAcquisition(value: AcquisitionSettings) = edit { it.withAcquisition(value) }
    fun applyExtraction(value: ExtractionSettings) = edit { it.withExtraction(value) }
    fun applyAssembly(value: AssemblySettings) = edit { it.withAssembly(value) }
    fun applyDebloat(value: DebloatSettings) = edit { it.withDebloat(value) }
    fun applyCustomization(value: CustomizationSettings) = edit { it.withCustomization(value) }
    fun applyBuild(value: BuildSettings) = edit { it.withBuild(value) }
    fun applyRelease(value: ReleaseSettings) = edit { it.withRelease(value) }
    fun applyPublish(value: PublishSettings) = edit { it.withPublish(value) }

    /** Reset is an edit, so observers can order it after an in-flight save. */
    fun reset() = edit { it.resetToBase() }

    /** Returns an independent controller; immutable settings values require no deep copy. */
    fun duplicate(): StudioDraftController = StudioDraftController(_draft.value.copy(), target)

    /** Clears all supported persisted settings to their type defaults and starts a fresh draft. */
    fun delete() = edit { it.copyFromDefaults() }

    fun reconcileAfterSave(snapshot: ConfigurationSnapshot, savedAsOfRevision: Long) {
        _draft.value = _draft.value.reconcileAfterSave(snapshot, savedAsOfRevision).revalidateFor(target)
    }

    private fun edit(transform: (WorkspacePipelineDraft) -> WorkspacePipelineDraft) {
        val current = _draft.value
        _draft.value = transform(current.copy(draftRevision = current.draftRevision + 1L))
            .revalidateFor(target)
    }
}
