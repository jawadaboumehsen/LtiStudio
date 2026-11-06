/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.display.ideCardSurface
import org.ide.lti.core.designsystem.component.feedback.GlassBanner
import org.ide.lti.core.designsystem.component.feedback.GlassBannerSeverity
import org.ide.lti.core.designsystem.theme.FontSize
import org.ide.lti.core.designsystem.theme.GlassDimens
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.designsystem.theme.ideFontFamily
import org.ide.lti.core.domain.pipeline.toolkit.RefusalException
import org.ide.lti.core.domain.workbench.FolderUsage
import org.ide.lti.core.domain.workbench.WorkspaceStorage
import org.ide.lti.feature.rom.studio.api.formatBytes

/** What the Disk usage panel shows; [busy] names the folder being measured or deleted. */
public data class StorageState(
    val folders: List<FolderUsage> = emptyList(),
    val loaded: Boolean = false,
    val busy: String? = null,
    val message: String? = null,
)

public class WorkspaceStorageViewModel(
    private val workspaceId: String,
    private val storage: WorkspaceStorage? = null,
) : ViewModel() {
    private val mutableState = MutableStateFlow(StorageState())
    public val state: StateFlow<StorageState> = mutableState.asStateFlow()

    public fun refresh(): Unit = task("Measuring") {
        val folders = it.usage(workspaceId)
        mutableState.update { s -> s.copy(folders = folders, loaded = true) }
    }

    /** Deletes [path] (a folder the next build makes again) and measures again. */
    public fun clean(path: String): Unit = task(path) {
        val freed = it.clean(workspaceId, path)
        val folders = it.usage(workspaceId)
        mutableState.update { s -> s.copy(folders = folders, message = "Freed ${formatBytes(freed)} ($path).") }
    }

    public fun dismissMessage() {
        mutableState.update { it.copy(message = null) }
    }

    private fun task(name: String, block: suspend (WorkspaceStorage) -> Unit) {
        val storage = storage ?: return
        if (state.value.busy != null) return
        mutableState.update { it.copy(busy = name, message = null) }
        viewModelScope.launch {
            try {
                block(storage)
            } catch (e: CancellationException) {
                throw e
            } catch (e: RefusalException) {
                mutableState.update { it.copy(message = "${e.refusal.cause} ${e.refusal.nextStep}") }
            } catch (e: Exception) {
                mutableState.update { it.copy(message = e.message ?: "Could not read the workspace folders.") }
            } finally {
                mutableState.update { it.copy(busy = null) }
            }
        }
    }
}

/** What each workspace folder takes in WSL, and freeing the ones the next build makes again. */
@Composable
public fun WorkspaceStoragePanel(viewModel: WorkspaceStorageViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(viewModel) { if (!state.loaded) viewModel.refresh() }
    Box(modifier = modifier.fillMaxSize().testTag("WorkspaceStoragePanel"), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = GlassDimens.OverviewMaxContentWidth)
                .padding(horizontal = Spacing.ExtraLarge, vertical = Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            item { Summary(state, onRefresh = viewModel::refresh) }
            state.message?.let { message ->
                item {
                    GlassBanner(
                        severity = GlassBannerSeverity.INFO,
                        title = message,
                        onDismiss = viewModel::dismissMessage,
                    )
                }
            }
            items(state.folders, key = { it.path }) { folder ->
                FolderRow(folder, busy = state.busy, onClean = { viewModel.clean(folder.path) })
            }
        }
    }
}

@Composable
private fun Summary(state: StorageState, onRefresh: () -> Unit) {
    val total = state.folders.sumOf { it.bytes }
    val freeable = state.folders.filter { it.cleanable }.sumOf { it.bytes }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(GlassShapes.ShellCard)
            .ideCardSurface(shape = GlassShapes.ShellCard)
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (state.loaded) "${formatBytes(total)} in this workspace" else "Disk usage",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontWeight = FontWeight.SemiBold,
                fontFamily = ideFontFamily(),
                modifier = Modifier.weight(1f),
            )
            GlassButton(onClick = onRefresh, enabled = state.busy == null, variant = GlassButtonVariant.Standard) {
                Text(if (state.busy == "Measuring") "Measuring…" else "Measure again", fontSize = FontSize.BodySmall)
            }
        }
        if (state.loaded) {
            Text(
                text = "${formatBytes(freeable)} can be freed: the next build makes those folders again " +
                    "(it then runs the stages that made them).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
    }
}

@Composable
private fun FolderRow(folder: FolderUsage, busy: String?, onClean: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().testTag("StorageRow_${folder.path}"),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = folder.path,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = FontSize.BodySmall,
                fontFamily = codeFontFamily(),
            )
            Text(
                text = folder.note,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = FontSize.Micro,
                fontFamily = ideFontFamily(),
            )
        }
        Text(
            text = formatBytes(folder.bytes),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = FontSize.BodySmall,
            fontFamily = codeFontFamily(),
        )
        if (folder.cleanable) {
            GlassButton(onClick = onClean, enabled = busy == null, variant = GlassButtonVariant.Standard) {
                Text(if (busy == folder.path) "Freeing…" else "Free", fontSize = FontSize.Micro)
            }
        }
    }
}
