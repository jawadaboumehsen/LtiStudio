/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.domain.setup.ToolGroupCatalog

/**
 * Inspector panel for examining, configuring, switching, or repairing tools and toolchains.
 */
@Composable
fun ToolInspector(state: ToolsPageState, modifier: Modifier = Modifier) {
    val groupState by state.selectedGroupState.collectAsState()
    val primaryAction by state.primaryButtonAction.collectAsState()
    val primaryDisabledReason by state.primaryButtonDisabledReason.collectAsState()
    val selectedVersionId by state.selectedVersionId.collectAsState()
    val refsError by state.refsError.collectAsState()
    val showTrustDialog by state.showTrustDialog.collectAsState()
    val untrustedUrl by state.untrustedRepoUrl.collectAsState()
    val selectedRef by state.selectedRef.collectAsState()

    val currentGroup = groupState ?: return
    val groupDef = ToolGroupCatalog.group(currentGroup.group)
    val isInstalling = state.isGroupInstalling(currentGroup.group)

    if (showTrustDialog && untrustedUrl != null) {
        TrustRepositoryDialog(
            repoUrl = untrustedUrl!!,
            onConfirm = { state.confirmTrustAndProceed() },
            onDismiss = { state.dismissTrustDialog() },
        )
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(currentGroup.group) {
        runCatchingCancellable { focusRequester.requestFocus() }
    }

    GlassCard(
        modifier = modifier
            .fillMaxHeight()
            .width(ComponentSize.PluginInspectorWidth)
            .semantics { contentDescription = "Inspector for ${currentGroup.group.value}" }
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (isEscape) state.closeInspector()
                isEscape
            },
        contentPadding = Spacing.Medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            ToolInspectorHeader(
                title = groupDef?.id?.value ?: currentGroup.group.value,
                statusText = if (isInstalling) "Installing" else currentGroup.status.name,
                isInstalling = isInstalling,
                onClose = { state.closeInspector() },
            )

            HorizontalDivider()

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clipToBounds()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
            ) {
                ToolInUseSection(currentGroup = currentGroup)
                ToolOutputsSection(groupDef = groupDef, isIntact = currentGroup.current?.intact == true)

                if (currentGroup.selection != null) {
                    StudioVersionSection(groupDef = groupDef, state = state)
                }

                HorizontalDivider()

                InstalledVersionsSection(
                    currentGroup = currentGroup,
                    state = state,
                    selectedVersionId = selectedVersionId,
                    isInstalling = isInstalling,
                )

                HorizontalDivider()

                Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    Text(
                        text = "Change version",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.isReleaseGroup) {
                        CuratedReleasesSection(
                            currentGroup = currentGroup,
                            state = state,
                            selectedVersionId = selectedVersionId,
                            isInstalling = isInstalling,
                        )
                    } else {
                        GitRefsSection(
                            currentGroup = currentGroup,
                            state = state,
                            selectedRef = selectedRef,
                            selectedVersionId = selectedVersionId,
                            refsError = refsError,
                            isInstalling = isInstalling,
                        )
                    }
                }
            }

            ToolInspectorFooter(
                currentGroup = currentGroup,
                state = state,
                primaryAction = primaryAction,
                primaryDisabledReason = primaryDisabledReason,
                selectedVersionId = selectedVersionId,
                isInstalling = isInstalling,
            )
        }
    }
}
