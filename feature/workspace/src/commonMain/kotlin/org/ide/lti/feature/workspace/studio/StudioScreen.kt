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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import org.ide.lti.core.designsystem.component.actions.GlassButton
import org.ide.lti.core.designsystem.component.actions.GlassButtonVariant
import org.ide.lti.core.designsystem.component.actions.GlassIconButton
import org.ide.lti.core.designsystem.component.actions.GlassTextButton
import org.ide.lti.core.designsystem.component.display.GlassCard
import org.ide.lti.core.designsystem.component.display.GlassPanel
import org.ide.lti.core.designsystem.component.display.GlassVerticalDivider
import org.ide.lti.core.designsystem.component.primitives.glassOutlineBorder
import org.ide.lti.core.designsystem.icon.AppIcons
import org.ide.lti.core.designsystem.theme.AlphaTokens
import org.ide.lti.core.designsystem.theme.ComponentSize
import org.ide.lti.core.designsystem.theme.GlassShapes
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.ide.lti.core.designsystem.theme.Spacing
import org.ide.lti.core.designsystem.theme.StrokeWidth
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.studio.StudioPresentation
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import java.awt.Cursor

private enum class StudioFocusArea {
    RAIL,
    NAVIGATOR,
    EDITOR,
    DOCK,
    ;

    fun next(): StudioFocusArea = when (this) {
        RAIL -> NAVIGATOR
        NAVIGATOR -> EDITOR
        EDITOR -> DOCK
        DOCK -> RAIL
    }
}

/**
 * Three-pane host shell for ROM Setup Studio.
 *
 * Shell Architecture:
 * - Stage Rail: 56 logical units wide, hosts the 8 pipeline stages from [StudioDestinationCatalog].
 * - Object Navigator: 240 default, user-adjustable 200..360, collapses into an accessible drawer at <= 900x600.
 * - Center Pane: Displays [StudioOverview] (Overview mode) or [StageObjectEditor] (Editor mode).
 * - Bounded Dock: Bottom collapsible activity/problems/artifacts dock with independent scroll region.
 *
 * House Rules & Invariants:
 * - Stage status is distinguishable without color alone (both shape/icons and textual labels).
 * - Stage badges reflect configuration validation ONLY (execution state belongs to the Run view).
 * - Keyboard shortcuts are registered only within studio scope and do not intercept text inputs.
 * - Form layouts adapt at 200% text scale with header actions moving to overflow while Save/Cancel remain reachable.
 */
@Composable
public fun StudioScreen(
    workspaceId: String,
    modifier: Modifier = Modifier,
    initialStageId: StageId? = null,
    initialObjectId: String? = null,
    onNavigateBack: () -> Unit = {},
    onNavigateToRun: (workspaceId: String, runId: String) -> Unit = { _, _ -> },
    onNavigateToPlugins: (workspaceId: String?, packageId: String?, destination: String) -> Unit = { _, _, _ -> },
    onNavigateToSetup: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: StudioViewModel = koinViewModel { parametersOf(workspaceId) },
) {
    val uiState by viewModel.uiState.collectAsState()
    val glassColors = GlassTheme.colors
    val density = LocalDensity.current

    LaunchedEffect(workspaceId, initialStageId, initialObjectId) {
        viewModel.onEntry(
            workspaceId = workspaceId,
            requestedStageId = initialStageId,
            requestedObjectId = initialObjectId,
        )
    }

    // Keyboard focus requesters
    val railFocusRequester = remember { FocusRequester() }
    val navigatorFocusRequester = remember { FocusRequester() }
    val editorFocusRequester = remember { FocusRequester() }
    val dockFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }

    var focusedArea by remember { mutableStateOf(StudioFocusArea.EDITOR) }
    var isOverflowMenuOpen by remember { mutableStateOf(false) }

    // Root keyboard dispatch
    val focusRequesters = StudioFocusRequesters(
        railFocusRequester,
        navigatorFocusRequester,
        editorFocusRequester,
        dockFocusRequester,
    )
    val keyboardModifier = Modifier.onPreviewKeyEvent { event ->
        handleStudioKeyEvent(
            event = event,
            focusedArea = focusedArea,
            onFocusedAreaChange = { focusedArea = it },
            focusRequesters = focusRequesters,
            isOverflowMenuOpen = isOverflowMenuOpen,
            onCloseOverflowMenu = { isOverflowMenuOpen = false },
            uiState = uiState,
            viewModel = viewModel,
            searchFocusRequester = searchFocusRequester,
        )
    }

    CompositionLocalProvider(LocalContentColor provides glassColors.onGlass) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxSize()
                .then(keyboardModifier),
        ) {
            val isCompact = maxWidth <= ComponentSize.StudioCompactWindowWidth ||
                maxHeight <= ComponentSize.StudioCompactWindowHeight
            val isHighTextScale = density.fontScale >= 2.0f

            val currentStageId = (uiState.entry as? StudioEntry.Editor)?.stageId
                ?: uiState.presentation.editorStageId
                ?: StudioDestinationCatalog.stages.first().stageId
            val currentStage = StudioDestinationCatalog.stage(currentStageId)
                ?: StudioDestinationCatalog.stages.first()

            val selectedObjectId = (uiState.entry as? StudioEntry.Editor)?.objectId
                ?: uiState.presentation.editorObjectId
                ?: currentStage.subobjects.firstOrNull()?.id

            StudioThreePaneLayout(
                isCompact = isCompact,
                isHighTextScale = isHighTextScale,
                currentStage = currentStage,
                selectedObjectId = selectedObjectId,
                uiState = uiState,
                viewModel = viewModel,
                density = density,
                focusRequesters = focusRequesters,
                searchFocusRequester = searchFocusRequester,
                isOverflowMenuOpen = isOverflowMenuOpen,
                onToggleOverflowMenu = { isOverflowMenuOpen = !isOverflowMenuOpen },
                onNavigateToRun = { onNavigateToRun(uiState.workspaceId, "run-latest") },
                onNavigateToSetup = onNavigateToSetup,
                onNavigateBack = onNavigateBack,
                onOpenPlugins = { onNavigateToPlugins(uiState.workspaceId, null, "marketplace") },
                onOpenSettings = onOpenSettings,
            )

            // ==================== RESPONSIVE COMPACT DRAWER (<= 900x600) ====================
            if (isCompact && uiState.isNavigatorDrawerOpen) {
                StudioCompactDrawer(
                    currentStage = currentStage,
                    selectedObjectId = selectedObjectId,
                    uiState = uiState,
                    viewModel = viewModel,
                    searchFocusRequester = searchFocusRequester,
                )
            }
        }
    }
}

@Suppress("LongParameterList") // Every parameter is a distinct piece of shell state; grouping further
// would just move the count into a wrapper class with no fewer moving parts.
@Composable
private fun StudioThreePaneLayout(
    isCompact: Boolean,
    isHighTextScale: Boolean,
    currentStage: StudioStageDestination,
    selectedObjectId: String?,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    density: Density,
    focusRequesters: StudioFocusRequesters,
    searchFocusRequester: FocusRequester,
    isOverflowMenuOpen: Boolean,
    onToggleOverflowMenu: () -> Unit,
    onNavigateToRun: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val glassColors = GlassTheme.colors
    Row(modifier = Modifier.fillMaxSize()) {
        // ==================== PANE 1: STAGE RAIL (56 units wide) ====================
        StageRail(
            stages = uiState.railItems,
            isOverviewActive = uiState.entry is StudioEntry.Overview,
            onStageClick = { stageId -> viewModel.selectStage(stageId) },
            onOverviewClick = { viewModel.openOverview() },
            onToggleDrawer = if (isCompact) {
                { viewModel.toggleNavigatorDrawer() }
            } else {
                null
            },
            modifier = Modifier
                .width(ComponentSize.StudioStageRailWidth)
                .fillMaxHeight()
                .focusRequester(focusRequesters.rail)
                .focusable(),
        )

        // ==================== PANE 2: OBJECT NAVIGATOR (Standard Mode) ====================
        if (!isCompact) {
            StudioNavigatorPane(
                currentStage = currentStage,
                selectedObjectId = selectedObjectId,
                uiState = uiState,
                viewModel = viewModel,
                density = density,
                navigatorFocusRequester = focusRequesters.navigator,
                searchFocusRequester = searchFocusRequester,
            )
        }

        // ==================== PANE 3: CENTER PANE & DOCK ====================
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            StudioTopBar(
                entry = uiState.entry,
                stage = currentStage,
                objectId = selectedObjectId,
                validationReport = uiState.validationReport,
                isHighTextScale = isHighTextScale,
                isOverflowOpen = isOverflowMenuOpen,
                onToggleOverflow = onToggleOverflowMenu,
                onSave = { viewModel.requestSave() },
                onNavigateToRun = onNavigateToRun,
                onNavigateToSetup = onNavigateToSetup,
                onNavigateBack = onNavigateBack,
                onOpenPlugins = onOpenPlugins,
                onOpenSettings = onOpenSettings,
            )

            // Hairline divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Hairline)
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
            )

            StudioCenterContent(
                currentStage = currentStage,
                selectedObjectId = selectedObjectId,
                uiState = uiState,
                viewModel = viewModel,
                editorFocusRequester = focusRequesters.editor,
                modifier = Modifier.weight(1f),
            )

            // Save notification banner
            if (uiState.saveRequestedNotification != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(glassColors.diagnostics.success.copy(alpha = AlphaTokens.Hover))
                        .padding(Spacing.Small),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = uiState.saveRequestedNotification ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = glassColors.diagnostics.success,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            // Bounded Activity / Problems / Artifacts Dock
            StudioDock(
                activeTab = uiState.activeDockTab,
                isExpanded = uiState.isDockExpanded,
                validationReport = uiState.validationReport,
                onTabSelect = { viewModel.setActiveDockTab(it) },
                onToggleExpand = { viewModel.toggleDock() },
                onSelectIssue = { sId, oId -> if (sId != null && oId != null) viewModel.selectObject(sId, oId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequesters.dock)
                    .focusable(),
            )
        }
    }
}

// Center Content (Overview OR Stage Editor Placeholder)
@Composable
private fun StudioCenterContent(
    currentStage: StudioStageDestination,
    selectedObjectId: String?,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    editorFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(editorFocusRequester)
            .focusable(),
    ) {
        when (uiState.entry) {
            is StudioEntry.Overview -> {
                StudioOverview(
                    workspaceId = uiState.workspaceId,
                    validationReport = uiState.validationReport,
                    availableWorkspaces = uiState.availableWorkspaces,
                    onSelectStage = { viewModel.selectStage(it) },
                    onSelectObject = { sId, oId -> viewModel.selectObject(sId, oId) },
                    onSelectWorkspace = { viewModel.onEntry(it) },
                    onReturnToEditor = { viewModel.returnToEditor() },
                )
            }
            is StudioEntry.Editor -> {
                StageObjectEditor(
                    stage = currentStage,
                    objectId = selectedObjectId,
                    validationErrors = uiState.validationReport.errors.filter {
                        it.stageId == currentStage.stageId && it.objectId == selectedObjectId
                    },
                    onSave = { viewModel.requestSave() },
                )
            }
        }
    }
}

/** The four focus targets F6 cycles between, bundled so the keyboard handler takes one parameter. */
private data class StudioFocusRequesters(
    val rail: FocusRequester,
    val navigator: FocusRequester,
    val editor: FocusRequester,
    val dock: FocusRequester,
)

/**
 * Root keyboard dispatch: F6 cycles rail -> navigator -> editor -> activity, Escape closes the topmost
 * transient only, Ctrl+F focuses navigator search, Ctrl+S requests Save, Ctrl+1..8 selects a stage.
 * Never intercepts ordinary text-editing keys - anything unmatched falls through (returns false).
 */
private fun handleStudioKeyEvent(
    event: KeyEvent,
    focusedArea: StudioFocusArea,
    onFocusedAreaChange: (StudioFocusArea) -> Unit,
    focusRequesters: StudioFocusRequesters,
    isOverflowMenuOpen: Boolean,
    onCloseOverflowMenu: () -> Unit,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    searchFocusRequester: FocusRequester,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val isCtrlOrMeta = event.isCtrlPressed || event.isMetaPressed

    return when {
        event.key == Key.F6 -> {
            cycleStudioFocus(focusedArea, onFocusedAreaChange, focusRequesters)
            true
        }
        event.key == Key.Escape ->
            closeTopmostTransient(isOverflowMenuOpen, onCloseOverflowMenu, uiState, viewModel)
        isCtrlOrMeta && event.key == Key.F -> {
            searchFocusRequester.requestFocus()
            true
        }
        isCtrlOrMeta && event.key == Key.S -> {
            viewModel.requestSave()
            true
        }
        isCtrlOrMeta && isDigitOneThroughEight(event.key) -> selectStageByDigit(event.key, viewModel)
        else -> false
    }
}

private fun cycleStudioFocus(
    focusedArea: StudioFocusArea,
    onFocusedAreaChange: (StudioFocusArea) -> Unit,
    focusRequesters: StudioFocusRequesters,
) {
    val nextArea = focusedArea.next()
    onFocusedAreaChange(nextArea)
    when (nextArea) {
        StudioFocusArea.RAIL -> focusRequesters.rail.requestFocus()
        StudioFocusArea.NAVIGATOR -> focusRequesters.navigator.requestFocus()
        StudioFocusArea.EDITOR -> focusRequesters.editor.requestFocus()
        StudioFocusArea.DOCK -> focusRequesters.dock.requestFocus()
    }
}

// Escape closes only the topmost transient: overflow menu, then drawer, then search, then the dock.
private fun closeTopmostTransient(
    isOverflowMenuOpen: Boolean,
    onCloseOverflowMenu: () -> Unit,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
): Boolean = when {
    isOverflowMenuOpen -> {
        onCloseOverflowMenu()
        true
    }
    uiState.isNavigatorDrawerOpen -> {
        viewModel.closeNavigatorDrawer()
        true
    }
    uiState.searchQuery.isNotEmpty() -> {
        viewModel.setSearchQuery("")
        true
    }
    uiState.isDockExpanded -> {
        viewModel.toggleDock()
        true
    }
    else -> false
}

private fun selectStageByDigit(key: Key, viewModel: StudioViewModel): Boolean {
    val stage = StudioDestinationCatalog.stages.getOrNull(digitToIndex(key)) ?: return false
    viewModel.selectStage(stage.stageId)
    return true
}

// Standard-width object navigator plus its resizable drag divider (900x600+ layouts).
@Composable
private fun StudioNavigatorPane(
    currentStage: StudioStageDestination,
    selectedObjectId: String?,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    density: Density,
    navigatorFocusRequester: FocusRequester,
    searchFocusRequester: FocusRequester,
) {
    val navigatorWidth = ComponentSize.studioNavigatorWidth(
        uiState.presentation.navigatorWidth.coerceIn(
            StudioPresentation.MIN_NAVIGATOR_WIDTH,
            StudioPresentation.MAX_NAVIGATOR_WIDTH,
        ),
    )

    Box(
        modifier = Modifier
            .width(navigatorWidth)
            .widthIn(min = ComponentSize.StudioNavigatorMinWidth, max = ComponentSize.StudioNavigatorMaxWidth)
            .fillMaxHeight()
            .focusRequester(navigatorFocusRequester)
            .focusable(),
    ) {
        StageObjectNavigator(
            currentStage = currentStage,
            selectedObjectId = selectedObjectId,
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onSelectObject = { stageId, objId -> viewModel.selectObject(stageId, objId) },
            onOpenOverview = { viewModel.openOverview() },
            validationErrors = uiState.validationReport.errors,
            searchFocusRequester = searchFocusRequester,
        )
    }

    // Resizable drag divider
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(Spacing.ExtraSmall)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.E_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, dragAmount ->
                    val currentWidth = uiState.presentation.navigatorWidth
                    val deltaDp = with(density) { dragAmount.toDp().value.toInt() }
                    viewModel.setNavigatorWidth(
                        (currentWidth + deltaDp).coerceIn(
                            StudioPresentation.MIN_NAVIGATOR_WIDTH,
                            StudioPresentation.MAX_NAVIGATOR_WIDTH,
                        ),
                    )
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        GlassVerticalDivider()
    }
}

// Accessible drawer replacing the standard navigator pane at <= 900x600 (contract requirement).
@Composable
private fun StudioCompactDrawer(
    currentStage: StudioStageDestination,
    selectedObjectId: String?,
    uiState: StudioUiState,
    viewModel: StudioViewModel,
    searchFocusRequester: FocusRequester,
) {
    val glassColors = GlassTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(glassColors.glassShadow.copy(alpha = AlphaTokens.Scrim))
            .clickable { viewModel.closeNavigatorDrawer() },
    ) {
        AnimatedVisibility(
            visible = uiState.isNavigatorDrawerOpen,
            enter = slideInHorizontally(),
            exit = slideOutHorizontally(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = ComponentSize.StudioStageRailWidth),
        ) {
            GlassPanel(
                width = ComponentSize.StudioNavigatorDefaultWidth,
                contentPadding = Spacing.None,
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable(enabled = false) {},
            ) {
                StageObjectNavigator(
                    currentStage = currentStage,
                    selectedObjectId = selectedObjectId,
                    searchQuery = uiState.searchQuery,
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onSelectObject = { stageId, objId ->
                        viewModel.selectObject(stageId, objId)
                        viewModel.closeNavigatorDrawer()
                    },
                    onOpenOverview = {
                        viewModel.openOverview()
                        viewModel.closeNavigatorDrawer()
                    },
                    validationErrors = uiState.validationReport.errors,
                    searchFocusRequester = searchFocusRequester,
                )
            }
        }
    }
}

/**
 * Vertical Stage Rail (56 logical units wide).
 * Distinguishable WITHOUT color alone (icon shape + status shape/text badge).
 */
@Composable
private fun StageRail(
    stages: List<StudioRailItem>,
    isOverviewActive: Boolean,
    onStageClick: (StageId) -> Unit,
    onOverviewClick: () -> Unit,
    onToggleDrawer: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    Row(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
                .padding(vertical = Spacing.Small),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
        ) {
            // Overview toggle button
            GlassIconButton(
                onClick = onOverviewClick,
                size = ComponentSize.SidePanelRailButtonSize,
                selected = isOverviewActive,
                modifier = Modifier.semantics {
                    contentDescription = "ROM Setup Studio Overview"
                    role = Role.Tab
                },
            ) {
                Icon(
                    painter = AppIcons.HomePainterResource(),
                    contentDescription = "Overview",
                    modifier = Modifier.size(IconSize.SidePanelRail),
                    tint = if (isOverviewActive) glassColors.onGlass else glassColors.onGlassMuted,
                )
            }

            // Compact mode drawer toggle button
            if (onToggleDrawer != null) {
                GlassIconButton(
                    onClick = onToggleDrawer,
                    size = ComponentSize.SidePanelRailButtonSize,
                    modifier = Modifier.semantics {
                        contentDescription = "Toggle Stage Objects Navigator Drawer"
                        role = Role.Button
                    },
                ) {
                    Icon(
                        painter = AppIcons.FolderPainterResource(),
                        contentDescription = "Objects",
                        modifier = Modifier.size(IconSize.SidePanelRail),
                        tint = glassColors.onGlassSecondary,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(StrokeWidth.Hairline)
                    .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
            )

            // 8 Stage Items
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                items(
                    items = stages,
                    key = { it.stage.stageId.name },
                ) { railItem ->
                    StageRailButton(
                        item = railItem,
                        onClick = { onStageClick(railItem.stage.stageId) },
                    )
                }
            }
        }

        // Inner vertical hairline divider separating rail from navigator
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(StrokeWidth.Hairline)
                .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
        )
    }
}

@Composable
private fun StageRailButton(item: StudioRailItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val glassColors = GlassTheme.colors
    val shape = GlassShapes.Compact

    val backgroundColor = when {
        item.isSelected -> glassColors.glassAccent.copy(alpha = AlphaTokens.Hover)
        else -> glassColors.glassSurfaceElevated.copy(alpha = AlphaTokens.UltraFaint)
    }

    val borderColor = when {
        item.isSelected -> glassColors.glassBorderHighlight.copy(alpha = AlphaTokens.Prominent)
        else -> glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)
    }

    Box(
        modifier = modifier
            .size(ComponentSize.SidePanelRailButtonSize)
            .clip(shape)
            .background(backgroundColor)
            .glassOutlineBorder(
                width = StrokeWidth.Hairline,
                color = borderColor,
                shape = shape,
            )
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "${item.stage.accessibleLabel}: ${item.statusLabel}"
                role = Role.Tab
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = item.stage.icon(),
            contentDescription = null,
            modifier = Modifier.size(IconSize.SidePanelRail),
            tint = if (item.isSelected) glassColors.onGlass else glassColors.onGlassSecondary,
        )

        // Status shape badge (distinguishable WITHOUT color alone: circle vs triangle/dot)
        if (item.errorCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.ExtraExtraSmall)
                    .size(ComponentSize.StatusDotSize)
                    .clip(GlassShapes.Pill)
                    .background(glassColors.diagnostics.error),
            )
        } else if (item.warningCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.ExtraExtraSmall)
                    .size(ComponentSize.StatusDotSize)
                    .clip(GlassShapes.Compact)
                    .background(glassColors.diagnostics.warning),
            )
        }
    }
}

@Composable
private fun StudioTopBar(
    entry: StudioEntry,
    stage: StudioStageDestination,
    objectId: String?,
    validationReport: ValidationReport,
    isHighTextScale: Boolean,
    isOverflowOpen: Boolean,
    onToggleOverflow: () -> Unit,
    onSave: () -> Unit,
    onNavigateToRun: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onNavigateBack: () -> Unit,
    onOpenPlugins: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors
    val stageErrors = validationReport.errors.filter { it.stageId == stage.stageId }
    val errorCount = stageErrors.count { it.severity == Severity.ERROR }
    val warningCount = stageErrors.count { it.severity == Severity.WARNING }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(ComponentSize.TopBarHeight)
            .padding(horizontal = Spacing.Medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Title and Breadcrumbs
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassIconButton(
                icon = AppIcons.ArrowBack,
                onClick = onNavigateBack,
                contentDescription = "Back to workspace",
            )
            Text(
                text = "ROM Setup Studio",
                style = MaterialTheme.typography.titleMedium,
                color = glassColors.onGlass,
                fontWeight = FontWeight.Bold,
            )

            if (entry is StudioEntry.Editor && objectId != null) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = glassColors.onGlassMuted,
                )
                Text(
                    text = "${stage.title} / $objectId",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glassColors.onGlassSecondary,
                )
            }

            TopBarValidationBadge(errorCount = errorCount, warningCount = warningCount)
        }

        // Action controls (Save/Cancel always reachable; secondary actions overflow at 200% text scale)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            GlassButton(
                onClick = onSave,
                variant = GlassButtonVariant.Primary,
                shape = GlassShapes.Compact,
            ) {
                Text("Save (Ctrl+S)", color = glassColors.onGlass)
            }

            val actions = TopBarActions(
                onNavigateToRun = onNavigateToRun,
                onNavigateToSetup = onNavigateToSetup,
                onOpenPlugins = onOpenPlugins,
                onOpenSettings = onOpenSettings,
            )
            if (!isHighTextScale) {
                TopBarInlineActions(actions)
            } else {
                TopBarOverflowActions(actions, isOverflowOpen, onToggleOverflow)
            }
        }
    }
}

// Distinguishable WITHOUT color alone (icon + text). Configuration validation ONLY - never combines
// execution states, which belong to the Run view.
@Composable
private fun TopBarValidationBadge(errorCount: Int, warningCount: Int) {
    val glassColors = GlassTheme.colors
    val (color, painter, label) = when {
        errorCount > 0 ->
            Triple(glassColors.diagnostics.error, AppIcons.ClearPainterResource(), "$errorCount validation errors")
        warningCount > 0 -> Triple(
            glassColors.diagnostics.warning,
            AppIcons.InfoPainterResource(),
            "$warningCount validation warnings",
        )
        else -> Triple(glassColors.diagnostics.success, AppIcons.CheckPainterResource(), "Config Valid")
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.ExtraExtraSmall),
        modifier = Modifier
            .clip(GlassShapes.Pill)
            .background(color.copy(alpha = AlphaTokens.Hover))
            .padding(horizontal = Spacing.Small, vertical = Spacing.ExtraExtraSmall),
    ) {
        Icon(painter = painter, contentDescription = null, modifier = Modifier.size(IconSize.Small), tint = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

private data class TopBarActions(
    val onNavigateToRun: () -> Unit,
    val onNavigateToSetup: () -> Unit,
    val onOpenPlugins: () -> Unit,
    val onOpenSettings: () -> Unit,
)

// Normal text scale: secondary actions reachable directly (visibly unavailable execution affordance
// for Run belongs to the Run view, not this editor).
@Composable
private fun TopBarInlineActions(actions: TopBarActions) {
    val glassColors = GlassTheme.colors
    GlassButton(
        onClick = actions.onNavigateToRun,
        variant = GlassButtonVariant.Standard,
        shape = GlassShapes.Compact,
        enabled = true,
        contentPadding = PaddingValues(horizontal = Spacing.SmallMedium, vertical = Spacing.ExtraSmall),
    ) {
        Text("Go to Run View", color = glassColors.onGlassSecondary)
    }
    GlassTextButton(onClick = actions.onNavigateToSetup) {
        Text("Setup Doctor", color = glassColors.onGlassSecondary)
    }
    GlassTextButton(onClick = actions.onOpenPlugins) {
        Text("Plugins", color = glassColors.onGlassSecondary)
    }
    GlassIconButton(icon = AppIcons.SettingsOutlined, onClick = actions.onOpenSettings, contentDescription = "Settings")
}

// 200% text scale: secondary actions move into a labelled overflow menu; Save stays reachable outside it.
@Composable
private fun TopBarOverflowActions(actions: TopBarActions, isOpen: Boolean, onToggle: () -> Unit) {
    val glassColors = GlassTheme.colors
    Box {
        GlassButton(onClick = onToggle, variant = GlassButtonVariant.Standard, shape = GlassShapes.Compact) {
            Text("More Actions...", color = glassColors.onGlassSecondary)
        }
        if (isOpen) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clip(GlassShapes.Compact)
                    .background(glassColors.glassSurfaceElevated)
                    .padding(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.ExtraSmall),
            ) {
                val entries = listOf(
                    "Go to Run View" to actions.onNavigateToRun,
                    "Setup Doctor" to actions.onNavigateToSetup,
                    "Plugins" to actions.onOpenPlugins,
                    "Settings" to actions.onOpenSettings,
                )
                entries.forEach { (label, action) ->
                    GlassTextButton(
                        onClick = {
                            onToggle()
                            action()
                        },
                    ) {
                        Text(label, color = glassColors.onGlass)
                    }
                }
            }
        }
    }
}

/**
 * Placeholder editor for stages 1..8.
 * Affordances for execution are visibly unavailable rather than fake.
 */
@Composable
private fun StageObjectEditor(
    stage: StudioStageDestination,
    objectId: String?,
    validationErrors: List<ValidationError>,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = GlassTheme.colors

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        item(key = "editor-placeholder-card") {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = Spacing.Large,
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
                    ) {
                        Icon(
                            painter = stage.icon(),
                            contentDescription = stage.accessibleLabel,
                            modifier = Modifier.size(IconSize.ActionCard),
                            tint = glassColors.glassAccent,
                        )
                        Column {
                            Text(
                                text = "${stage.title} Editor: ${objectId ?: "Settings"}",
                                style = MaterialTheme.typography.titleLarge,
                                color = glassColors.onGlass,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = "Pipeline Stage ${stage.position} • Dedicated configuration editor " +
                                    "placeholder",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glassColors.onGlassSecondary,
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(StrokeWidth.Hairline)
                            .background(glassColors.glassBorder.copy(alpha = AlphaTokens.Faint)),
                    )

                    Text(
                        text = "The dedicated editor components for stage ${stage.title} " +
                            "(subobject: ${objectId ?: "default"}) are under active development. " +
                            "Configuration parameters defined in this stage can be reviewed below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = glassColors.onGlass,
                    )

                    // Execution-dependent affordances are visibly unavailable rather than fake
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                    ) {
                        GlassButton(
                            onClick = {},
                            enabled = false,
                            variant = GlassButtonVariant.Standard,
                            shape = GlassShapes.Compact,
                        ) {
                            Text("Execute Stage (Unavailable in Studio)", color = glassColors.onGlassMuted)
                        }

                        GlassButton(
                            onClick = onSave,
                            variant = GlassButtonVariant.Primary,
                            shape = GlassShapes.Compact,
                        ) {
                            Text("Save Draft", color = glassColors.onGlass)
                        }
                    }
                }
            }
        }

        if (validationErrors.isNotEmpty()) {
            item(key = "editor-validation-issues") {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = Spacing.Medium,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                        Text(
                            text = "Validation Issues for ${objectId ?: "this object"}:",
                            style = MaterialTheme.typography.titleSmall,
                            color = glassColors.diagnostics.error,
                            fontWeight = FontWeight.SemiBold,
                        )
                        validationErrors.forEach { err ->
                            Text(
                                text = "• [${err.fieldPath}] ${err.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = glassColors.onGlass,
                            )
                            if (err.remediation != null) {
                                Text(
                                    text = "  Remediation: ${err.remediation}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = glassColors.onGlassSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isDigitOneThroughEight(key: Key): Boolean = when (key) {
    Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight -> true
    else -> false
}

private fun digitToIndex(key: Key): Int = when (key) {
    Key.One -> 0
    Key.Two -> 1
    Key.Three -> 2
    Key.Four -> 3
    Key.Five -> 4
    Key.Six -> 5
    Key.Seven -> 6
    Key.Eight -> 7
    else -> 0
}
