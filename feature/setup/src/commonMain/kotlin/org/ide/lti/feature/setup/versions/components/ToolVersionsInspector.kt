/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.versions.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import org.ide.lti.feature.setup.versions.ToolVersionsState

/**
 * The right-hand inspector for the group being edited. Host it beside the page, not inside it: it has a bounded
 * height of its own and scrolls independently of the page, like a macOS inspector. Renders nothing without a draft.
 */
@Composable
public fun ToolVersionsInspector(state: ToolVersionsState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val draft by state.draft.collectAsState()
    val rows by state.rows.collectAsState()
    val availableRefs by state.availableRefs.collectAsState()
    val isLoadingRefs by state.isLoadingRefs.collectAsState()
    val resolutionState by state.resolutionState.collectAsState()

    val currentDraft = draft ?: return
    val installedVersion = rows.firstOrNull { it.groupId == currentDraft.group.value }?.installedLabel ?: "unknown"

    EditVersionPanel(
        draft = currentDraft,
        installedVersion = installedVersion,
        availableRefs = availableRefs,
        isLoadingRefs = isLoadingRefs,
        resolutionState = resolutionState,
        onRefSelected = { state.updateDraftRef(it) },
        onRepoUrlChanged = { state.updateDraftRepoUrl(it) },
        onQueryChanged = { state.updateDraftQuery(it) },
        onAdvancedToggled = { state.updateDraftAdvanced(it) },
        onSave = { coroutineScope.launch { state.saveDraft() } },
        onReviewAndBuild = { state.openReview() },
        onCancel = { state.cancelEdit() },
        onResetToRecommended = { coroutineScope.launch { state.resetToRecommended(currentDraft.group) } },
        modifier =
        modifier.onKeyEvent { event ->
            val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
            if (isEscape) state.cancelEdit()
            isEscape
        },
    )
}
