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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.CompatibilityResult
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.setup.ports.RefListing
import org.ide.lti.core.model.setup.ToolRef
import org.ide.lti.feature.setup.versions.ResolutionState
import org.ide.lti.feature.setup.versions.ToolGroupDraft

@Composable
public fun EditVersionPanel(
    draft: ToolGroupDraft,
    installedVersion: String,
    availableRefs: RefListing,
    isLoadingRefs: Boolean,
    resolutionState: ResolutionState,
    onRefSelected: (ToolRef) -> Unit,
    onRepoUrlChanged: (String?) -> Unit,
    onQueryChanged: (String) -> Unit,
    onAdvancedToggled: (Boolean) -> Unit,
    onSave: () -> Unit,
    onReviewAndBuild: () -> Unit,
    onCancel: () -> Unit,
    onResetToRecommended: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var showForkTrustDialog by remember { mutableStateOf(false) }

    GlassCard(
        modifier =
        modifier
            .fillMaxHeight()
            .width(ComponentSize.PluginInspectorWidth)
            .semantics { contentDescription = "Edit version panel for ${draft.group.value}" },
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Large),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Edit ${draft.group.value}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                GlassIconButton(
                    icon = AppIcons.Close,
                    onClick = onCancel,
                    contentDescription = "Close edit panel",
                    size = ComponentSize.PanelHeaderAction,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Medium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(Spacing.Medium))

            Text(
                text = "Currently Installed",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = installedVersion,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = codeFontFamily(),
                color = MaterialTheme.colorScheme.onSurface,
            )

            val isReleaseGroup = ToolGroupCatalog.group(draft.group)?.source is ToolSource.Release
            if (!isReleaseGroup) {
                Spacer(modifier = Modifier.height(Spacing.Large))
                SourceSection(
                    repoUrl = draft.repoUrl,
                    onRepoUrlChanged = onRepoUrlChanged,
                )
            }

            Spacer(modifier = Modifier.height(Spacing.Large))

            VersionSection(
                draft = draft,
                availableRefs = availableRefs,
                isLoadingRefs = isLoadingRefs,
                isReleaseGroup = isReleaseGroup,
                onRefSelected = onRefSelected,
                onQueryChanged = onQueryChanged,
                onAdvancedToggled = onAdvancedToggled,
            )

            Spacer(modifier = Modifier.height(Spacing.Large))

            ResolvedStatusSection(resolutionState = resolutionState)

            Spacer(modifier = Modifier.height(Spacing.Large))

            GlassSecondaryButton(
                onClick = onResetToRecommended,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Reset to recommended")
            }

            Spacer(modifier = Modifier.height(Spacing.Medium))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small, Alignment.End),
            ) {
                GlassSecondaryButton(onClick = onCancel) {
                    Text("Cancel")
                }
                GlassSecondaryButton(
                    onClick = {
                        if (draft.repoUrl != null) showForkTrustDialog = true else onSave()
                    },
                ) {
                    Text("Save")
                }
                val canBuild =
                    resolutionState is ResolutionState.Resolved &&
                        resolutionState.compatibility is CompatibilityResult.LayoutCompatible
                GlassPrimaryButton(
                    onClick = {
                        if (draft.repoUrl != null) showForkTrustDialog = true else onReviewAndBuild()
                    },
                    enabled = canBuild,
                ) {
                    Text("Review & build")
                }
            }
        }
    }

    if (showForkTrustDialog) {
        TrustRepositoryDialog(
            repoUrl = draft.repoUrl ?: "",
            onConfirm = {
                showForkTrustDialog = false
                onSave()
            },
            onDismiss = { showForkTrustDialog = false },
        )
    }
}

@Composable
private fun SourceSection(repoUrl: String?, onRepoUrlChanged: (String?) -> Unit) {
    Text(
        text = "Source Repository",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassChip(
            label = "Recommended",
            selected = repoUrl == null,
            onClick = { onRepoUrlChanged(null) },
        )
        GlassChip(
            label = "Fork URL",
            selected = repoUrl != null,
            onClick = { if (repoUrl == null) onRepoUrlChanged("https://") },
        )
    }
    if (repoUrl != null) {
        Spacer(modifier = Modifier.height(Spacing.Small))
        GlassTextField(
            value = repoUrl,
            onValueChange = { onRepoUrlChanged(it.ifBlank { null }) },
            label = "Git URL (HTTPS or SSH)",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun VersionSection(
    draft: ToolGroupDraft,
    availableRefs: RefListing,
    isLoadingRefs: Boolean,
    isReleaseGroup: Boolean,
    onRefSelected: (ToolRef) -> Unit,
    onQueryChanged: (String) -> Unit,
    onAdvancedToggled: (Boolean) -> Unit,
) {
    Text(
        text = if (isReleaseGroup) "Curated Release Versions" else "Version Selection",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

    GlassTextField(
        value = draft.refQuery,
        onValueChange = onQueryChanged,
        label = if (isReleaseGroup) "Search release versions" else "Search tags or branches",
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(modifier = Modifier.height(Spacing.Small))

    if (isLoadingRefs) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(IconSize.Medium))
            Spacer(modifier = Modifier.width(Spacing.Small))
            Text(
                text = if (isReleaseGroup) "Fetching versions..." else "Fetching tags...",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    } else {
        VersionTagList(
            draft = draft,
            tags = availableRefs.tags,
            isReleaseGroup = isReleaseGroup,
            onRefSelected = onRefSelected,
        )
    }

    if (!isReleaseGroup) {
        AdvancedCommitSection(
            draft = draft,
            onRefSelected = onRefSelected,
            onAdvancedToggled = onAdvancedToggled,
        )
    }
}

@Composable
private fun VersionTagList(
    draft: ToolGroupDraft,
    tags: List<String>,
    isReleaseGroup: Boolean,
    onRefSelected: (ToolRef) -> Unit,
) {
    val filteredTags = tags.filter { it.contains(draft.refQuery, ignoreCase = true) }
    Box(modifier = Modifier.heightIn(max = ComponentSize.PluginDiagnosticsTableColFile)) {
        LazyColumn {
            items(filteredTags.take(15)) { tag ->
                val isSelected =
                    when (val ref = draft.ref) {
                        is ToolRef.ReleaseVersion -> ref.version == tag
                        is ToolRef.Tag -> ref.name == tag
                        else -> false
                    }
                Row(
                    modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            val nextRef = if (isReleaseGroup) ToolRef.ReleaseVersion(tag) else ToolRef.Tag(tag)
                            onRefSelected(nextRef)
                        }.padding(vertical = Spacing.ExtraSmall, horizontal = Spacing.Small),
                ) {
                    Text(
                        text = tag,
                        fontFamily = codeFontFamily(),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color =
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedCommitSection(
    draft: ToolGroupDraft,
    onRefSelected: (ToolRef) -> Unit,
    onAdvancedToggled: (Boolean) -> Unit,
) {
    Spacer(modifier = Modifier.height(Spacing.Small))
    Row(
        modifier = Modifier.clickable { onAdvancedToggled(!draft.isAdvanced) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (draft.isAdvanced) "▼ Advanced: branch / commit" else "▶ Advanced: branch / commit",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    if (draft.isAdvanced) {
        Spacer(modifier = Modifier.height(Spacing.Small))
        var commitInput by remember {
            mutableStateOf(if (draft.ref is ToolRef.Commit) (draft.ref as ToolRef.Commit).sha else "")
        }
        GlassTextField(
            value = commitInput,
            onValueChange = {
                commitInput = it
                if (it.length == 40 && it.matches(Regex("^[0-9a-fA-F]{40}$"))) {
                    onRefSelected(ToolRef.Commit(it.lowercase()))
                }
            },
            label = "Exact 40-character commit SHA",
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ResolvedStatusSection(resolutionState: ResolutionState) {
    Text(
        text = "Resolved Status",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.height(Spacing.ExtraSmall))

    when (resolutionState) {
        is ResolutionState.Idle -> {
            Text("Select a version to resolve", style = MaterialTheme.typography.bodySmall)
        }
        is ResolutionState.Resolving -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(IconSize.Medium))
                Spacer(modifier = Modifier.width(Spacing.Small))
                Text("Resolving commit and checking layout...", style = MaterialTheme.typography.bodySmall)
            }
        }
        is ResolutionState.Resolved -> {
            val isRelease = resolutionState.input is ResolvedInput.Release
            val commitText =
                when (val input = resolutionState.input) {
                    is ResolvedInput.Git -> "Resolved commit: ${input.commit.take(7)}"
                    is ResolvedInput.Release -> "Resolved release: ${input.version}"
                }
            Text(
                text = commitText,
                fontFamily = codeFontFamily(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            val wording =
                when (resolutionState.compatibility) {
                    is CompatibilityResult.LayoutCompatible ->
                        if (isRelease) {
                            "Checksum-verified release binary"
                        } else {
                            "Compatible with toolchain build system"
                        }
                    is CompatibilityResult.Unsupported -> "Unsupported layout: missing required files"
                    is CompatibilityResult.NotFound -> "Reference not found on remote"
                    is CompatibilityResult.Unreachable -> "Remote repository unreachable"
                    is CompatibilityResult.AccessDenied -> "Access denied (authentication required)"
                }
            Text(
                text = wording,
                style = MaterialTheme.typography.bodySmall,
                color =
                if (resolutionState.compatibility is CompatibilityResult.LayoutCompatible) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
        }
        is ResolutionState.Error -> {
            Text(
                text = resolutionState.reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
