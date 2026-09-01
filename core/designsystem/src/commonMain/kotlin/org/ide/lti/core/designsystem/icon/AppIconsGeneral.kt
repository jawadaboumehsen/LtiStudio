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
import org.ide.lti.core.designsystem.generated.resources.ideGeneralAutoscrollFromSource
import org.ide.lti.core.designsystem.generated.resources.ideGeneralAutoscrollToSource
import org.ide.lti.core.designsystem.generated.resources.ideGeneralBalloon
import org.ide.lti.core.designsystem.generated.resources.ideGeneralChevronDownLarge
import org.ide.lti.core.designsystem.generated.resources.ideGeneralChevronDownLargeWhite
import org.ide.lti.core.designsystem.generated.resources.ideGeneralChevronUp
import org.ide.lti.core.designsystem.generated.resources.ideGeneralChevronUpLarge
import org.ide.lti.core.designsystem.generated.resources.ideGeneralCloseSmall
import org.ide.lti.core.designsystem.generated.resources.ideGeneralCloseSmallHovered
import org.ide.lti.core.designsystem.generated.resources.ideGeneralCollapseAll
import org.ide.lti.core.designsystem.generated.resources.ideGeneralCut
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDebugDisabled
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDown
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDownload
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDrag
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDropdown
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDropdownGutter
import org.ide.lti.core.designsystem.generated.resources.ideGeneralEditorOnly
import org.ide.lti.core.designsystem.generated.resources.ideGeneralEditorPreview
import org.ide.lti.core.designsystem.generated.resources.ideGeneralEditorPreviewVertical
import org.ide.lti.core.designsystem.generated.resources.ideGeneralErrorDialog
import org.ide.lti.core.designsystem.generated.resources.ideGeneralExit
import org.ide.lti.core.designsystem.generated.resources.ideGeneralExpandAll
import org.ide.lti.core.designsystem.generated.resources.ideGeneralExport
import org.ide.lti.core.designsystem.generated.resources.ideGeneralExternalTools
import org.ide.lti.core.designsystem.generated.resources.ideGeneralFilter
import org.ide.lti.core.designsystem.generated.resources.ideGeneralGreenCheckmark
import org.ide.lti.core.designsystem.generated.resources.ideGeneralGroups
import org.ide.lti.core.designsystem.generated.resources.ideGeneralHashtag
import org.ide.lti.core.designsystem.generated.resources.ideGeneralHelp
import org.ide.lti.core.designsystem.generated.resources.ideGeneralHistory
import org.ide.lti.core.designsystem.generated.resources.ideGeneralIdeUpdate
import org.ide.lti.core.designsystem.generated.resources.ideGeneralImport
import org.ide.lti.core.designsystem.generated.resources.ideGeneralIndentDetected
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsError
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsErrorEmpty
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsEye
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsGrammar
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsMixed
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsOK
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsOKEmpty
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsPause
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsPowerSaveMode
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsTrafficOff
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsTypos
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsWarning
import org.ide.lti.core.designsystem.generated.resources.ideGeneralInspectionsInspectionsWarningEmpty
import org.ide.lti.core.designsystem.generated.resources.ideGeneralKeyboard
import org.ide.lti.core.designsystem.generated.resources.ideGeneralLanguage
import org.ide.lti.core.designsystem.generated.resources.ideGeneralLayout
import org.ide.lti.core.designsystem.generated.resources.ideGeneralLearn
import org.ide.lti.core.designsystem.generated.resources.ideGeneralLeft
import org.ide.lti.core.designsystem.generated.resources.ideGeneralListFiles
import org.ide.lti.core.designsystem.generated.resources.ideGeneralLocate
import org.ide.lti.core.designsystem.generated.resources.ideGeneralMoreHorizontal
import org.ide.lti.core.designsystem.generated.resources.ideGeneralMoreVerticalStroke
import org.ide.lti.core.designsystem.generated.resources.ideGeneralMoveDown
import org.ide.lti.core.designsystem.generated.resources.ideGeneralMoveUp
import org.ide.lti.core.designsystem.generated.resources.ideGeneralOpen
import org.ide.lti.core.designsystem.generated.resources.ideGeneralOpenInToolWindow
import org.ide.lti.core.designsystem.generated.resources.ideGeneralOpenNewTab
import org.ide.lti.core.designsystem.generated.resources.ideGeneralOverriddenMethod
import org.ide.lti.core.designsystem.generated.resources.ideGeneralOverridingMethod
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPaste
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPin
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPinHovered
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPinSelected
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPinSelectedHovered
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPluginUpdate
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPreviewHorizontally
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPreviewOnly
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPreviewVertically
import org.ide.lti.core.designsystem.generated.resources.ideGeneralPrint
import org.ide.lti.core.designsystem.generated.resources.ideGeneralProjectConfigurable
import org.ide.lti.core.designsystem.generated.resources.ideGeneralProjectStructure
import org.ide.lti.core.designsystem.generated.resources.ideGeneralProjectWideAnalysisOff
import org.ide.lti.core.designsystem.generated.resources.ideGeneralProjectWideAnalysisOn
import org.ide.lti.core.designsystem.generated.resources.ideGeneralQuestionDialog
import org.ide.lti.core.designsystem.generated.resources.ideGeneralQuestionMark
import org.ide.lti.core.designsystem.generated.resources.ideGeneralReaderMode
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRedo
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRefreshAuto
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRelated
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRemove
import org.ide.lti.core.designsystem.generated.resources.ideGeneralReset
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRight
import org.ide.lti.core.designsystem.generated.resources.ideGeneralRunAnything
import org.ide.lti.core.designsystem.generated.resources.ideGeneralScrollDown
import org.ide.lti.core.designsystem.generated.resources.ideGeneralScrollUp
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSelectIn
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSeparatorHorizontal
import org.ide.lti.core.designsystem.generated.resources.ideGeneralShowAsTree
import org.ide.lti.core.designsystem.generated.resources.ideGeneralShowToImplement
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSoftWrap
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSplitHorizontally
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSplitVertically
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSuccessDialog
import org.ide.lti.core.designsystem.generated.resources.ideGeneralSuccessLogin
import org.ide.lti.core.designsystem.generated.resources.ideGeneralTree
import org.ide.lti.core.designsystem.generated.resources.ideGeneralTreeHovered
import org.ide.lti.core.designsystem.generated.resources.ideGeneralTreeSelected
import org.ide.lti.core.designsystem.generated.resources.ideGeneralUndo
import org.ide.lti.core.designsystem.generated.resources.ideGeneralUnlocked
import org.ide.lti.core.designsystem.generated.resources.ideGeneralUp
import org.ide.lti.core.designsystem.generated.resources.ideGeneralUpload
import org.ide.lti.core.designsystem.generated.resources.ideGeneralVcs
import org.ide.lti.core.designsystem.generated.resources.ideGeneralWarningDialog
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "general" icon category.
 * Auto-generated from the bulk-imported expui/general/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideGeneralAutoscrollFromSource: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralAutoscrollFromSource) }
val AppIcons.ideGeneralAutoscrollToSource: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralAutoscrollToSource) }
val AppIcons.ideGeneralBalloon: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralBalloon) }
val AppIcons.ideGeneralChevronDownLarge: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralChevronDownLarge) }
val AppIcons.ideGeneralChevronDownLargeWhite: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralChevronDownLargeWhite) }
val AppIcons.ideGeneralChevronUp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralChevronUp) }
val AppIcons.ideGeneralChevronUpLarge: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralChevronUpLarge) }
val AppIcons.ideGeneralCloseSmall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralCloseSmall) }
val AppIcons.ideGeneralCloseSmallHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralCloseSmallHovered) }
val AppIcons.ideGeneralCollapseAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralCollapseAll) }
val AppIcons.ideGeneralCut: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralCut) }
val AppIcons.ideGeneralDebugDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDebugDisabled) }
val AppIcons.ideGeneralDown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDown) }
val AppIcons.ideGeneralDownload: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDownload) }
val AppIcons.ideGeneralDrag: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDrag) }
val AppIcons.ideGeneralDropdown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDropdown) }
val AppIcons.ideGeneralDropdownGutter: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralDropdownGutter) }
val AppIcons.ideGeneralEditorOnly: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralEditorOnly) }
val AppIcons.ideGeneralEditorPreview: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralEditorPreview) }
val AppIcons.ideGeneralEditorPreviewVertical: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralEditorPreviewVertical) }
val AppIcons.ideGeneralErrorDialog: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralErrorDialog) }
val AppIcons.ideGeneralExit: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralExit) }
val AppIcons.ideGeneralExpandAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralExpandAll) }
val AppIcons.ideGeneralExport: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralExport) }
val AppIcons.ideGeneralExternalTools: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralExternalTools) }
val AppIcons.ideGeneralFilter: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralFilter) }
val AppIcons.ideGeneralGreenCheckmark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralGreenCheckmark) }
val AppIcons.ideGeneralGroups: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralGroups) }
val AppIcons.ideGeneralHashtag: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralHashtag) }
val AppIcons.ideGeneralHelp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralHelp) }
val AppIcons.ideGeneralHistory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralHistory) }
val AppIcons.ideGeneralIdeUpdate: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralIdeUpdate) }
val AppIcons.ideGeneralImport: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralImport) }
val AppIcons.ideGeneralIndentDetected: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralIndentDetected) }
val AppIcons.ideGeneralInspectionsInspectionsError: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsError) }
val AppIcons.ideGeneralInspectionsInspectionsErrorEmpty: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsErrorEmpty) }
val AppIcons.ideGeneralInspectionsInspectionsEye: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsEye) }
val AppIcons.ideGeneralInspectionsInspectionsGrammar: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsGrammar) }
val AppIcons.ideGeneralInspectionsInspectionsMixed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsMixed) }
val AppIcons.ideGeneralInspectionsInspectionsOK: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsOK) }
val AppIcons.ideGeneralInspectionsInspectionsOKEmpty: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsOKEmpty) }
val AppIcons.ideGeneralInspectionsInspectionsPause: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsPause) }
val AppIcons.ideGeneralInspectionsInspectionsPowerSaveMode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsPowerSaveMode) }
val AppIcons.ideGeneralInspectionsInspectionsTrafficOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsTrafficOff) }
val AppIcons.ideGeneralInspectionsInspectionsTypos: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsTypos) }
val AppIcons.ideGeneralInspectionsInspectionsWarning: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsWarning) }
val AppIcons.ideGeneralInspectionsInspectionsWarningEmpty: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralInspectionsInspectionsWarningEmpty) }
val AppIcons.ideGeneralKeyboard: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralKeyboard) }
val AppIcons.ideGeneralLanguage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralLanguage) }
val AppIcons.ideGeneralLayout: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralLayout) }
val AppIcons.ideGeneralLearn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralLearn) }
val AppIcons.ideGeneralLeft: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralLeft) }
val AppIcons.ideGeneralListFiles: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralListFiles) }
val AppIcons.ideGeneralLocate: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralLocate) }
val AppIcons.ideGeneralMoreHorizontal: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralMoreHorizontal) }
val AppIcons.ideGeneralMoreVerticalStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralMoreVerticalStroke) }
val AppIcons.ideGeneralMoveDown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralMoveDown) }
val AppIcons.ideGeneralMoveUp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralMoveUp) }
val AppIcons.ideGeneralOpen: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralOpen) }
val AppIcons.ideGeneralOpenInToolWindow: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralOpenInToolWindow) }
val AppIcons.ideGeneralOpenNewTab: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralOpenNewTab) }
val AppIcons.ideGeneralOverriddenMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralOverriddenMethod) }
val AppIcons.ideGeneralOverridingMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralOverridingMethod) }
val AppIcons.ideGeneralPaste: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPaste) }
val AppIcons.ideGeneralPin: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPin) }
val AppIcons.ideGeneralPinHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPinHovered) }
val AppIcons.ideGeneralPinSelected: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPinSelected) }
val AppIcons.ideGeneralPinSelectedHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPinSelectedHovered) }
val AppIcons.ideGeneralPluginUpdate: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPluginUpdate) }
val AppIcons.ideGeneralPreviewHorizontally: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPreviewHorizontally) }
val AppIcons.ideGeneralPreviewOnly: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPreviewOnly) }
val AppIcons.ideGeneralPreviewVertically: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPreviewVertically) }
val AppIcons.ideGeneralPrint: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralPrint) }
val AppIcons.ideGeneralProjectConfigurable: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralProjectConfigurable) }
val AppIcons.ideGeneralProjectStructure: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralProjectStructure) }
val AppIcons.ideGeneralProjectWideAnalysisOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralProjectWideAnalysisOff) }
val AppIcons.ideGeneralProjectWideAnalysisOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralProjectWideAnalysisOn) }
val AppIcons.ideGeneralQuestionDialog: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralQuestionDialog) }
val AppIcons.ideGeneralQuestionMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralQuestionMark) }
val AppIcons.ideGeneralReaderMode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralReaderMode) }
val AppIcons.ideGeneralRedo: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRedo) }
val AppIcons.ideGeneralRefreshAuto: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRefreshAuto) }
val AppIcons.ideGeneralRelated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRelated) }
val AppIcons.ideGeneralRemove: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRemove) }
val AppIcons.ideGeneralReset: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralReset) }
val AppIcons.ideGeneralRight: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRight) }
val AppIcons.ideGeneralRunAnything: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralRunAnything) }
val AppIcons.ideGeneralScrollDown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralScrollDown) }
val AppIcons.ideGeneralScrollUp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralScrollUp) }
val AppIcons.ideGeneralSelectIn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSelectIn) }
val AppIcons.ideGeneralSeparatorHorizontal: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSeparatorHorizontal) }
val AppIcons.ideGeneralShowAsTree: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralShowAsTree) }
val AppIcons.ideGeneralShowToImplement: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralShowToImplement) }
val AppIcons.ideGeneralSoftWrap: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSoftWrap) }
val AppIcons.ideGeneralSplitHorizontally: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSplitHorizontally) }
val AppIcons.ideGeneralSplitVertically: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSplitVertically) }
val AppIcons.ideGeneralSuccessDialog: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSuccessDialog) }
val AppIcons.ideGeneralSuccessLogin: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralSuccessLogin) }
val AppIcons.ideGeneralTree: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralTree) }
val AppIcons.ideGeneralTreeHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralTreeHovered) }
val AppIcons.ideGeneralTreeSelected: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralTreeSelected) }
val AppIcons.ideGeneralUndo: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralUndo) }
val AppIcons.ideGeneralUnlocked: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralUnlocked) }
val AppIcons.ideGeneralUp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralUp) }
val AppIcons.ideGeneralUpload: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralUpload) }
val AppIcons.ideGeneralVcs: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralVcs) }
val AppIcons.ideGeneralWarningDialog: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGeneralWarningDialog) }
