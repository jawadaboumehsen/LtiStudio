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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.component.inputs.TextFieldState
import org.ide.lti.core.designsystem.component.progress.GlassLoadingPlaceholder
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.model.setup.ToolRef

@Composable
internal fun GitTagSelector(
    currentGroup: GroupState,
    state: ToolsPageState,
    selectedRef: ToolRef?,
    selectedVersionId: String?,
    refsError: String?,
    isCustomUrlMode: Boolean,
    customUrlInput: String,
    isInstalling: Boolean,
    onResolveRef: (ToolRef) -> Unit,
) {
    val availableRefs by state.availableRefs.collectAsState()
    val isLoadingRefs by state.isLoadingRefs.collectAsState()
    var tagQuery by remember(currentGroup.group) { mutableStateOf("") }

    LaunchedEffect(currentGroup.group) {
        state.loadRefs(if (isCustomUrlMode) customUrlInput else null)
    }

    GlassTextField(
        value = tagQuery,
        onValueChange = { tagQuery = it },
        placeholder = "Filter tags…",
        enabled = !isInstalling,
        modifier = Modifier.fillMaxWidth(),
    )

    if (isLoadingRefs) {
        GlassLoadingPlaceholder("Loading tags…")
    } else if (refsError != null) {
        Text(
            text = "Couldn't load tags: $refsError",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        InspectorSecondaryButton(
            text = "Retry",
            onClick = { state.loadRefs(if (isCustomUrlMode) customUrlInput else null) },
            enabled = !isInstalling,
        )
    } else {
        val allTags = availableRefs?.tags.orEmpty()
        val filteredTags = if (tagQuery.isBlank()) {
            allTags
        } else {
            allTags.filter { it.name.contains(tagQuery, ignoreCase = true) }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = ComponentSize.ThemeCardMinWidth),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            items(
                items = filteredTags,
                key = { it.name },
                contentType = { "TagRef" },
            ) { tag ->
                val isTagInstalled = currentGroup.installed.any { it.id == tag.name && it.intact }
                val isTagSelected = (selectedRef as? ToolRef.Tag)?.name == tag.name ||
                    selectedVersionId == tag.name
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectableBorder(isTagSelected)
                        .clip(GlassShapes.Card)
                        .clickable(enabled = !isInstalling) {
                            onResolveRef(ToolRef.Tag(tag.name))
                        },
                    contentPadding = Spacing.Small,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = codeFontFamily(),
                            )
                            Text(
                                text = tag.commit.take(7),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        InspectorSecondaryButton(
                            text = when {
                                isTagInstalled -> "Use"
                                isTagSelected -> "Selected"
                                else -> "Select"
                            },
                            onClick = {
                                if (isTagInstalled) {
                                    state.use(tag.name)
                                } else {
                                    onResolveRef(ToolRef.Tag(tag.name))
                                }
                            },
                            enabled = !isInstalling,
                            contentPadding = PaddingValues(
                                horizontal = Spacing.Small,
                                vertical = Spacing.ExtraSmall,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun GitBranchSelector(state: ToolsPageState, isInstalling: Boolean, onResolveRef: (ToolRef) -> Unit) {
    var branchInput by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassTextField(
            value = branchInput,
            onValueChange = {
                branchInput = it
                state.refInputChanged()
            },
            placeholder = "Branch name",
            enabled = !isInstalling,
            modifier = Modifier.weight(1f),
        )
        InspectorSecondaryButton(
            text = "Resolve",
            onClick = { onResolveRef(ToolRef.Branch(branchInput)) },
            enabled = branchInput.isNotBlank() && !isInstalling,
        )
    }
}

@Composable
internal fun GitCommitSelector(state: ToolsPageState, isInstalling: Boolean, onResolveRef: (ToolRef) -> Unit) {
    var commitInput by remember { mutableStateOf("") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassTextField(
            value = commitInput,
            onValueChange = {
                commitInput = it
                state.refInputChanged()
            },
            placeholder = "Commit SHA (40 hex)",
            enabled = !isInstalling,
            state = if (commitInput.isNotBlank() && commitSha(commitInput) == null) {
                TextFieldState.Error("A commit is 40 hex characters.")
            } else {
                TextFieldState.Default
            },
            modifier = Modifier.weight(1f),
        )
        InspectorSecondaryButton(
            text = "Resolve",
            onClick = {
                commitSha(commitInput)?.let { sha ->
                    onResolveRef(ToolRef.Commit(sha))
                }
            },
            enabled = commitSha(commitInput) != null && !isInstalling,
        )
    }
}

@Composable
internal fun GitResolutionFeedback(state: ToolsPageState, selectedRef: ToolRef?) {
    val resolvedCheck by state.resolvedCheck.collectAsState()
    val isResolving by state.isResolving.collectAsState()
    val resolveError by state.resolveError.collectAsState()

    if (isResolving) {
        Text("Resolving…", style = MaterialTheme.typography.labelSmall)
    }
    if (resolveError != null) {
        Text(
            text = resolveError!!,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
    if (resolvedCheck != null) {
        val refText = when (val r = selectedRef) {
            is ToolRef.Tag -> r.name
            is ToolRef.Branch -> r.name
            is ToolRef.Commit -> r.sha.take(7)
            is ToolRef.ReleaseVersion -> r.version
            null -> ""
        }
        Text(
            text = "$refText → ${resolvedCheck!!.commit.take(7)}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.primary,
        )
        if (resolvedCheck!!.layoutMissing.isNotEmpty()) {
            Text(
                text = "Not compatible: missing ${resolvedCheck!!.layoutMissing.joinToString()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

internal fun commitSha(input: String): String? = input.trim().lowercase().takeIf { COMMIT_SHA.matches(it) }

private val COMMIT_SHA = Regex("[0-9a-f]{40}")

internal fun repoFor(isCustom: Boolean, customInput: String, recommended: String?): String =
    if (isCustom) customInput else recommended.orEmpty()
