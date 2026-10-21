/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import org.ide.lti.core.domain.usecase.publish.PublishJournal
import org.ide.lti.core.domain.usecase.publish.PublishPreflight
import org.ide.lti.core.domain.usecase.publish.PublishReleaseUseCase
import org.ide.lti.core.domain.usecase.publish.PublishTransfer
import org.ide.lti.core.domain.workspace.StageSettingsSync
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.PublishSettings

/** What Publish would do now (or why not), and the last attempt's journal. */
public data class PublishUiState(
    val preflight: PublishPreflight? = null,
    val journal: PublishJournal? = null,
    val publishing: Boolean = false,
    /** The running step and its bytes while publishing. */
    val transfer: PublishTransfer? = null,
    /** Speed of the running transfer, once it has run long enough to measure. */
    val bytesPerSecond: Double? = null,
    val cancelling: Boolean = false,
)

public class PublishViewModel(
    public val workspaceId: String,
    initialSettings: PublishSettings? = null,
    store: WorkspaceSettingsStore? = null,
    publisher: PublishReleaseUseCase? = null,
    sessions: PublishSessions? = null,
) : ViewModel() {

    public val targetDevice: TargetDevice = DefaultTargetCatalog.PQ84P01_DEFAULT

    private val sync = StageSettingsSync(
        scope = viewModelScope,
        workspaceId = workspaceId,
        store = store,
        initial = initialSettings ?: PublishSettings(),
        read = { it.publish },
        write = { snapshot, value -> snapshot.copy(publish = value) },
    )

    public val settings: StateFlow<PublishSettings> = sync.settings

    /** Why the last automatic save failed (for example while a build runs), or null. */
    public val saveError: StateFlow<String?> = sync.saveError

    /** The workspace's own device once loaded; null until then. */
    public val device: StateFlow<TargetDevice?> = sync.device

    public fun updateSettings(updated: PublishSettings) {
        sync.update(updated)
    }

    private val sessions: PublishSessions = sessions ?: PublishSessions(publisher)

    /** The workspace's publish, which outlives this editor (see [PublishSessions]). */
    public val state: StateFlow<PublishUiState> = this.sessions.state(workspaceId)

    public fun refresh() {
        sessions.refresh(workspaceId, settings.value)
    }

    /** Publishes (or resumes the last attempt). Only called after the person confirms it. */
    public fun publish() {
        sessions.publish(workspaceId, settings.value)
    }

    /** Stops the running publish; the journal keeps what is done, so publishing again resumes. */
    public fun cancel() {
        sessions.cancel(workspaceId)
    }
}
