/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideActionsAddDirectory
import org.ide.lti.core.designsystem.generated.resources.ideActionsAddExcludedRoot
import org.ide.lti.core.designsystem.generated.resources.ideActionsAddFile
import org.ide.lti.core.designsystem.generated.resources.ideActionsAddLink
import org.ide.lti.core.designsystem.generated.resources.ideActionsAddToDictionary
import org.ide.lti.core.designsystem.generated.resources.ideActionsAiIntentionBulb
import org.ide.lti.core.designsystem.generated.resources.ideActionsAttach
import org.ide.lti.core.designsystem.generated.resources.ideActionsBuildAutoReloadChanges
import org.ide.lti.core.designsystem.generated.resources.ideActionsBuildLoadChanges
import org.ide.lti.core.designsystem.generated.resources.ideActionsChangeView
import org.ide.lti.core.designsystem.generated.resources.ideActionsClearCash
import org.ide.lti.core.designsystem.generated.resources.ideActionsDeploy
import org.ide.lti.core.designsystem.generated.resources.ideActionsDiagramDiff
import org.ide.lti.core.designsystem.generated.resources.ideActionsDiffWithClipboard
import org.ide.lti.core.designsystem.generated.resources.ideActionsFilterdups
import org.ide.lti.core.designsystem.generated.resources.ideActionsFindBackward
import org.ide.lti.core.designsystem.generated.resources.ideActionsFindEntireFile
import org.ide.lti.core.designsystem.generated.resources.ideActionsFindForward
import org.ide.lti.core.designsystem.generated.resources.ideActionsForceRefresh
import org.ide.lti.core.designsystem.generated.resources.ideActionsGenerated
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByClass
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByFile
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByMethod
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByModule
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByModuleGroup
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByPackage
import org.ide.lti.core.designsystem.generated.resources.ideActionsGroupByTestProduction
import org.ide.lti.core.designsystem.generated.resources.ideActionsHighlighting
import org.ide.lti.core.designsystem.generated.resources.ideActionsInSelection
import org.ide.lti.core.designsystem.generated.resources.ideActionsInstall
import org.ide.lti.core.designsystem.generated.resources.ideActionsLearn
import org.ide.lti.core.designsystem.generated.resources.ideActionsLightning
import org.ide.lti.core.designsystem.generated.resources.ideActionsMinimap
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToBottomLeft
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToBottomRight
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToButton
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToLeftBottom
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToLeftTop
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToRightBottom
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToRightTop
import org.ide.lti.core.designsystem.generated.resources.ideActionsMoveToWindow
import org.ide.lti.core.designsystem.generated.resources.ideActionsNewFolder
import org.ide.lti.core.designsystem.generated.resources.ideActionsNotificationsBottomLeft
import org.ide.lti.core.designsystem.generated.resources.ideActionsNotificationsBottomRight
import org.ide.lti.core.designsystem.generated.resources.ideActionsNotificationsTopLeft
import org.ide.lti.core.designsystem.generated.resources.ideActionsNotificationsTopRight
import org.ide.lti.core.designsystem.generated.resources.ideActionsPlayBack
import org.ide.lti.core.designsystem.generated.resources.ideActionsPlayFirst
import org.ide.lti.core.designsystem.generated.resources.ideActionsPlayForward
import org.ide.lti.core.designsystem.generated.resources.ideActionsPlayLast
import org.ide.lti.core.designsystem.generated.resources.ideActionsPopFrame
import org.ide.lti.core.designsystem.generated.resources.ideActionsPreview
import org.ide.lti.core.designsystem.generated.resources.ideActionsProfileBlue
import org.ide.lti.core.designsystem.generated.resources.ideActionsProfileCPU
import org.ide.lti.core.designsystem.generated.resources.ideActionsProfileMemory
import org.ide.lti.core.designsystem.generated.resources.ideActionsProfileRed
import org.ide.lti.core.designsystem.generated.resources.ideActionsProfileYellow
import org.ide.lti.core.designsystem.generated.resources.ideActionsProjectDirectory
import org.ide.lti.core.designsystem.generated.resources.ideActionsProperties
import org.ide.lti.core.designsystem.generated.resources.ideActionsRealIntentionBulb
import org.ide.lti.core.designsystem.generated.resources.ideActionsRefactoringBulb
import org.ide.lti.core.designsystem.generated.resources.ideActionsReformatCode
import org.ide.lti.core.designsystem.generated.resources.ideActionsReplace
import org.ide.lti.core.designsystem.generated.resources.ideActionsReport
import org.ide.lti.core.designsystem.generated.resources.ideActionsRerunAutomatically
import org.ide.lti.core.designsystem.generated.resources.ideActionsRestartStop
import org.ide.lti.core.designsystem.generated.resources.ideActionsRunAll
import org.ide.lti.core.designsystem.generated.resources.ideActionsSelectAll
import org.ide.lti.core.designsystem.generated.resources.ideActionsShortcutFilter
import org.ide.lti.core.designsystem.generated.resources.ideActionsShowImportStatements
import org.ide.lti.core.designsystem.generated.resources.ideActionsShowReadAccess
import org.ide.lti.core.designsystem.generated.resources.ideActionsShowWriteAccess
import org.ide.lti.core.designsystem.generated.resources.ideActionsSplit
import org.ide.lti.core.designsystem.generated.resources.ideActionsStartMemoryProfile
import org.ide.lti.core.designsystem.generated.resources.ideActionsStopRefresh
import org.ide.lti.core.designsystem.generated.resources.ideActionsStopWatch
import org.ide.lti.core.designsystem.generated.resources.ideActionsSuggestedRefactoringBulb
import org.ide.lti.core.designsystem.generated.resources.ideActionsSwapPanels
import org.ide.lti.core.designsystem.generated.resources.ideActionsSynchronizeScrolling
import org.ide.lti.core.designsystem.generated.resources.ideActionsToggleVisibility
import org.ide.lti.core.designsystem.generated.resources.ideActionsUndeploy
import org.ide.lti.core.designsystem.generated.resources.ideActionsUninstall
import org.ide.lti.core.designsystem.generated.resources.ideActionsUnselectAll
import org.ide.lti.core.designsystem.generated.resources.ideActionsUnshare
import org.ide.lti.core.designsystem.generated.resources.ideActionsViewAsImage
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "actions" icon category.
 * Auto-generated from the bulk-imported expui/actions/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideActionsAddDirectory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAddDirectory) }
val AppIcons.ideActionsAddExcludedRoot: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAddExcludedRoot) }
val AppIcons.ideActionsAddFile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAddFile) }
val AppIcons.ideActionsAddLink: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAddLink) }
val AppIcons.ideActionsAddToDictionary: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAddToDictionary) }
val AppIcons.ideActionsAiIntentionBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAiIntentionBulb) }
val AppIcons.ideActionsAttach: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsAttach) }
val AppIcons.ideActionsBuildAutoReloadChanges: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsBuildAutoReloadChanges) }
val AppIcons.ideActionsBuildLoadChanges: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsBuildLoadChanges) }
val AppIcons.ideActionsChangeView: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsChangeView) }
val AppIcons.ideActionsClearCash: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsClearCash) }
val AppIcons.ideActionsDeploy: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsDeploy) }
val AppIcons.ideActionsDiagramDiff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsDiagramDiff) }
val AppIcons.ideActionsDiffWithClipboard: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsDiffWithClipboard) }
val AppIcons.ideActionsFilterdups: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsFilterdups) }
val AppIcons.ideActionsFindBackward: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsFindBackward) }
val AppIcons.ideActionsFindEntireFile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsFindEntireFile) }
val AppIcons.ideActionsFindForward: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsFindForward) }
val AppIcons.ideActionsForceRefresh: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsForceRefresh) }
val AppIcons.ideActionsGenerated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGenerated) }
val AppIcons.ideActionsGroupByClass: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByClass) }
val AppIcons.ideActionsGroupByFile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByFile) }
val AppIcons.ideActionsGroupByMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByMethod) }
val AppIcons.ideActionsGroupByModule: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByModule) }
val AppIcons.ideActionsGroupByModuleGroup: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByModuleGroup) }
val AppIcons.ideActionsGroupByPackage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByPackage) }
val AppIcons.ideActionsGroupByTestProduction: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsGroupByTestProduction) }
val AppIcons.ideActionsHighlighting: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsHighlighting) }
val AppIcons.ideActionsInSelection: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsInSelection) }
val AppIcons.ideActionsInstall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsInstall) }
val AppIcons.ideActionsLearn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsLearn) }
val AppIcons.ideActionsLightning: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsLightning) }
val AppIcons.ideActionsMinimap: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMinimap) }
val AppIcons.ideActionsMoveToBottomLeft: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToBottomLeft) }
val AppIcons.ideActionsMoveToBottomRight: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToBottomRight) }
val AppIcons.ideActionsMoveToButton: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToButton) }
val AppIcons.ideActionsMoveToLeftBottom: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToLeftBottom) }
val AppIcons.ideActionsMoveToLeftTop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToLeftTop) }
val AppIcons.ideActionsMoveToRightBottom: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToRightBottom) }
val AppIcons.ideActionsMoveToRightTop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToRightTop) }
val AppIcons.ideActionsMoveToWindow: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsMoveToWindow) }
val AppIcons.ideActionsNewFolder: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsNewFolder) }
val AppIcons.ideActionsNotificationsBottomLeft: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsNotificationsBottomLeft) }
val AppIcons.ideActionsNotificationsBottomRight: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsNotificationsBottomRight) }
val AppIcons.ideActionsNotificationsTopLeft: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsNotificationsTopLeft) }
val AppIcons.ideActionsNotificationsTopRight: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsNotificationsTopRight) }
val AppIcons.ideActionsPlayBack: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPlayBack) }
val AppIcons.ideActionsPlayFirst: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPlayFirst) }
val AppIcons.ideActionsPlayForward: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPlayForward) }
val AppIcons.ideActionsPlayLast: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPlayLast) }
val AppIcons.ideActionsPopFrame: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPopFrame) }
val AppIcons.ideActionsPreview: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsPreview) }
val AppIcons.ideActionsProfileBlue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProfileBlue) }
val AppIcons.ideActionsProfileCPU: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProfileCPU) }
val AppIcons.ideActionsProfileMemory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProfileMemory) }
val AppIcons.ideActionsProfileRed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProfileRed) }
val AppIcons.ideActionsProfileYellow: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProfileYellow) }
val AppIcons.ideActionsProjectDirectory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProjectDirectory) }
val AppIcons.ideActionsProperties: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsProperties) }
val AppIcons.ideActionsRealIntentionBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsRealIntentionBulb) }
val AppIcons.ideActionsRefactoringBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsRefactoringBulb) }
val AppIcons.ideActionsReformatCode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsReformatCode) }
val AppIcons.ideActionsReplace: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsReplace) }
val AppIcons.ideActionsReport: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsReport) }
val AppIcons.ideActionsRerunAutomatically: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsRerunAutomatically) }
val AppIcons.ideActionsRestartStop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsRestartStop) }
val AppIcons.ideActionsRunAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsRunAll) }
val AppIcons.ideActionsSelectAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsSelectAll) }
val AppIcons.ideActionsShortcutFilter: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsShortcutFilter) }
val AppIcons.ideActionsShowImportStatements: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsShowImportStatements) }
val AppIcons.ideActionsShowReadAccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsShowReadAccess) }
val AppIcons.ideActionsShowWriteAccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsShowWriteAccess) }
val AppIcons.ideActionsSplit: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsSplit) }
val AppIcons.ideActionsStartMemoryProfile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsStartMemoryProfile) }
val AppIcons.ideActionsStopRefresh: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsStopRefresh) }
val AppIcons.ideActionsStopWatch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsStopWatch) }
val AppIcons.ideActionsSuggestedRefactoringBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsSuggestedRefactoringBulb) }
val AppIcons.ideActionsSwapPanels: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsSwapPanels) }
val AppIcons.ideActionsSynchronizeScrolling: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsSynchronizeScrolling) }
val AppIcons.ideActionsToggleVisibility: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsToggleVisibility) }
val AppIcons.ideActionsUndeploy: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsUndeploy) }
val AppIcons.ideActionsUninstall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsUninstall) }
val AppIcons.ideActionsUnselectAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsUnselectAll) }
val AppIcons.ideActionsUnshare: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsUnshare) }
val AppIcons.ideActionsViewAsImage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideActionsViewAsImage) }
