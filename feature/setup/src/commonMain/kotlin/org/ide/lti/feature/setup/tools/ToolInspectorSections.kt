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

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroup
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef

@Composable
internal fun ToolInspectorHeader(title: String, statusText: String, isInstalling: Boolean, onClose: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isInstalling) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        GlassIconButton(onClick = onClose) {
            Icon(
                painter = AppIcons.ClosePainterResource(),
                contentDescription = "Close",
                modifier = Modifier.size(IconSize.Small),
            )
        }
    }
}

@Composable
internal fun ToolInUseSection(currentGroup: GroupState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(
            text = "In use",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val cur = currentGroup.current
        val inUseText = when {
            cur == null -> "—"
            currentGroup.selection != null -> "${cur.id} · your pick"
            else -> cur.id
        }
        Text(
            text = inUseText,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            fontFamily = codeFontFamily(),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun ToolOutputsSection(groupDef: ToolGroup?, isIntact: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(
            text = "Tools",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val outputs = groupDef?.outputs.orEmpty()
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            for ((toolId) in outputs) {
                val symbol = if (isIntact) "✓" else "✕"
                Text(
                    text = "$toolId $symbol",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = codeFontFamily(),
                    color = if (isIntact) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
        }
    }
}

@Composable
internal fun StudioVersionSection(groupDef: ToolGroup?, state: ToolsPageState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(
            text = "Studio's version",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val studioPin = when (val src = groupDef?.source) {
            is ToolSource.Git -> when (val ref = src.pinnedRef) {
                is ToolRef.Tag -> ref.name
                is ToolRef.Branch -> "${ref.name}-${src.pinnedCommit.take(7)}"
                is ToolRef.Commit -> ref.sha.take(7)
                is ToolRef.ReleaseVersion -> ref.version
            }

            is ToolSource.Release -> {
                val rec = groupDef.recipe as? RecipeConfig.Release
                rec?.versions?.firstOrNull()?.version ?: "pinned"
            }

            null -> "pinned"
        }
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = Spacing.Small,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.ExtraSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = studioPin,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = codeFontFamily(),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                InspectorSecondaryButton(
                    text = "Use Studio's version",
                    onClick = { state.followStudio() },
                    enabled = state.canUseStudioVersion,
                )
            }
        }
        val reason = state.useStudioVersionDisabledReason()
        if (reason != null && !state.canUseStudioVersion) {
            Text(
                text = reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
internal fun InstalledVersionsSection(
    currentGroup: GroupState,
    state: ToolsPageState,
    selectedVersionId: String?,
    isInstalling: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
        Text(
            text = "Installed",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (currentGroup.installed.isEmpty()) {
            Text(
                text = "None",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = ComponentSize.MinPanelWidth),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                items(
                    items = currentGroup.installed,
                    key = { it.id },
                    contentType = { "InstalledVersionItem" },
                ) { v ->
                    val isCurrent = v.id == currentGroup.current?.id
                    val isVersionSelected = selectedVersionId == v.id
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectableBorder(isVersionSelected)
                            .clip(GlassShapes.Card)
                            .clickable(enabled = !isInstalling) {
                                state.selectInstalledVersion(v.id)
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
                            Text(
                                text = v.id,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = codeFontFamily(),
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (isCurrent) {
                                Text(
                                    text = "in use",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                                    InspectorSecondaryButton(
                                        text = "Use",
                                        onClick = { state.use(v.id) },
                                        enabled = state.canUseVersion(v.id),
                                    )
                                    InspectorSecondaryButton(
                                        text = "Remove",
                                        onClick = { state.remove(v.id) },
                                        enabled = state.canRemoveVersion(v.id),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun CuratedReleasesSection(
    currentGroup: GroupState,
    state: ToolsPageState,
    selectedVersionId: String?,
    isInstalling: Boolean,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = ComponentSize.MinPanelWidth),
        verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
    ) {
        items(
            items = state.curatedReleases,
            key = { it.version },
            contentType = { "CuratedRelease" },
        ) { rel ->
            val isInstalled = currentGroup.installed.any { it.id == rel.version && it.intact }
            val isSelected = selectedVersionId == rel.version
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableBorder(isSelected)
                    .clip(GlassShapes.Card)
                    .clickable(enabled = !isInstalling) {
                        state.selectTargetVersion(rel.version)
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
                    Column {
                        Text(
                            text = rel.version,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                            fontFamily = codeFontFamily(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = if (isInstalled) "Checksum verified" else "Checksum verified on install",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    InspectorSecondaryButton(
                        text = if (isInstalled) "Use" else "Install & use",
                        onClick = {
                            if (isInstalled) {
                                state.use(rel.version)
                            } else {
                                state.installAndUse(
                                    ResolvedSource.Release(
                                        version = rel.version,
                                        url = rel.url,
                                        sha256 = rel.sha256,
                                        member = rel.archiveMember,
                                    ),
                                )
                            }
                        },
                        enabled = !isInstalling,
                    )
                }
            }
        }
    }
}

@Composable
internal fun GitRefsSection(
    currentGroup: GroupState,
    state: ToolsPageState,
    selectedRef: ToolRef?,
    selectedVersionId: String?,
    refsError: String?,
    isInstalling: Boolean,
) {
    val coroutineScope = rememberCoroutineScope()
    val selectedRefType by state.selectedRefType.collectAsState()
    var customUrlInput by remember(currentGroup.group) { mutableStateOf("") }
    var isCustomUrlMode by remember(currentGroup.group) { mutableStateOf(false) }

    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        GlassChip(
            label = "Tag",
            selected = selectedRefType == RefType.Tag,
            enabled = !isInstalling,
            onClick = {
                state.selectRefType(RefType.Tag)
                state.loadRefs(if (isCustomUrlMode) customUrlInput else null)
            },
        )
        GlassChip(
            label = "Branch",
            selected = selectedRefType == RefType.Branch,
            enabled = !isInstalling,
            onClick = { state.selectRefType(RefType.Branch) },
        )
        GlassChip(
            label = "Commit",
            selected = selectedRefType == RefType.Commit,
            enabled = !isInstalling,
            onClick = { state.selectRefType(RefType.Commit) },
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        GlassChip(
            label = "Studio's",
            selected = !isCustomUrlMode,
            enabled = !isInstalling,
            onClick = {
                isCustomUrlMode = false
                state.setCustomRepoUrl(null)
            },
        )
        GlassChip(
            label = "Custom URL",
            selected = isCustomUrlMode,
            enabled = !isInstalling,
            onClick = { isCustomUrlMode = true },
        )
    }

    if (isCustomUrlMode) {
        GlassTextField(
            value = customUrlInput,
            onValueChange = {
                customUrlInput = it
                state.setCustomRepoUrl(it.takeIf { s -> s.isNotBlank() })
            },
            placeholder = "https://github.com/owner/repo.git",
            enabled = !isInstalling,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val onResolveRef: (ToolRef) -> Unit = { ref ->
        coroutineScope.launch {
            val repo = repoFor(isCustomUrlMode, customUrlInput, state.recommendedUrl)
            state.requestWithTrustCheck(repo) {
                state.resolveRef(ref, repo)
            }
        }
    }

    when (selectedRefType) {
        RefType.Tag -> GitTagSelector(
            currentGroup = currentGroup,
            state = state,
            selectedRef = selectedRef,
            selectedVersionId = selectedVersionId,
            refsError = refsError,
            isCustomUrlMode = isCustomUrlMode,
            customUrlInput = customUrlInput,
            isInstalling = isInstalling,
            onResolveRef = onResolveRef,
        )

        RefType.Branch -> GitBranchSelector(
            state = state,
            isInstalling = isInstalling,
            onResolveRef = onResolveRef,
        )

        RefType.Commit -> GitCommitSelector(
            state = state,
            isInstalling = isInstalling,
            onResolveRef = onResolveRef,
        )
    }

    GitResolutionFeedback(state = state, selectedRef = selectedRef)
}

@Composable
internal fun ToolInspectorFooter(
    currentGroup: GroupState,
    state: ToolsPageState,
    primaryAction: PrimaryButtonAction,
    primaryDisabledReason: String?,
    selectedVersionId: String?,
    isInstalling: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        HorizontalDivider()

        val runningOp = currentGroup.operation?.takeIf { it.result == null }
        if (isInstalling && runningOp != null) {
            OperationProgressCard(
                progress = ToolsPresentationMapper.mapProgress(runningOp),
                onCancel = { state.cancelActiveOperation() },
            )
        }

        if (!isInstalling) {
            InspectorPrimaryActionButton(
                primaryAction = primaryAction,
                state = state,
                selectedVersionId = selectedVersionId,
                isInstalling = isInstalling,
            )
        }

        if (primaryAction == PrimaryButtonAction.InstallAndUse && !isInstalling &&
            !state.canInstallSelected()
        ) {
            Text(
                text = "Pick a version and resolve it first",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val actionError by state.actionError.collectAsState()
        actionError?.takeIf { it.group == currentGroup.group }?.let { failure ->
            Text(
                text = failure.message,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (primaryDisabledReason != null && !isInstalling) {
            Text(
                text = primaryDisabledReason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun InspectorPrimaryActionButton(
    primaryAction: PrimaryButtonAction,
    state: ToolsPageState,
    selectedVersionId: String?,
    isInstalling: Boolean,
) {
    when (primaryAction) {
        PrimaryButtonAction.Use -> {
            GlassPrimaryButton(
                onClick = {
                    val vId = state.selectedVersionId.value
                    if (vId != null) state.use(vId)
                },
                enabled = !isInstalling && state.canUseVersion(state.selectedVersionId.value ?: ""),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use", style = MaterialTheme.typography.labelMedium)
            }
        }

        PrimaryButtonAction.InstallAndUse -> {
            GlassPrimaryButton(
                onClick = { state.installResolved() },
                enabled = !isInstalling && selectedVersionId.let { state.canInstallSelected() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Install & use", style = MaterialTheme.typography.labelMedium)
            }
        }

        PrimaryButtonAction.Repair -> {
            GlassPrimaryButton(
                onClick = { state.repair() },
                enabled = state.canRepair(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Repair", style = MaterialTheme.typography.labelMedium)
            }
        }

        PrimaryButtonAction.Disabled -> {
            GlassPrimaryButton(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Install & use", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun Modifier.selectableBorder(isSelected: Boolean): Modifier = if (isSelected) {
    border(StrokeWidth.Focused, MaterialTheme.colorScheme.primary, GlassShapes.Card)
} else {
    this
}

@Composable
internal fun InspectorSecondaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(
        horizontal = Spacing.Medium,
        vertical = Spacing.ExtraSmall,
    ),
) {
    GlassSecondaryButton(
        onClick = onClick,
        enabled = enabled,
        minWidth = Spacing.None,
        contentPadding = contentPadding,
        modifier = modifier,
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall)
    }
}
