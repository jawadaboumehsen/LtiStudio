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

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.feature.setup.versions.ToolGroupRowModel
import org.ide.lti.feature.setup.versions.ToolGroupStatus
import org.ide.lti.feature.setup.versions.ToolVersionsState

@Composable
public fun ToolVersionsSection(state: ToolVersionsState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val rows by state.rows.collectAsState()
    val draft by state.draft.collectAsState()
    val selectedGroupId by state.selectedGroupId.collectAsState()
    val isReviewOpen by state.isReviewOpen.collectAsState()
    val reviewModel by state.reviewModel.collectAsState()
    val isRestoreDialogOpen by state.isRestoreDialogOpen.collectAsState()
    val restoreModel by state.restoreModel.collectAsState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    LaunchedEffect(draft) {
        if (draft != null) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(isReviewOpen) {
        if (isReviewOpen) {
            focusRequester.requestFocus()
        }
    }
    LaunchedEffect(isRestoreDialogOpen) {
        if (isRestoreDialogOpen) {
            focusRequester.requestFocus()
        }
    }

    Box(
        modifier =
        modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
                handleSectionKeyEvent(keyEvent, isReviewOpen || isRestoreDialogOpen, draft != null, state)
            },
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                Text(
                    text = "Tool Build Groups & Versions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Configure source versions and track release updates for native build groups",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(Spacing.Small))

                ToolGroupRowsList(rows = rows, selectedGroupId = selectedGroupId, state = state)
            }
        }

        if (isReviewOpen && reviewModel != null) {
            ReviewSwitchDialog(
                model = reviewModel ?: return@Box,
                onConfirmBuildAndSwitch = {
                    coroutineScope.launch {
                        state.confirmBuildAndSwitch()
                    }
                },
                onDismiss = { state.closeReview() },
            )
        }

        if (isRestoreDialogOpen && restoreModel != null) {
            RestorePreviousDialog(
                model = restoreModel ?: return@Box,
                onConfirmRestore = {
                    coroutineScope.launch {
                        state.confirmRestore()
                    }
                },
                onDismiss = { state.closeRestoreDialog() },
            )
        }
    }
}

@Composable
private fun ToolGroupRowsList(rows: List<ToolGroupRowModel>, selectedGroupId: ToolGroupId?, state: ToolVersionsState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        rows.forEach { rowModel ->
            ToolGroupRow(
                model = rowModel,
                isSelected = selectedGroupId?.value == rowModel.groupId,
                onActionClick = {
                    if (rowModel.status == ToolGroupStatus.CHANGE_PENDING) {
                        state.openReview()
                    } else {
                        state.startEdit(ToolGroupId(rowModel.groupId))
                    }
                },
            )
        }
    }
}

private fun handleSectionKeyEvent(
    keyEvent: KeyEvent,
    isReviewOpen: Boolean,
    hasDraft: Boolean,
    state: ToolVersionsState,
): Boolean {
    if (keyEvent.type != KeyEventType.KeyDown || keyEvent.key != Key.Escape) return false
    return when {
        isReviewOpen -> {
            state.closeReview()
            true
        }
        hasDraft -> {
            state.cancelEdit()
            true
        }
        else -> false
    }
}
