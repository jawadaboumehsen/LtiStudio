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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import kotlinx.coroutines.launch
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassPrimaryButton
import org.ide.lti.core.designsystem.component.actions.GlassSecondaryButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassChip
import org.ide.lti.core.designsystem.component.inputs.GlassTextField
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.designsystem.theme.codeFontFamily
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.setup.ToolSource
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef

@Suppress("CyclomaticComplexMethod", "LongMethod")
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun ToolInspector(state: ToolsPageState, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val groupState by state.selectedGroupState.collectAsState()
    val primaryAction by state.primaryButtonAction.collectAsState()
    val primaryDisabledReason by state.primaryButtonDisabledReason.collectAsState()
    val resolvedSelection by state.resolvedSelection.collectAsState()
    val selectedVersion by state.selectedVersionId.collectAsState()
    val refsError by state.refsError.collectAsState()
    val showTrustDialog by state.showTrustDialog.collectAsState()
    val untrustedUrl by state.untrustedRepoUrl.collectAsState()

    val currentGroup = groupState ?: return
    val groupDef = ToolGroupCatalog.group(currentGroup.group)

    val isReducedMotion = GlassTheme.reducedMotionPolicy == GlassReducedMotionPolicy.Reduced
    val isInstalling = state.isGroupInstalling(currentGroup.group)

    if (showTrustDialog && untrustedUrl != null) {
        TrustRepositoryDialog(
            repoUrl = untrustedUrl!!,
            onConfirm = { state.confirmTrustAndProceed() },
            onDismiss = { state.dismissTrustDialog() },
        )
    }

    GlassCard(
        modifier =
        modifier
            .fillMaxHeight()
            .width(ComponentSize.PluginInspectorWidth)
            .semantics { contentDescription = "Inspector for ${currentGroup.group.value}" }
            .onKeyEvent { event ->
                val isEscape = event.type == KeyEventType.KeyDown && event.key == Key.Escape
                if (isEscape) state.closeInspector()
                isEscape
            },
        contentPadding = Spacing.Medium,
    ) {
        Column(
            modifier =
            Modifier
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(Spacing.Small),
            verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = groupDef?.id?.value ?: currentGroup.group.value,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (isInstalling) "Installing" else currentGroup.status.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isInstalling) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                GlassIconButton(
                    onClick = { state.closeInspector() },
                ) {
                    Icon(
                        painter = AppIcons.ClosePainterResource(),
                        contentDescription = "Close",
                        modifier = Modifier.size(IconSize.Small),
                    )
                }
            }

            HorizontalDivider()

            // In use section
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                Text(
                    text = "In use",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val cur = currentGroup.current
                val inUseText =
                    when {
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

            // Studio's version section (offered when group has a pick)
            if (currentGroup.selection != null) {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall)) {
                    Text(
                        text = "Studio's version",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val studioPin =
                        when (val src = groupDef?.source) {
                            is ToolSource.Git ->
                                when (val ref = src.pinnedRef) {
                                    is ToolRef.Tag -> ref.name
                                    is ToolRef.Branch -> "${ref.name}-${src.pinnedCommit.take(7)}"
                                    is ToolRef.Commit -> ref.sha.take(7)
                                    is ToolRef.ReleaseVersion -> ref.version
                                }
                            is ToolSource.Release -> {
                                val rec = groupDef.recipe as? org.ide.lti.core.domain.setup.RecipeConfig.Release
                                rec?.versions?.firstOrNull()?.version ?: "pinned"
                            }
                            null -> "pinned"
                        }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = studioPin,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = codeFontFamily(),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        GlassSecondaryButton(
                            onClick = { state.followStudio() },
                            enabled = state.canUseStudioVersion,
                        ) {
                            Text("Use Studio's version", style = MaterialTheme.typography.labelSmall)
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

            HorizontalDivider()

            // Installed versions list (bounded LazyColumn)
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
                        modifier =
                        Modifier
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
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
                                        GlassTextButton(
                                            onClick = { state.use(v.id) },
                                            enabled = state.canUseVersion(v.id),
                                        ) {
                                            Text("Use", style = MaterialTheme.typography.labelSmall)
                                        }
                                        GlassTextButton(
                                            onClick = { state.remove(v.id) },
                                            enabled = state.canRemoveVersion(v.id),
                                        ) {
                                            Text("Remove", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            // Change version section
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                Text(
                    text = "Change version",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (state.isReleaseGroup) {
                    // Curated release groups show only their curated versions
                    LazyColumn(
                        modifier =
                        Modifier
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
                            Row(
                                modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { state.selectTargetVersion(rel.version) }
                                    .padding(vertical = Spacing.ExtraSmall),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Column {
                                    Text(
                                        text = rel.version,
                                        style =
                                        MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.SemiBold,
                                        ),
                                        fontFamily = codeFontFamily(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        // Expected, not yet checked: the download is verified when it is installed.
                                        text = if (isInstalled) "Checksum verified" else "Checksum verified on install",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                GlassSecondaryButton(
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
                                ) {
                                    Text(
                                        if (isInstalled) "Use" else "Install & use",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Git groups show Tag, Branch, Commit selectors
                    val selectedRefType by state.selectedRefType.collectAsState()
                    val availableRefs by state.availableRefs.collectAsState()
                    val isLoadingRefs by state.isLoadingRefs.collectAsState()
                    var customUrlInput by remember(currentGroup.group) { mutableStateOf("") }
                    var tagQuery by remember(currentGroup.group) { mutableStateOf("") }
                    var branchInput by remember(currentGroup.group) { mutableStateOf("") }
                    var commitInput by remember(currentGroup.group) { mutableStateOf("") }
                    var isCustomUrlMode by remember(currentGroup.group) { mutableStateOf(false) }

                    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        GlassChip(
                            label = "Tag",
                            selected = selectedRefType == RefType.Tag,
                            onClick = {
                                state.selectRefType(RefType.Tag)
                                state.loadRefs(if (isCustomUrlMode) customUrlInput else null)
                            },
                        )
                        GlassChip(
                            label = "Branch",
                            selected = selectedRefType == RefType.Branch,
                            onClick = { state.selectRefType(RefType.Branch) },
                        )
                        GlassChip(
                            label = "Commit",
                            selected = selectedRefType == RefType.Commit,
                            onClick = { state.selectRefType(RefType.Commit) },
                        )
                    }

                    // Repository mode toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassChip(
                            label = "Studio's",
                            selected = !isCustomUrlMode,
                            onClick = {
                                isCustomUrlMode = false
                                state.setCustomRepoUrl(null)
                            },
                        )
                        GlassChip(
                            label = "Custom URL",
                            selected = isCustomUrlMode,
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    when (selectedRefType) {
                        RefType.Tag -> {
                            LaunchedEffect(currentGroup.group) {
                                state.loadRefs(if (isCustomUrlMode) customUrlInput else null)
                            }
                            GlassTextField(
                                value = tagQuery,
                                onValueChange = { tagQuery = it },
                                placeholder = "Filter tags…",
                                modifier = Modifier.fillMaxWidth(),
                            )

                            if (isLoadingRefs) {
                                Text("Loading tags…", style = MaterialTheme.typography.bodySmall)
                            } else if (refsError != null) {
                                Text(
                                    text = "Couldn't load tags: $refsError",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                                GlassTextButton(
                                    onClick = { state.loadRefs(if (isCustomUrlMode) customUrlInput else null) },
                                ) {
                                    Text("Retry", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                val allTags = availableRefs?.tags.orEmpty()
                                val filteredTags =
                                    if (tagQuery.isBlank()) {
                                        allTags
                                    } else {
                                        allTags.filter { it.name.contains(tagQuery, ignoreCase = true) }
                                    }

                                LazyColumn(
                                    modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = ComponentSize.ThemeCardMinWidth),
                                    verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
                                ) {
                                    items(
                                        items = filteredTags,
                                        key = { it.name },
                                        contentType = { "TagRef" },
                                    ) { tag ->
                                        Row(
                                            modifier =
                                            Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    coroutineScope.launch {
                                                        val repo =
                                                            repoFor(
                                                                isCustomUrlMode,
                                                                customUrlInput,
                                                                state.recommendedUrl,
                                                            )
                                                        val ref = ToolRef.Tag(tag.name)
                                                        state.requestWithTrustCheck(repo) {
                                                            state.resolveRef(ref, repo)
                                                        }
                                                    }
                                                }
                                                .padding(Spacing.ExtraSmall),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
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
                                    }
                                }
                            }
                        }

                        RefType.Branch -> {
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
                                    modifier = Modifier.weight(1f),
                                )
                                GlassSecondaryButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val repo = repoFor(isCustomUrlMode, customUrlInput, state.recommendedUrl)
                                            val ref = ToolRef.Branch(branchInput)
                                            state.requestWithTrustCheck(repo) {
                                                state.resolveRef(ref, repo)
                                            }
                                        }
                                    },
                                    enabled = branchInput.isNotBlank() && !isInstalling,
                                ) {
                                    Text("Resolve", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }

                        RefType.Commit -> {
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
                                    modifier = Modifier.weight(1f),
                                )
                                GlassSecondaryButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            val repo = repoFor(isCustomUrlMode, customUrlInput, state.recommendedUrl)
                                            val ref = ToolRef.Commit(commitInput)
                                            state.requestWithTrustCheck(repo) {
                                                state.resolveRef(ref, repo)
                                            }
                                        }
                                    },
                                    enabled = commitInput.isNotBlank() && !isInstalling,
                                ) {
                                    Text("Resolve", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    // Resolution feedback
                    val resolvedCheck by state.resolvedCheck.collectAsState()
                    val isResolving by state.isResolving.collectAsState()
                    val resolveError by state.resolveError.collectAsState()
                    val selectedRef by state.selectedRef.collectAsState()

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
                        val refText =
                            when (val r = selectedRef) {
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
            }

            HorizontalDivider()

            // Tools outputs
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
                    for (tool in outputs) {
                        val isIntact = currentGroup.current?.intact == true
                        val symbol = if (isIntact) "✓" else "✕"
                        Text(
                            text = "${tool.toolId} $symbol",
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

            HorizontalDivider()

            // While installing section
            if (isInstalling) {
                val op = currentGroup.operation
                val phaseText = op?.phase?.name ?: "Building"
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        if (isReducedMotion) {
                            Text(
                                text = "Installing · $phaseText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(IconSize.Medium),
                                strokeWidth = StrokeWidth.Focused,
                            )
                            Text(
                                text = "Installing · $phaseText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    GlassTextButton(onClick = { state.cancelActiveOperation() }) {
                        Text("Cancel", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            // Primary action button
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
                        enabled = !isInstalling && selectedVersion.let { state.canInstallSelected() },
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

            if (primaryAction == PrimaryButtonAction.InstallAndUse && !isInstalling && !state.canInstallSelected()) {
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

            if (primaryDisabledReason != null) {
                Text(
                    text = primaryDisabledReason!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun repoFor(isCustom: Boolean, customInput: String, recommended: String?): String =
    if (isCustom) customInput else recommended.orEmpty()
