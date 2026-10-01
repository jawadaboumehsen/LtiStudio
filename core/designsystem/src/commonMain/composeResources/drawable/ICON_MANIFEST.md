# JetBrains IntelliJ Platform Icon Manifest

This manifest documents all 807 `ide*.svg` icon assets available in `core/designsystem/src/commonMain/composeResources/drawable/`.

All icons are sourced from JetBrains' IntelliJ Platform ("Expressive UI" / expui set) under the Apache 2.0 license.

### Usage
Every icon is addressable through the `AppIcons` object - the curated ones (below) under their
semantic name, everything else under its raw category-derived name as an extension property
(declared in `AppIcons<Category>.kt` next to `AppIcons.kt`):
```kotlin
import org.ide.lti.core.designsystem.icon.AppIcons

Icon(painter = AppIcons.ideActionsAddFile(), contentDescription = null, tint = ...)
```

## Curated App Icons (AppIcons.kt) (26)

- ideAdd.svg -> AppIcons.AddPainterResource
- ideCheck.svg -> AppIcons.CheckPainterResource
- ideChevronDown.svg -> AppIcons.ChevronDownPainterResource
- ideChevronLeft.svg -> AppIcons.ChevronLeftPainterResource
- ideChevronRight.svg -> AppIcons.ChevronRightPainterResource
- ideClose.svg -> AppIcons.ClosePainterResource (also ClearPainterResource)
- ideCopy.svg -> AppIcons.CopyPainterResource
- ideDelete.svg -> AppIcons.DeletePainterResource
- ideEdit.svg -> AppIcons.EditPainterResource
- ideFile.svg -> AppIcons.FilePainterResource
- ideFolder.svg -> AppIcons.FolderPainterResource
- ideHide.svg -> AppIcons.HidePainterResource
- ideInfo.svg -> AppIcons.InfoPainterResource
- ideLocked.svg -> AppIcons.LockedPainterResource
- ideMenu.svg -> AppIcons.MenuPainterResource
- ideMoreVertical.svg -> AppIcons.MoreVertPainterResource
- ideNotifications.svg -> AppIcons.NotificationsPainterResource
- ideRefresh.svg -> AppIcons.RefreshPainterResource
- ideSave.svg -> AppIcons.SavePainterResource
- ideSearch.svg -> AppIcons.SearchPainterResource
- ideSettings.svg -> AppIcons.SettingsPainterResource
- ideShow.svg -> AppIcons.ShowPainterResource
- ideUser.svg -> AppIcons.UserPainterResource
- ideWindowMaximize.svg -> AppIcons.WindowMaximizePainterResource
- ideWindowMinimize.svg -> AppIcons.WindowMinimizePainterResource
- ideWindowRestore.svg -> AppIcons.WindowRestorePainterResource

## Actions (85)

- ideActionsAddDirectory.svg -> AppIcons.ideActionsAddDirectory
- ideActionsAddExcludedRoot.svg -> AppIcons.ideActionsAddExcludedRoot
- ideActionsAddFile.svg -> AppIcons.ideActionsAddFile
- ideActionsAddLink.svg -> AppIcons.ideActionsAddLink
- ideActionsAddToDictionary.svg -> AppIcons.ideActionsAddToDictionary
- ideActionsAiIntentionBulb.svg -> AppIcons.ideActionsAiIntentionBulb
- ideActionsAttach.svg -> AppIcons.ideActionsAttach
- ideActionsBuildAutoReloadChanges.svg -> AppIcons.ideActionsBuildAutoReloadChanges
- ideActionsBuildLoadChanges.svg -> AppIcons.ideActionsBuildLoadChanges
- ideActionsChangeView.svg -> AppIcons.ideActionsChangeView
- ideActionsClearCash.svg -> AppIcons.ideActionsClearCash
- ideActionsDeploy.svg -> AppIcons.ideActionsDeploy
- ideActionsDiagramDiff.svg -> AppIcons.ideActionsDiagramDiff
- ideActionsDiffWithClipboard.svg -> AppIcons.ideActionsDiffWithClipboard
- ideActionsFilterdups.svg -> AppIcons.ideActionsFilterdups
- ideActionsFindBackward.svg -> AppIcons.ideActionsFindBackward
- ideActionsFindEntireFile.svg -> AppIcons.ideActionsFindEntireFile
- ideActionsFindForward.svg -> AppIcons.ideActionsFindForward
- ideActionsForceRefresh.svg -> AppIcons.ideActionsForceRefresh
- ideActionsGenerated.svg -> AppIcons.ideActionsGenerated
- ideActionsGroupByClass.svg -> AppIcons.ideActionsGroupByClass
- ideActionsGroupByFile.svg -> AppIcons.ideActionsGroupByFile
- ideActionsGroupByMethod.svg -> AppIcons.ideActionsGroupByMethod
- ideActionsGroupByModule.svg -> AppIcons.ideActionsGroupByModule
- ideActionsGroupByModuleGroup.svg -> AppIcons.ideActionsGroupByModuleGroup
- ideActionsGroupByPackage.svg -> AppIcons.ideActionsGroupByPackage
- ideActionsGroupByTestProduction.svg -> AppIcons.ideActionsGroupByTestProduction
- ideActionsHighlighting.svg -> AppIcons.ideActionsHighlighting
- ideActionsInSelection.svg -> AppIcons.ideActionsInSelection
- ideActionsInstall.svg -> AppIcons.ideActionsInstall
- ideActionsLearn.svg -> AppIcons.ideActionsLearn
- ideActionsLightning.svg -> AppIcons.ideActionsLightning
- ideActionsMinimap.svg -> AppIcons.ideActionsMinimap
- ideActionsMoveToBottomLeft.svg -> AppIcons.ideActionsMoveToBottomLeft
- ideActionsMoveToBottomRight.svg -> AppIcons.ideActionsMoveToBottomRight
- ideActionsMoveToButton.svg -> AppIcons.ideActionsMoveToButton
- ideActionsMoveToLeftBottom.svg -> AppIcons.ideActionsMoveToLeftBottom
- ideActionsMoveToLeftTop.svg -> AppIcons.ideActionsMoveToLeftTop
- ideActionsMoveToRightBottom.svg -> AppIcons.ideActionsMoveToRightBottom
- ideActionsMoveToRightTop.svg -> AppIcons.ideActionsMoveToRightTop
- ideActionsMoveToWindow.svg -> AppIcons.ideActionsMoveToWindow
- ideActionsNewFolder.svg -> AppIcons.ideActionsNewFolder
- ideActionsNotificationsBottomLeft.svg -> AppIcons.ideActionsNotificationsBottomLeft
- ideActionsNotificationsBottomRight.svg -> AppIcons.ideActionsNotificationsBottomRight
- ideActionsNotificationsTopLeft.svg -> AppIcons.ideActionsNotificationsTopLeft
- ideActionsNotificationsTopRight.svg -> AppIcons.ideActionsNotificationsTopRight
- ideActionsPlayBack.svg -> AppIcons.ideActionsPlayBack
- ideActionsPlayFirst.svg -> AppIcons.ideActionsPlayFirst
- ideActionsPlayForward.svg -> AppIcons.ideActionsPlayForward
- ideActionsPlayLast.svg -> AppIcons.ideActionsPlayLast
- ideActionsPopFrame.svg -> AppIcons.ideActionsPopFrame
- ideActionsPreview.svg -> AppIcons.ideActionsPreview
- ideActionsProfileBlue.svg -> AppIcons.ideActionsProfileBlue
- ideActionsProfileCPU.svg -> AppIcons.ideActionsProfileCPU
- ideActionsProfileMemory.svg -> AppIcons.ideActionsProfileMemory
- ideActionsProfileRed.svg -> AppIcons.ideActionsProfileRed
- ideActionsProfileYellow.svg -> AppIcons.ideActionsProfileYellow
- ideActionsProjectDirectory.svg -> AppIcons.ideActionsProjectDirectory
- ideActionsProperties.svg -> AppIcons.ideActionsProperties
- ideActionsRealIntentionBulb.svg -> AppIcons.ideActionsRealIntentionBulb
- ideActionsRefactoringBulb.svg -> AppIcons.ideActionsRefactoringBulb
- ideActionsReformatCode.svg -> AppIcons.ideActionsReformatCode
- ideActionsReplace.svg -> AppIcons.ideActionsReplace
- ideActionsReport.svg -> AppIcons.ideActionsReport
- ideActionsRerunAutomatically.svg -> AppIcons.ideActionsRerunAutomatically
- ideActionsRestartStop.svg -> AppIcons.ideActionsRestartStop
- ideActionsRunAll.svg -> AppIcons.ideActionsRunAll
- ideActionsSelectAll.svg -> AppIcons.ideActionsSelectAll
- ideActionsShortcutFilter.svg -> AppIcons.ideActionsShortcutFilter
- ideActionsShowImportStatements.svg -> AppIcons.ideActionsShowImportStatements
- ideActionsShowReadAccess.svg -> AppIcons.ideActionsShowReadAccess
- ideActionsShowWriteAccess.svg -> AppIcons.ideActionsShowWriteAccess
- ideActionsSplit.svg -> AppIcons.ideActionsSplit
- ideActionsStartMemoryProfile.svg -> AppIcons.ideActionsStartMemoryProfile
- ideActionsStopRefresh.svg -> AppIcons.ideActionsStopRefresh
- ideActionsStopWatch.svg -> AppIcons.ideActionsStopWatch
- ideActionsSuggestedRefactoringBulb.svg -> AppIcons.ideActionsSuggestedRefactoringBulb
- ideActionsSwapPanels.svg -> AppIcons.ideActionsSwapPanels
- ideActionsSynchronizeScrolling.svg -> AppIcons.ideActionsSynchronizeScrolling
- ideActionsToggleVisibility.svg -> AppIcons.ideActionsToggleVisibility
- ideActionsUndeploy.svg -> AppIcons.ideActionsUndeploy
- ideActionsUninstall.svg -> AppIcons.ideActionsUninstall
- ideActionsUnselectAll.svg -> AppIcons.ideActionsUnselectAll
- ideActionsUnshare.svg -> AppIcons.ideActionsUnshare
- ideActionsViewAsImage.svg -> AppIcons.ideActionsViewAsImage

## Bookmarks (4)

- ideBookmarksAddBookmarksList.svg -> AppIcons.ideBookmarksAddBookmarksList
- ideBookmarksBookmark.svg -> AppIcons.ideBookmarksBookmark
- ideBookmarksBookmarksList.svg -> AppIcons.ideBookmarksBookmarksList
- ideBookmarksMnemonic.svg -> AppIcons.ideBookmarksMnemonic

## Breakpoints (41)

- ideBreakpointsBreakpoint.svg -> AppIcons.ideBreakpointsBreakpoint
- ideBreakpointsBreakpointDependent.svg -> AppIcons.ideBreakpointsBreakpointDependent
- ideBreakpointsBreakpointDisabled.svg -> AppIcons.ideBreakpointsBreakpointDisabled
- ideBreakpointsBreakpointException.svg -> AppIcons.ideBreakpointsBreakpointException
- ideBreakpointsBreakpointExceptionDisabled.svg -> AppIcons.ideBreakpointsBreakpointExceptionDisabled
- ideBreakpointsBreakpointField.svg -> AppIcons.ideBreakpointsBreakpointField
- ideBreakpointsBreakpointFieldDependent.svg -> AppIcons.ideBreakpointsBreakpointFieldDependent
- ideBreakpointsBreakpointFieldDisabled.svg -> AppIcons.ideBreakpointsBreakpointFieldDisabled
- ideBreakpointsBreakpointFieldMuted.svg -> AppIcons.ideBreakpointsBreakpointFieldMuted
- ideBreakpointsBreakpointFieldMutedDependent.svg -> AppIcons.ideBreakpointsBreakpointFieldMutedDependent
- ideBreakpointsBreakpointFieldMutedDisabled.svg -> AppIcons.ideBreakpointsBreakpointFieldMutedDisabled
- ideBreakpointsBreakpointFieldUnsuspendent.svg -> AppIcons.ideBreakpointsBreakpointFieldUnsuspendent
- ideBreakpointsBreakpointFieldUnsuspendentDisabled.svg -> AppIcons.ideBreakpointsBreakpointFieldUnsuspendentDisabled
- ideBreakpointsBreakpointFieldUnsuspendentValid.svg -> AppIcons.ideBreakpointsBreakpointFieldUnsuspendentValid
- ideBreakpointsBreakpointFieldValid.svg -> AppIcons.ideBreakpointsBreakpointFieldValid
- ideBreakpointsBreakpointInvalid.svg -> AppIcons.ideBreakpointsBreakpointInvalid
- ideBreakpointsBreakpointLambda.svg -> AppIcons.ideBreakpointsBreakpointLambda
- ideBreakpointsBreakpointMethod.svg -> AppIcons.ideBreakpointsBreakpointMethod
- ideBreakpointsBreakpointMethodDependent.svg -> AppIcons.ideBreakpointsBreakpointMethodDependent
- ideBreakpointsBreakpointMethodDisabled.svg -> AppIcons.ideBreakpointsBreakpointMethodDisabled
- ideBreakpointsBreakpointMethodMuted.svg -> AppIcons.ideBreakpointsBreakpointMethodMuted
- ideBreakpointsBreakpointMethodMutedDependent.svg -> AppIcons.ideBreakpointsBreakpointMethodMutedDependent
- ideBreakpointsBreakpointMethodMutedDisabled.svg -> AppIcons.ideBreakpointsBreakpointMethodMutedDisabled
- ideBreakpointsBreakpointMethodUnsuspendent.svg -> AppIcons.ideBreakpointsBreakpointMethodUnsuspendent
- ideBreakpointsBreakpointMethodUnsuspendentDisabled.svg -> AppIcons.ideBreakpointsBreakpointMethodUnsuspendentDisabled
- ideBreakpointsBreakpointMethodUnsuspendentValid.svg -> AppIcons.ideBreakpointsBreakpointMethodUnsuspendentValid
- ideBreakpointsBreakpointMethodValid.svg -> AppIcons.ideBreakpointsBreakpointMethodValid
- ideBreakpointsBreakpointMuted.svg -> AppIcons.ideBreakpointsBreakpointMuted
- ideBreakpointsBreakpointMutedDependent.svg -> AppIcons.ideBreakpointsBreakpointMutedDependent
- ideBreakpointsBreakpointMutedDisabled.svg -> AppIcons.ideBreakpointsBreakpointMutedDisabled
- ideBreakpointsBreakpointObsolete.svg -> AppIcons.ideBreakpointsBreakpointObsolete
- ideBreakpointsBreakpointUnsuspendent.svg -> AppIcons.ideBreakpointsBreakpointUnsuspendent
- ideBreakpointsBreakpointUnsuspendentDisabled.svg -> AppIcons.ideBreakpointsBreakpointUnsuspendentDisabled
- ideBreakpointsBreakpointUnsuspendentValid.svg -> AppIcons.ideBreakpointsBreakpointUnsuspendentValid
- ideBreakpointsBreakpointValid.svg -> AppIcons.ideBreakpointsBreakpointValid
- ideBreakpointsConditionalInstrumentation.svg -> AppIcons.ideBreakpointsConditionalInstrumentation
- ideBreakpointsLoggingInstrumentation.svg -> AppIcons.ideBreakpointsLoggingInstrumentation
- ideBreakpointsMultipleBreakpoints.svg -> AppIcons.ideBreakpointsMultipleBreakpoints
- ideBreakpointsMultipleBreakpointsDisabled.svg -> AppIcons.ideBreakpointsMultipleBreakpointsDisabled
- ideBreakpointsMultipleBreakpointsMuted.svg -> AppIcons.ideBreakpointsMultipleBreakpointsMuted
- ideBreakpointsQuestionBadge.svg -> AppIcons.ideBreakpointsQuestionBadge

## Build (7)

- ideBuildBuild.svg -> AppIcons.ideBuildBuild
- ideBuildCompletionCloud.svg -> AppIcons.ideBuildCompletionCloud
- ideBuildCompletionLocalCache.svg -> AppIcons.ideBuildCompletionLocalCache
- ideBuildDependencyAnalyzer.svg -> AppIcons.ideBuildDependencyAnalyzer
- ideBuildRebuild.svg -> AppIcons.ideBuildRebuild
- ideBuildTaskGroup.svg -> AppIcons.ideBuildTaskGroup
- ideBuildToggleOfflineMode.svg -> AppIcons.ideBuildToggleOfflineMode

## CodeInsight (11)

- ideCodeInsightInlayGlobe.svg -> AppIcons.ideCodeInsightInlayGlobe
- ideCodeInsightInlayRenameInComments.svg -> AppIcons.ideCodeInsightInlayRenameInComments
- ideCodeInsightInlayRenameInCommentsActive.svg -> AppIcons.ideCodeInsightInlayRenameInCommentsActive
- ideCodeInsightInlayRenameInNoCodeFiles.svg -> AppIcons.ideCodeInsightInlayRenameInNoCodeFiles
- ideCodeInsightInlayRenameInNoCodeFilesActive.svg -> AppIcons.ideCodeInsightInlayRenameInNoCodeFilesActive
- ideCodeInsightInlaySecuredShield.svg -> AppIcons.ideCodeInsightInlaySecuredShield
- ideCodeInsightInlaySettings.svg -> AppIcons.ideCodeInsightInlaySettings
- ideCodeInsightIntentionBulb.svg -> AppIcons.ideCodeInsightIntentionBulb
- ideCodeInsightIntentionBulbGrey.svg -> AppIcons.ideCodeInsightIntentionBulbGrey
- ideCodeInsightQuickfixBulb.svg -> AppIcons.ideCodeInsightQuickfixBulb
- ideCodeInsightQuickfixOffBulb.svg -> AppIcons.ideCodeInsightQuickfixOffBulb

## CodeWithMe (28)

- ideCodeWithMeCwmAccess.svg -> AppIcons.ideCodeWithMeCwmAccess
- ideCodeWithMeCwmCamAvatarOff.svg -> AppIcons.ideCodeWithMeCwmCamAvatarOff
- ideCodeWithMeCwmCamAvatarOn.svg -> AppIcons.ideCodeWithMeCwmCamAvatarOn
- ideCodeWithMeCwmCamOff.svg -> AppIcons.ideCodeWithMeCwmCamOff
- ideCodeWithMeCwmCamOn.svg -> AppIcons.ideCodeWithMeCwmCamOn
- ideCodeWithMeCwmDisableCall.svg -> AppIcons.ideCodeWithMeCwmDisableCall
- ideCodeWithMeCwmEnableCall.svg -> AppIcons.ideCodeWithMeCwmEnableCall
- ideCodeWithMeCwmIconModificator.svg -> AppIcons.ideCodeWithMeCwmIconModificator
- ideCodeWithMeCwmIconModificatorMenu.svg -> AppIcons.ideCodeWithMeCwmIconModificatorMenu
- ideCodeWithMeCwmInvite.svg -> AppIcons.ideCodeWithMeCwmInvite
- ideCodeWithMeCwmMicAvatarOff.svg -> AppIcons.ideCodeWithMeCwmMicAvatarOff
- ideCodeWithMeCwmMicAvatarOn.svg -> AppIcons.ideCodeWithMeCwmMicAvatarOn
- ideCodeWithMeCwmMicOff.svg -> AppIcons.ideCodeWithMeCwmMicOff
- ideCodeWithMeCwmMicOn.svg -> AppIcons.ideCodeWithMeCwmMicOn
- ideCodeWithMeCwmPermissionEdit.svg -> AppIcons.ideCodeWithMeCwmPermissionEdit
- ideCodeWithMeCwmPermissionFull.svg -> AppIcons.ideCodeWithMeCwmPermissionFull
- ideCodeWithMeCwmPermissionView.svg -> AppIcons.ideCodeWithMeCwmPermissionView
- ideCodeWithMeCwmPermissions.svg -> AppIcons.ideCodeWithMeCwmPermissions
- ideCodeWithMeCwmPermissionsDenied.svg -> AppIcons.ideCodeWithMeCwmPermissionsDenied
- ideCodeWithMeCwmPermissionsGranted.svg -> AppIcons.ideCodeWithMeCwmPermissionsGranted
- ideCodeWithMeCwmScreenInBrowserOff.svg -> AppIcons.ideCodeWithMeCwmScreenInBrowserOff
- ideCodeWithMeCwmScreenInBrowserOn.svg -> AppIcons.ideCodeWithMeCwmScreenInBrowserOn
- ideCodeWithMeCwmScreenOff.svg -> AppIcons.ideCodeWithMeCwmScreenOff
- ideCodeWithMeCwmScreenOn.svg -> AppIcons.ideCodeWithMeCwmScreenOn
- ideCodeWithMeCwmShare.svg -> AppIcons.ideCodeWithMeCwmShare
- ideCodeWithMeCwmSharingAvatarOn.svg -> AppIcons.ideCodeWithMeCwmSharingAvatarOn
- ideCodeWithMeCwmUsers.svg -> AppIcons.ideCodeWithMeCwmUsers
- ideCodeWithMeCwmVerified.svg -> AppIcons.ideCodeWithMeCwmVerified

## Debugger (35)

- ideDebuggerAddToWatch.svg -> AppIcons.ideDebuggerAddToWatch
- ideDebuggerClassLevelWatch.svg -> AppIcons.ideDebuggerClassLevelWatch
- ideDebuggerDbArray.svg -> AppIcons.ideDebuggerDbArray
- ideDebuggerDbObject.svg -> AppIcons.ideDebuggerDbObject
- ideDebuggerDbPrimitive.svg -> AppIcons.ideDebuggerDbPrimitive
- ideDebuggerDebuggerSync.svg -> AppIcons.ideDebuggerDebuggerSync
- ideDebuggerEvaluationResult.svg -> AppIcons.ideDebuggerEvaluationResult
- ideDebuggerExecuteCurrentStatement.svg -> AppIcons.ideDebuggerExecuteCurrentStatement
- ideDebuggerFrame.svg -> AppIcons.ideDebuggerFrame
- ideDebuggerFreeze.svg -> AppIcons.ideDebuggerFreeze
- ideDebuggerFreezeAll.svg -> AppIcons.ideDebuggerFreezeAll
- ideDebuggerInspectionSeverity.svg -> AppIcons.ideDebuggerInspectionSeverity
- ideDebuggerNextStatement.svg -> AppIcons.ideDebuggerNextStatement
- ideDebuggerOverhead.svg -> AppIcons.ideDebuggerOverhead
- ideDebuggerPinToTopPinnedItem.svg -> AppIcons.ideDebuggerPinToTopPinnedItem
- ideDebuggerPinToTopUnpinnedItem.svg -> AppIcons.ideDebuggerPinToTopUnpinnedItem
- ideDebuggerPromptInput.svg -> AppIcons.ideDebuggerPromptInput
- ideDebuggerPromptInputHistory.svg -> AppIcons.ideDebuggerPromptInputHistory
- ideDebuggerSelfReference.svg -> AppIcons.ideDebuggerSelfReference
- ideDebuggerSpecialVar.svg -> AppIcons.ideDebuggerSpecialVar
- ideDebuggerStepIntoMyCode.svg -> AppIcons.ideDebuggerStepIntoMyCode
- ideDebuggerThaw.svg -> AppIcons.ideDebuggerThaw
- ideDebuggerThawAll.svg -> AppIcons.ideDebuggerThawAll
- ideDebuggerThreadAtBreakpoint.svg -> AppIcons.ideDebuggerThreadAtBreakpoint
- ideDebuggerThreadCurrent.svg -> AppIcons.ideDebuggerThreadCurrent
- ideDebuggerThreadDaemon.svg -> AppIcons.ideDebuggerThreadDaemon
- ideDebuggerThreadFrozen.svg -> AppIcons.ideDebuggerThreadFrozen
- ideDebuggerThreadGroupCurrent.svg -> AppIcons.ideDebuggerThreadGroupCurrent
- ideDebuggerThreadRunning.svg -> AppIcons.ideDebuggerThreadRunning
- ideDebuggerThreadVirtual.svg -> AppIcons.ideDebuggerThreadVirtual
- ideDebuggerThreads.svg -> AppIcons.ideDebuggerThreads
- ideDebuggerValue.svg -> AppIcons.ideDebuggerValue
- ideDebuggerVariablesTab.svg -> AppIcons.ideDebuggerVariablesTab
- ideDebuggerWatch.svg -> AppIcons.ideDebuggerWatch
- ideDebuggerWatchLastReturnValue.svg -> AppIcons.ideDebuggerWatchLastReturnValue

## Diff (17)

- ideDiffApplyNotConflicts.svg -> AppIcons.ideDiffApplyNotConflicts
- ideDiffApplyNotConflictsLeft.svg -> AppIcons.ideDiffApplyNotConflictsLeft
- ideDiffApplyNotConflictsRight.svg -> AppIcons.ideDiffApplyNotConflictsRight
- ideDiffArrowLeftRight.svg -> AppIcons.ideDiffArrowLeftRight
- ideDiffCompare3LeftMiddle.svg -> AppIcons.ideDiffCompare3LeftMiddle
- ideDiffCompare3LeftRight.svg -> AppIcons.ideDiffCompare3LeftRight
- ideDiffCompare3MiddleRight.svg -> AppIcons.ideDiffCompare3MiddleRight
- ideDiffCompare4LeftBottom.svg -> AppIcons.ideDiffCompare4LeftBottom
- ideDiffCompare4LeftMiddle.svg -> AppIcons.ideDiffCompare4LeftMiddle
- ideDiffCompare4LeftRight.svg -> AppIcons.ideDiffCompare4LeftRight
- ideDiffCompare4MiddleBottom.svg -> AppIcons.ideDiffCompare4MiddleBottom
- ideDiffCompare4MiddleRight.svg -> AppIcons.ideDiffCompare4MiddleRight
- ideDiffCompare4RightBottom.svg -> AppIcons.ideDiffCompare4RightBottom
- ideDiffDisableEditing.svg -> AppIcons.ideDiffDisableEditing
- ideDiffMagicResolveToolbar.svg -> AppIcons.ideDiffMagicResolveToolbar
- ideDiffSideBySide.svg -> AppIcons.ideDiffSideBySide
- ideDiffUnified.svg -> AppIcons.ideDiffUnified

## Duplicates (4)

- ideDuplicatesSendToTheLeft.svg -> AppIcons.ideDuplicatesSendToTheLeft
- ideDuplicatesSendToTheLeftGrayed.svg -> AppIcons.ideDuplicatesSendToTheLeftGrayed
- ideDuplicatesSendToTheRight.svg -> AppIcons.ideDuplicatesSendToTheRight
- ideDuplicatesSendToTheRightGrayed.svg -> AppIcons.ideDuplicatesSendToTheRightGrayed

## FileTypes (70)

- ideFileTypesActionScript.svg -> AppIcons.ideFileTypesActionScript
- ideFileTypesAddAny.svg -> AppIcons.ideFileTypesAddAny
- ideFileTypesAnyType.svg -> AppIcons.ideFileTypesAnyType
- ideFileTypesArchive.svg -> AppIcons.ideFileTypesArchive
- ideFileTypesAspectJ.svg -> AppIcons.ideFileTypesAspectJ
- ideFileTypesBazel.svg -> AppIcons.ideFileTypesBazel
- ideFileTypesBinaryData.svg -> AppIcons.ideFileTypesBinaryData
- ideFileTypesC.svg -> AppIcons.ideFileTypesC
- ideFileTypesChangedFile.svg -> AppIcons.ideFileTypesChangedFile
- ideFileTypesChangedFiles.svg -> AppIcons.ideFileTypesChangedFiles
- ideFileTypesConfig.svg -> AppIcons.ideFileTypesConfig
- ideFileTypesContexts.svg -> AppIcons.ideFileTypesContexts
- ideFileTypesContextsModifier.svg -> AppIcons.ideFileTypesContextsModifier
- ideFileTypesCpp.svg -> AppIcons.ideFileTypesCpp
- ideFileTypesCsharp.svg -> AppIcons.ideFileTypesCsharp
- ideFileTypesCss.svg -> AppIcons.ideFileTypesCss
- ideFileTypesCsv.svg -> AppIcons.ideFileTypesCsv
- ideFileTypesDiagram.svg -> AppIcons.ideFileTypesDiagram
- ideFileTypesDocker.svg -> AppIcons.ideFileTypesDocker
- ideFileTypesEditorConfig.svg -> AppIcons.ideFileTypesEditorConfig
- ideFileTypesFont.svg -> AppIcons.ideFileTypesFont
- ideFileTypesGitignore.svg -> AppIcons.ideFileTypesGitignore
- ideFileTypesGradle.svg -> AppIcons.ideFileTypesGradle
- ideFileTypesGraphql.svg -> AppIcons.ideFileTypesGraphql
- ideFileTypesGroovy.svg -> AppIcons.ideFileTypesGroovy
- ideFileTypesH.svg -> AppIcons.ideFileTypesH
- ideFileTypesHprof.svg -> AppIcons.ideFileTypesHprof
- ideFileTypesHtml.svg -> AppIcons.ideFileTypesHtml
- ideFileTypesHttp.svg -> AppIcons.ideFileTypesHttp
- ideFileTypesI18n.svg -> AppIcons.ideFileTypesI18n
- ideFileTypesIdeaModule.svg -> AppIcons.ideFileTypesIdeaModule
- ideFileTypesIdl.svg -> AppIcons.ideFileTypesIdl
- ideFileTypesIgnored.svg -> AppIcons.ideFileTypesIgnored
- ideFileTypesImage.svg -> AppIcons.ideFileTypesImage
- ideFileTypesJava.svg -> AppIcons.ideFileTypesJava
- ideFileTypesJavaClass.svg -> AppIcons.ideFileTypesJavaClass
- ideFileTypesJavaScript.svg -> AppIcons.ideFileTypesJavaScript
- ideFileTypesJenkins.svg -> AppIcons.ideFileTypesJenkins
- ideFileTypesJfr.svg -> AppIcons.ideFileTypesJfr
- ideFileTypesJson.svg -> AppIcons.ideFileTypesJson
- ideFileTypesJsonSchema.svg -> AppIcons.ideFileTypesJsonSchema
- ideFileTypesJsp.svg -> AppIcons.ideFileTypesJsp
- ideFileTypesJspx.svg -> AppIcons.ideFileTypesJspx
- ideFileTypesJupyter.svg -> AppIcons.ideFileTypesJupyter
- ideFileTypesManifest.svg -> AppIcons.ideFileTypesManifest
- ideFileTypesMarkdown.svg -> AppIcons.ideFileTypesMarkdown
- ideFileTypesMicrosoftWindows.svg -> AppIcons.ideFileTypesMicrosoftWindows
- ideFileTypesModified.svg -> AppIcons.ideFileTypesModified
- ideFileTypesPatch.svg -> AppIcons.ideFileTypesPatch
- ideFileTypesPerl.svg -> AppIcons.ideFileTypesPerl
- ideFileTypesProperties.svg -> AppIcons.ideFileTypesProperties
- ideFileTypesRegexp.svg -> AppIcons.ideFileTypesRegexp
- ideFileTypesRst.svg -> AppIcons.ideFileTypesRst
- ideFileTypesScratch.svg -> AppIcons.ideFileTypesScratch
- ideFileTypesScratches.svg -> AppIcons.ideFileTypesScratches
- ideFileTypesShell.svg -> AppIcons.ideFileTypesShell
- ideFileTypesSourceMap.svg -> AppIcons.ideFileTypesSourceMap
- ideFileTypesSql.svg -> AppIcons.ideFileTypesSql
- ideFileTypesSwiftLang.svg -> AppIcons.ideFileTypesSwiftLang
- ideFileTypesTerraform.svg -> AppIcons.ideFileTypesTerraform
- ideFileTypesText.svg -> AppIcons.ideFileTypesText
- ideFileTypesToml.svg -> AppIcons.ideFileTypesToml
- ideFileTypesUiForm.svg -> AppIcons.ideFileTypesUiForm
- ideFileTypesUnknown.svg -> AppIcons.ideFileTypesUnknown
- ideFileTypesVue.svg -> AppIcons.ideFileTypesVue
- ideFileTypesWsdl.svg -> AppIcons.ideFileTypesWsdl
- ideFileTypesXhtml.svg -> AppIcons.ideFileTypesXhtml
- ideFileTypesXml.svg -> AppIcons.ideFileTypesXml
- ideFileTypesXsd.svg -> AppIcons.ideFileTypesXsd
- ideFileTypesYaml.svg -> AppIcons.ideFileTypesYaml

## General (107)

- ideGeneralAutoscrollFromSource.svg -> AppIcons.ideGeneralAutoscrollFromSource
- ideGeneralAutoscrollToSource.svg -> AppIcons.ideGeneralAutoscrollToSource
- ideGeneralBalloon.svg -> AppIcons.ideGeneralBalloon
- ideGeneralChevronDownLarge.svg -> AppIcons.ideGeneralChevronDownLarge
- ideGeneralChevronDownLargeWhite.svg -> AppIcons.ideGeneralChevronDownLargeWhite
- ideGeneralChevronUp.svg -> AppIcons.ideGeneralChevronUp
- ideGeneralChevronUpLarge.svg -> AppIcons.ideGeneralChevronUpLarge
- ideGeneralCloseSmall.svg -> AppIcons.ideGeneralCloseSmall
- ideGeneralCloseSmallHovered.svg -> AppIcons.ideGeneralCloseSmallHovered
- ideGeneralCollapseAll.svg -> AppIcons.ideGeneralCollapseAll
- ideGeneralCut.svg -> AppIcons.ideGeneralCut
- ideGeneralDebugDisabled.svg -> AppIcons.ideGeneralDebugDisabled
- ideGeneralDown.svg -> AppIcons.ideGeneralDown
- ideGeneralDownload.svg -> AppIcons.ideGeneralDownload
- ideGeneralDrag.svg -> AppIcons.ideGeneralDrag
- ideGeneralDropdown.svg -> AppIcons.ideGeneralDropdown
- ideGeneralDropdownGutter.svg -> AppIcons.ideGeneralDropdownGutter
- ideGeneralEditorOnly.svg -> AppIcons.ideGeneralEditorOnly
- ideGeneralEditorPreview.svg -> AppIcons.ideGeneralEditorPreview
- ideGeneralEditorPreviewVertical.svg -> AppIcons.ideGeneralEditorPreviewVertical
- ideGeneralErrorDialog.svg -> AppIcons.ideGeneralErrorDialog
- ideGeneralExit.svg -> AppIcons.ideGeneralExit
- ideGeneralExpandAll.svg -> AppIcons.ideGeneralExpandAll
- ideGeneralExport.svg -> AppIcons.ideGeneralExport
- ideGeneralExternalTools.svg -> AppIcons.ideGeneralExternalTools
- ideGeneralFilter.svg -> AppIcons.ideGeneralFilter
- ideGeneralGreenCheckmark.svg -> AppIcons.ideGeneralGreenCheckmark
- ideGeneralGroups.svg -> AppIcons.ideGeneralGroups
- ideGeneralHashtag.svg -> AppIcons.ideGeneralHashtag
- ideGeneralHelp.svg -> AppIcons.ideGeneralHelp
- ideGeneralHistory.svg -> AppIcons.ideGeneralHistory
- ideGeneralIdeUpdate.svg -> AppIcons.ideGeneralIdeUpdate
- ideGeneralImport.svg -> AppIcons.ideGeneralImport
- ideGeneralIndentDetected.svg -> AppIcons.ideGeneralIndentDetected
- ideGeneralInspectionsInspectionsError.svg -> AppIcons.ideGeneralInspectionsInspectionsError
- ideGeneralInspectionsInspectionsErrorEmpty.svg -> AppIcons.ideGeneralInspectionsInspectionsErrorEmpty
- ideGeneralInspectionsInspectionsEye.svg -> AppIcons.ideGeneralInspectionsInspectionsEye
- ideGeneralInspectionsInspectionsGrammar.svg -> AppIcons.ideGeneralInspectionsInspectionsGrammar
- ideGeneralInspectionsInspectionsMixed.svg -> AppIcons.ideGeneralInspectionsInspectionsMixed
- ideGeneralInspectionsInspectionsOK.svg -> AppIcons.ideGeneralInspectionsInspectionsOK
- ideGeneralInspectionsInspectionsOKEmpty.svg -> AppIcons.ideGeneralInspectionsInspectionsOKEmpty
- ideGeneralInspectionsInspectionsPause.svg -> AppIcons.ideGeneralInspectionsInspectionsPause
- ideGeneralInspectionsInspectionsPowerSaveMode.svg -> AppIcons.ideGeneralInspectionsInspectionsPowerSaveMode
- ideGeneralInspectionsInspectionsTrafficOff.svg -> AppIcons.ideGeneralInspectionsInspectionsTrafficOff
- ideGeneralInspectionsInspectionsTypos.svg -> AppIcons.ideGeneralInspectionsInspectionsTypos
- ideGeneralInspectionsInspectionsWarning.svg -> AppIcons.ideGeneralInspectionsInspectionsWarning
- ideGeneralInspectionsInspectionsWarningEmpty.svg -> AppIcons.ideGeneralInspectionsInspectionsWarningEmpty
- ideGeneralKeyboard.svg -> AppIcons.ideGeneralKeyboard
- ideGeneralLanguage.svg -> AppIcons.ideGeneralLanguage
- ideGeneralLayout.svg -> AppIcons.ideGeneralLayout
- ideGeneralLearn.svg -> AppIcons.ideGeneralLearn
- ideGeneralLeft.svg -> AppIcons.ideGeneralLeft
- ideGeneralListFiles.svg -> AppIcons.ideGeneralListFiles
- ideGeneralLocate.svg -> AppIcons.ideGeneralLocate
- ideGeneralMoreHorizontal.svg -> AppIcons.ideGeneralMoreHorizontal
- ideGeneralMoreVerticalStroke.svg -> AppIcons.ideGeneralMoreVerticalStroke
- ideGeneralMoveDown.svg -> AppIcons.ideGeneralMoveDown
- ideGeneralMoveUp.svg -> AppIcons.ideGeneralMoveUp
- ideGeneralOpen.svg -> AppIcons.ideGeneralOpen
- ideGeneralOpenInToolWindow.svg -> AppIcons.ideGeneralOpenInToolWindow
- ideGeneralOpenNewTab.svg -> AppIcons.ideGeneralOpenNewTab
- ideGeneralOverriddenMethod.svg -> AppIcons.ideGeneralOverriddenMethod
- ideGeneralOverridingMethod.svg -> AppIcons.ideGeneralOverridingMethod
- ideGeneralPaste.svg -> AppIcons.ideGeneralPaste
- ideGeneralPin.svg -> AppIcons.ideGeneralPin
- ideGeneralPinHovered.svg -> AppIcons.ideGeneralPinHovered
- ideGeneralPinSelected.svg -> AppIcons.ideGeneralPinSelected
- ideGeneralPinSelectedHovered.svg -> AppIcons.ideGeneralPinSelectedHovered
- ideGeneralPluginUpdate.svg -> AppIcons.ideGeneralPluginUpdate
- ideGeneralPreviewHorizontally.svg -> AppIcons.ideGeneralPreviewHorizontally
- ideGeneralPreviewOnly.svg -> AppIcons.ideGeneralPreviewOnly
- ideGeneralPreviewVertically.svg -> AppIcons.ideGeneralPreviewVertically
- ideGeneralPrint.svg -> AppIcons.ideGeneralPrint
- ideGeneralProjectConfigurable.svg -> AppIcons.ideGeneralProjectConfigurable
- ideGeneralProjectStructure.svg -> AppIcons.ideGeneralProjectStructure
- ideGeneralProjectWideAnalysisOff.svg -> AppIcons.ideGeneralProjectWideAnalysisOff
- ideGeneralProjectWideAnalysisOn.svg -> AppIcons.ideGeneralProjectWideAnalysisOn
- ideGeneralQuestionDialog.svg -> AppIcons.ideGeneralQuestionDialog
- ideGeneralQuestionMark.svg -> AppIcons.ideGeneralQuestionMark
- ideGeneralReaderMode.svg -> AppIcons.ideGeneralReaderMode
- ideGeneralRedo.svg -> AppIcons.ideGeneralRedo
- ideGeneralRefreshAuto.svg -> AppIcons.ideGeneralRefreshAuto
- ideGeneralRelated.svg -> AppIcons.ideGeneralRelated
- ideGeneralRemove.svg -> AppIcons.ideGeneralRemove
- ideGeneralReset.svg -> AppIcons.ideGeneralReset
- ideGeneralRight.svg -> AppIcons.ideGeneralRight
- ideGeneralRunAnything.svg -> AppIcons.ideGeneralRunAnything
- ideGeneralScrollDown.svg -> AppIcons.ideGeneralScrollDown
- ideGeneralScrollUp.svg -> AppIcons.ideGeneralScrollUp
- ideGeneralSelectIn.svg -> AppIcons.ideGeneralSelectIn
- ideGeneralSeparatorHorizontal.svg -> AppIcons.ideGeneralSeparatorHorizontal
- ideGeneralShowAsTree.svg -> AppIcons.ideGeneralShowAsTree
- ideGeneralShowToImplement.svg -> AppIcons.ideGeneralShowToImplement
- ideGeneralSoftWrap.svg -> AppIcons.ideGeneralSoftWrap
- ideGeneralSplitHorizontally.svg -> AppIcons.ideGeneralSplitHorizontally
- ideGeneralSplitVertically.svg -> AppIcons.ideGeneralSplitVertically
- ideGeneralSuccessDialog.svg -> AppIcons.ideGeneralSuccessDialog
- ideGeneralSuccessLogin.svg -> AppIcons.ideGeneralSuccessLogin
- ideGeneralTree.svg -> AppIcons.ideGeneralTree
- ideGeneralTreeHovered.svg -> AppIcons.ideGeneralTreeHovered
- ideGeneralTreeSelected.svg -> AppIcons.ideGeneralTreeSelected
- ideGeneralUndo.svg -> AppIcons.ideGeneralUndo
- ideGeneralUnlocked.svg -> AppIcons.ideGeneralUnlocked
- ideGeneralUp.svg -> AppIcons.ideGeneralUp
- ideGeneralUpload.svg -> AppIcons.ideGeneralUpload
- ideGeneralVcs.svg -> AppIcons.ideGeneralVcs
- ideGeneralWarningDialog.svg -> AppIcons.ideGeneralWarningDialog

## Graph (1)

- ideGraphGraphLayout.svg -> AppIcons.ideGraphGraphLayout

## Gutter (28)

- ideGutterBookmark.svg -> AppIcons.ideGutterBookmark
- ideGutterColors.svg -> AppIcons.ideGutterColors
- ideGutterDataSchema.svg -> AppIcons.ideGutterDataSchema
- ideGutterExtAnnotation.svg -> AppIcons.ideGutterExtAnnotation
- ideGutterFold.svg -> AppIcons.ideGutterFold
- ideGutterFoldBottom.svg -> AppIcons.ideGutterFoldBottom
- ideGutterImplementedMethod.svg -> AppIcons.ideGutterImplementedMethod
- ideGutterImplementingFunctionalInterface.svg -> AppIcons.ideGutterImplementingFunctionalInterface
- ideGutterImplementingMethod.svg -> AppIcons.ideGutterImplementingMethod
- ideGutterJavadocEdit.svg -> AppIcons.ideGutterJavadocEdit
- ideGutterJavadocRead.svg -> AppIcons.ideGutterJavadocRead
- ideGutterMnemonic.svg -> AppIcons.ideGutterMnemonic
- ideGutterOverridenMethod.svg -> AppIcons.ideGutterOverridenMethod
- ideGutterOverridingMethod.svg -> AppIcons.ideGutterOverridingMethod
- ideGutterReadAccess.svg -> AppIcons.ideGutterReadAccess
- ideGutterRecursiveMethod.svg -> AppIcons.ideGutterRecursiveMethod
- ideGutterRerun.svg -> AppIcons.ideGutterRerun
- ideGutterRun.svg -> AppIcons.ideGutterRun
- ideGutterRunError.svg -> AppIcons.ideGutterRunError
- ideGutterRunFailed.svg -> AppIcons.ideGutterRunFailed
- ideGutterRunInQueue.svg -> AppIcons.ideGutterRunInQueue
- ideGutterRunSuccess.svg -> AppIcons.ideGutterRunSuccess
- ideGutterSiblingInheritedMethod.svg -> AppIcons.ideGutterSiblingInheritedMethod
- ideGutterSuggestedRefactoring.svg -> AppIcons.ideGutterSuggestedRefactoring
- ideGutterSuggestedRefactoringDisabled.svg -> AppIcons.ideGutterSuggestedRefactoringDisabled
- ideGutterUnfold.svg -> AppIcons.ideGutterUnfold
- ideGutterWeb.svg -> AppIcons.ideGutterWeb
- ideGutterWriteAccess.svg -> AppIcons.ideGutterWriteAccess

## Hierarchy (3)

- ideHierarchyClassHierarchy.svg -> AppIcons.ideHierarchyClassHierarchy
- ideHierarchySubtypes.svg -> AppIcons.ideHierarchySubtypes
- ideHierarchySupertypes.svg -> AppIcons.ideHierarchySupertypes

## Ide (10)

- ideIdeConfigFile.svg -> AppIcons.ideIdeConfigFile
- ideIdeExternalLink.svg -> AppIcons.ideIdeExternalLink
- ideIdeExternalLinkWhite.svg -> AppIcons.ideIdeExternalLinkWhite
- ideIdeFeedbackRating.svg -> AppIcons.ideIdeFeedbackRating
- ideIdeFeedbackRatingFocused.svg -> AppIcons.ideIdeFeedbackRatingFocused
- ideIdeFeedbackRatingFocusedOn.svg -> AppIcons.ideIdeFeedbackRatingFocusedOn
- ideIdeFeedbackRatingOn.svg -> AppIcons.ideIdeFeedbackRatingOn
- ideIdeGift.svg -> AppIcons.ideIdeGift
- ideIdeLocalScope.svg -> AppIcons.ideIdeLocalScope
- ideIdeSharedScope.svg -> AppIcons.ideIdeSharedScope

## Image (7)

- ideImageActualZoom.svg -> AppIcons.ideImageActualZoom
- ideImageColorPicker.svg -> AppIcons.ideImageColorPicker
- ideImageColorPickerRollover.svg -> AppIcons.ideImageColorPickerRollover
- ideImageFitContent.svg -> AppIcons.ideImageFitContent
- ideImageGrid.svg -> AppIcons.ideImageGrid
- ideImageZoomIn.svg -> AppIcons.ideImageZoomIn
- ideImageZoomOut.svg -> AppIcons.ideImageZoomOut

## Inline (16)

- ideInlineBrowse.svg -> AppIcons.ideInlineBrowse
- ideInlineCollapse.svg -> AppIcons.ideInlineCollapse
- ideInlineCopy.svg -> AppIcons.ideInlineCopy
- ideInlineExactWords.svg -> AppIcons.ideInlineExactWords
- ideInlineExpand.svg -> AppIcons.ideInlineExpand
- ideInlineInlineAdd.svg -> AppIcons.ideInlineInlineAdd
- ideInlineInlineClose.svg -> AppIcons.ideInlineInlineClose
- ideInlineInlineEdit.svg -> AppIcons.ideInlineInlineEdit
- ideInlineInlineSettings.svg -> AppIcons.ideInlineInlineSettings
- ideInlineMatchCase.svg -> AppIcons.ideInlineMatchCase
- ideInlineNewLine.svg -> AppIcons.ideInlineNewLine
- ideInlinePreserveCase.svg -> AppIcons.ideInlinePreserveCase
- ideInlineRefresh.svg -> AppIcons.ideInlineRefresh
- ideInlineRegex.svg -> AppIcons.ideInlineRegex
- ideInlineSearchHistory.svg -> AppIcons.ideInlineSearchHistory
- ideInlineVariables.svg -> AppIcons.ideInlineVariables

## Javaee (6)

- ideJavaeeHome.svg -> AppIcons.ideJavaeeHome
- ideJavaeePersistenceEntity.svg -> AppIcons.ideJavaeePersistenceEntity
- ideJavaeeUpdateRunningApplication.svg -> AppIcons.ideJavaeeUpdateRunningApplication
- ideJavaeeWebModuleGroup.svg -> AppIcons.ideJavaeeWebModuleGroup
- ideJavaeeWebService.svg -> AppIcons.ideJavaeeWebService
- ideJavaeeWebServiceClient.svg -> AppIcons.ideJavaeeWebServiceClient

## Json (2)

- ideJsonArray.svg -> AppIcons.ideJsonArray
- ideJsonObject.svg -> AppIcons.ideJsonObject

## Language (2)

- ideLanguagePhp.svg -> AppIcons.ideLanguagePhp
- ideLanguageScala.svg -> AppIcons.ideLanguageScala

## MeetNewUi (2)

- ideMeetNewUiDarkTheme.svg -> AppIcons.ideMeetNewUiDarkTheme
- ideMeetNewUiLightTheme.svg -> AppIcons.ideMeetNewUiLightTheme

## Nodes (115)

- ideNodesAbstractException.svg -> AppIcons.ideNodesAbstractException
- ideNodesAccessLocal.svg -> AppIcons.ideNodesAccessLocal
- ideNodesAccessPrivate.svg -> AppIcons.ideNodesAccessPrivate
- ideNodesAccessProtected.svg -> AppIcons.ideNodesAccessProtected
- ideNodesAccessPublic.svg -> AppIcons.ideNodesAccessPublic
- ideNodesAlias.svg -> AppIcons.ideNodesAlias
- ideNodesAnnotation.svg -> AppIcons.ideNodesAnnotation
- ideNodesAnnotationFolder.svg -> AppIcons.ideNodesAnnotationFolder
- ideNodesArtifact.svg -> AppIcons.ideNodesArtifact
- ideNodesAttribute.svg -> AppIcons.ideNodesAttribute
- ideNodesClass.svg -> AppIcons.ideNodesClass
- ideNodesClassAbstract.svg -> AppIcons.ideNodesClassAbstract
- ideNodesClassAnonymous.svg -> AppIcons.ideNodesClassAnonymous
- ideNodesClassInitializer.svg -> AppIcons.ideNodesClassInitializer
- ideNodesCompiledClassesFolder.svg -> AppIcons.ideNodesCompiledClassesFolder
- ideNodesConstant.svg -> AppIcons.ideNodesConstant
- ideNodesConstructor.svg -> AppIcons.ideNodesConstructor
- ideNodesController.svg -> AppIcons.ideNodesController
- ideNodesCopyOfFolder.svg -> AppIcons.ideNodesCopyOfFolder
- ideNodesDataColumn.svg -> AppIcons.ideNodesDataColumn
- ideNodesDataSchema.svg -> AppIcons.ideNodesDataSchema
- ideNodesDataTables.svg -> AppIcons.ideNodesDataTables
- ideNodesDesktop.svg -> AppIcons.ideNodesDesktop
- ideNodesEditFolder.svg -> AppIcons.ideNodesEditFolder
- ideNodesEntryPoints.svg -> AppIcons.ideNodesEntryPoints
- ideNodesEnum.svg -> AppIcons.ideNodesEnum
- ideNodesErrorIntroduction.svg -> AppIcons.ideNodesErrorIntroduction
- ideNodesException.svg -> AppIcons.ideNodesException
- ideNodesExcludeRoot.svg -> AppIcons.ideNodesExcludeRoot
- ideNodesExcludedGenerated.svg -> AppIcons.ideNodesExcludedGenerated
- ideNodesExtractedFolder.svg -> AppIcons.ideNodesExtractedFolder
- ideNodesField.svg -> AppIcons.ideNodesField
- ideNodesFinalMark.svg -> AppIcons.ideNodesFinalMark
- ideNodesFolderGithub.svg -> AppIcons.ideNodesFolderGithub
- ideNodesFunction.svg -> AppIcons.ideNodesFunction
- ideNodesGenerated.svg -> AppIcons.ideNodesGenerated
- ideNodesGeneratedSource.svg -> AppIcons.ideNodesGeneratedSource
- ideNodesGeneratedTestRoot.svg -> AppIcons.ideNodesGeneratedTestRoot
- ideNodesGvariable.svg -> AppIcons.ideNodesGvariable
- ideNodesHomeFolder.svg -> AppIcons.ideNodesHomeFolder
- ideNodesIdeaProject.svg -> AppIcons.ideNodesIdeaProject
- ideNodesInclude.svg -> AppIcons.ideNodesInclude
- ideNodesInterface.svg -> AppIcons.ideNodesInterface
- ideNodesJarDirectory.svg -> AppIcons.ideNodesJarDirectory
- ideNodesJavaDocFolder.svg -> AppIcons.ideNodesJavaDocFolder
- ideNodesJdk.svg -> AppIcons.ideNodesJdk
- ideNodesJunitTestMark.svg -> AppIcons.ideNodesJunitTestMark
- ideNodesLambda.svg -> AppIcons.ideNodesLambda
- ideNodesLibrary.svg -> AppIcons.ideNodesLibrary
- ideNodesLibraryFolder.svg -> AppIcons.ideNodesLibraryFolder
- ideNodesLocked.svg -> AppIcons.ideNodesLocked
- ideNodesLogFolder.svg -> AppIcons.ideNodesLogFolder
- ideNodesMcpServer.svg -> AppIcons.ideNodesMcpServer
- ideNodesMcpServerWidget.svg -> AppIcons.ideNodesMcpServerWidget
- ideNodesMethod.svg -> AppIcons.ideNodesMethod
- ideNodesMethodAbstract.svg -> AppIcons.ideNodesMethodAbstract
- ideNodesMethodReference.svg -> AppIcons.ideNodesMethodReference
- ideNodesModelClass.svg -> AppIcons.ideNodesModelClass
- ideNodesModels.svg -> AppIcons.ideNodesModels
- ideNodesModule.svg -> AppIcons.ideNodesModule
- ideNodesModule8x8.svg -> AppIcons.ideNodesModule8x8
- ideNodesModuleGroup.svg -> AppIcons.ideNodesModuleGroup
- ideNodesModuleJava.svg -> AppIcons.ideNodesModuleJava
- ideNodesMultipleTypeDefinitions.svg -> AppIcons.ideNodesMultipleTypeDefinitions
- ideNodesNativeLibrariesFolder.svg -> AppIcons.ideNodesNativeLibrariesFolder
- ideNodesPackage.svg -> AppIcons.ideNodesPackage
- ideNodesParameter.svg -> AppIcons.ideNodesParameter
- ideNodesPlugin.svg -> AppIcons.ideNodesPlugin
- ideNodesPluginJB.svg -> AppIcons.ideNodesPluginJB
- ideNodesPluginLogo.svg -> AppIcons.ideNodesPluginLogo
- ideNodesPluginLogoDisabled.svg -> AppIcons.ideNodesPluginLogoDisabled
- ideNodesPpInvalid.svg -> AppIcons.ideNodesPpInvalid
- ideNodesPpWeb.svg -> AppIcons.ideNodesPpWeb
- ideNodesProcessMark.svg -> AppIcons.ideNodesProcessMark
- ideNodesProperty.svg -> AppIcons.ideNodesProperty
- ideNodesRecord.svg -> AppIcons.ideNodesRecord
- ideNodesRelated.svg -> AppIcons.ideNodesRelated
- ideNodesResourceBundle.svg -> AppIcons.ideNodesResourceBundle
- ideNodesResourcesRoot.svg -> AppIcons.ideNodesResourcesRoot
- ideNodesRunnableMark.svg -> AppIcons.ideNodesRunnableMark
- ideNodesSecurityRole.svg -> AppIcons.ideNodesSecurityRole
- ideNodesServices.svg -> AppIcons.ideNodesServices
- ideNodesServlet.svg -> AppIcons.ideNodesServlet
- ideNodesShared.svg -> AppIcons.ideNodesShared
- ideNodesSortBySeverity.svg -> AppIcons.ideNodesSortBySeverity
- ideNodesSourceRoot.svg -> AppIcons.ideNodesSourceRoot
- ideNodesSourceRootFileLayer.svg -> AppIcons.ideNodesSourceRootFileLayer
- ideNodesSsh.svg -> AppIcons.ideNodesSsh
- ideNodesStar.svg -> AppIcons.ideNodesStar
- ideNodesStarEmpty.svg -> AppIcons.ideNodesStarEmpty
- ideNodesStatic.svg -> AppIcons.ideNodesStatic
- ideNodesStaticMark.svg -> AppIcons.ideNodesStaticMark
- ideNodesSymlink.svg -> AppIcons.ideNodesSymlink
- ideNodesTabAlert.svg -> AppIcons.ideNodesTabAlert
- ideNodesTag.svg -> AppIcons.ideNodesTag
- ideNodesTemplate.svg -> AppIcons.ideNodesTemplate
- ideNodesTemplateRoot.svg -> AppIcons.ideNodesTemplateRoot
- ideNodesTest.svg -> AppIcons.ideNodesTest
- ideNodesTestGroup.svg -> AppIcons.ideNodesTestGroup
- ideNodesTestIgnored.svg -> AppIcons.ideNodesTestIgnored
- ideNodesTestResourcesRoot.svg -> AppIcons.ideNodesTestResourcesRoot
- ideNodesTestRoot.svg -> AppIcons.ideNodesTestRoot
- ideNodesTestSourceFolder.svg -> AppIcons.ideNodesTestSourceFolder
- ideNodesTextArea.svg -> AppIcons.ideNodesTextArea
- ideNodesType.svg -> AppIcons.ideNodesType
- ideNodesUnloadedModule.svg -> AppIcons.ideNodesUnloadedModule
- ideNodesUnloadedProject.svg -> AppIcons.ideNodesUnloadedProject
- ideNodesUnmarkWebRoot.svg -> AppIcons.ideNodesUnmarkWebRoot
- ideNodesUpFolder.svg -> AppIcons.ideNodesUpFolder
- ideNodesUpLevel.svg -> AppIcons.ideNodesUpLevel
- ideNodesVariable.svg -> AppIcons.ideNodesVariable
- ideNodesWarningIntroduction.svg -> AppIcons.ideNodesWarningIntroduction
- ideNodesWebFolder.svg -> AppIcons.ideNodesWebFolder
- ideNodesWord.svg -> AppIcons.ideNodesWord
- ideNodesWorkspace.svg -> AppIcons.ideNodesWorkspace

## ObjectBrowser (10)

- ideObjectBrowserAbbreviatePackageNames.svg -> AppIcons.ideObjectBrowserAbbreviatePackageNames
- ideObjectBrowserCompactEmptyPackages.svg -> AppIcons.ideObjectBrowserCompactEmptyPackages
- ideObjectBrowserFlattenModules.svg -> AppIcons.ideObjectBrowserFlattenModules
- ideObjectBrowserFlattenPackages.svg -> AppIcons.ideObjectBrowserFlattenPackages
- ideObjectBrowserShowLibraryContents.svg -> AppIcons.ideObjectBrowserShowLibraryContents
- ideObjectBrowserShowMembers.svg -> AppIcons.ideObjectBrowserShowMembers
- ideObjectBrowserSortAlphabetically.svg -> AppIcons.ideObjectBrowserSortAlphabetically
- ideObjectBrowserSortByType.svg -> AppIcons.ideObjectBrowserSortByType
- ideObjectBrowserSortByUsage.svg -> AppIcons.ideObjectBrowserSortByUsage
- ideObjectBrowserSortByVisibility.svg -> AppIcons.ideObjectBrowserSortByVisibility

## Profiler (3)

- ideProfilerCollapseNode.svg -> AppIcons.ideProfilerCollapseNode
- ideProfilerExpandNode.svg -> AppIcons.ideProfilerExpandNode
- ideProfilerRec.svg -> AppIcons.ideProfilerRec

## Progress (6)

- ideProgressPause.svg -> AppIcons.ideProgressPause
- ideProgressPauseHovered.svg -> AppIcons.ideProgressPauseHovered
- ideProgressResume.svg -> AppIcons.ideProgressResume
- ideProgressResumeHovered.svg -> AppIcons.ideProgressResumeHovered
- ideProgressStop.svg -> AppIcons.ideProgressStop
- ideProgressStopHovered.svg -> AppIcons.ideProgressStopHovered

## Remote (2)

- ideRemoteServersResumeScaled.svg -> AppIcons.ideRemoteServersResumeScaled
- ideRemoteServersSuspendScaled.svg -> AppIcons.ideRemoteServersSuspendScaled

## Run (61)

- ideRunAttachToProcess.svg -> AppIcons.ideRunAttachToProcess
- ideRunConfigurationsApplication.svg -> AppIcons.ideRunConfigurationsApplication
- ideRunConfigurationsApplicationRemote.svg -> AppIcons.ideRunConfigurationsApplicationRemote
- ideRunConfigurationsCompound.svg -> AppIcons.ideRunConfigurationsCompound
- ideRunConfigurationsCoverage.svg -> AppIcons.ideRunConfigurationsCoverage
- ideRunConfigurationsIgnoredTest.svg -> AppIcons.ideRunConfigurationsIgnoredTest
- ideRunConfigurationsInvalidConfigurationLayer.svg -> AppIcons.ideRunConfigurationsInvalidConfigurationLayer
- ideRunConfigurationsJunit.svg -> AppIcons.ideRunConfigurationsJunit
- ideRunConfigurationsJunitTestMark.svg -> AppIcons.ideRunConfigurationsJunitTestMark
- ideRunConfigurationsMultiLaunch.svg -> AppIcons.ideRunConfigurationsMultiLaunch
- ideRunConfigurationsRemoteDebug.svg -> AppIcons.ideRunConfigurationsRemoteDebug
- ideRunConfigurationsSortByDuration.svg -> AppIcons.ideRunConfigurationsSortByDuration
- ideRunConfigurationsWebApp.svg -> AppIcons.ideRunConfigurationsWebApp
- ideRunDebug.svg -> AppIcons.ideRunDebug
- ideRunDebugStroke.svg -> AppIcons.ideRunDebugStroke
- ideRunDumpThreads.svg -> AppIcons.ideRunDumpThreads
- ideRunEvaluateExpression.svg -> AppIcons.ideRunEvaluateExpression
- ideRunForceRunToCursor.svg -> AppIcons.ideRunForceRunToCursor
- ideRunForceStepInto.svg -> AppIcons.ideRunForceStepInto
- ideRunForceStepOver.svg -> AppIcons.ideRunForceStepOver
- ideRunKillProcess.svg -> AppIcons.ideRunKillProcess
- ideRunMuteBreakpoints.svg -> AppIcons.ideRunMuteBreakpoints
- ideRunPause.svg -> AppIcons.ideRunPause
- ideRunProfile.svg -> AppIcons.ideRunProfile
- ideRunRerun.svg -> AppIcons.ideRunRerun
- ideRunRerunStroke.svg -> AppIcons.ideRunRerunStroke
- ideRunRestart.svg -> AppIcons.ideRunRestart
- ideRunRestartDebug.svg -> AppIcons.ideRunRestartDebug
- ideRunRestartDebugStroke.svg -> AppIcons.ideRunRestartDebugStroke
- ideRunRestartFailedTests.svg -> AppIcons.ideRunRestartFailedTests
- ideRunRestartFrame.svg -> AppIcons.ideRunRestartFrame
- ideRunResume.svg -> AppIcons.ideRunResume
- ideRunResumeStroke.svg -> AppIcons.ideRunResumeStroke
- ideRunRun.svg -> AppIcons.ideRunRun
- ideRunRunStroke.svg -> AppIcons.ideRunRunStroke
- ideRunRunToCursor.svg -> AppIcons.ideRunRunToCursor
- ideRunRunWithCoverage.svg -> AppIcons.ideRunRunWithCoverage
- ideRunShowCurrentFrame.svg -> AppIcons.ideRunShowCurrentFrame
- ideRunShowIgnored.svg -> AppIcons.ideRunShowIgnored
- ideRunShowPassed.svg -> AppIcons.ideRunShowPassed
- ideRunSmartStepInto.svg -> AppIcons.ideRunSmartStepInto
- ideRunStepInto.svg -> AppIcons.ideRunStepInto
- ideRunStepOut.svg -> AppIcons.ideRunStepOut
- ideRunStepOutCodeBlock.svg -> AppIcons.ideRunStepOutCodeBlock
- ideRunStepOver.svg -> AppIcons.ideRunStepOver
- ideRunStop.svg -> AppIcons.ideRunStop
- ideRunStopStroke.svg -> AppIcons.ideRunStopStroke
- ideRunTestCustom.svg -> AppIcons.ideRunTestCustom
- ideRunTestError.svg -> AppIcons.ideRunTestError
- ideRunTestFailed.svg -> AppIcons.ideRunTestFailed
- ideRunTestIgnored.svg -> AppIcons.ideRunTestIgnored
- ideRunTestNotRunYet.svg -> AppIcons.ideRunTestNotRunYet
- ideRunTestPassed.svg -> AppIcons.ideRunTestPassed
- ideRunTestPassedIgnored.svg -> AppIcons.ideRunTestPassedIgnored
- ideRunTestPaused.svg -> AppIcons.ideRunTestPaused
- ideRunTestSkipped.svg -> AppIcons.ideRunTestSkipped
- ideRunTestTerminated.svg -> AppIcons.ideRunTestTerminated
- ideRunTestUnknown.svg -> AppIcons.ideRunTestUnknown
- ideRunViewBreakpoints.svg -> AppIcons.ideRunViewBreakpoints
- ideRunWidgetBuild.svg -> AppIcons.ideRunWidgetBuild
- ideRunWidgetRestart.svg -> AppIcons.ideRunWidgetRestart

## Status (8)

- ideStatusError.svg -> AppIcons.ideStatusError
- ideStatusErrorOutline.svg -> AppIcons.ideStatusErrorOutline
- ideStatusFailedInProgress.svg -> AppIcons.ideStatusFailedInProgress
- ideStatusInfo.svg -> AppIcons.ideStatusInfo
- ideStatusInfoOutline.svg -> AppIcons.ideStatusInfoOutline
- ideStatusSuccess.svg -> AppIcons.ideStatusSuccess
- ideStatusWarning.svg -> AppIcons.ideStatusWarning
- ideStatusWarningOutline.svg -> AppIcons.ideStatusWarningOutline

## Survey (5)

- ideSurveyDissatisfied.svg -> AppIcons.ideSurveyDissatisfied
- ideSurveyNeutral.svg -> AppIcons.ideSurveyNeutral
- ideSurveySatisfied.svg -> AppIcons.ideSurveySatisfied
- ideSurveyVeryDissatisfied.svg -> AppIcons.ideSurveyVeryDissatisfied
- ideSurveyVerySatisfied.svg -> AppIcons.ideSurveyVerySatisfied

## Table (1)

- ideTablePagination.svg -> AppIcons.ideTablePagination

## Toolbar (1)

- ideToolbarUnknown.svg -> AppIcons.ideToolbarUnknown

## Toolwindows (41)

- ideToolwindowsAnt.svg -> AppIcons.ideToolwindowsAnt
- ideToolwindowsBookmarks.svg -> AppIcons.ideToolwindowsBookmarks
- ideToolwindowsBuild.svg -> AppIcons.ideToolwindowsBuild
- ideToolwindowsChanges.svg -> AppIcons.ideToolwindowsChanges
- ideToolwindowsCommit.svg -> AppIcons.ideToolwindowsCommit
- ideToolwindowsCoverage.svg -> AppIcons.ideToolwindowsCoverage
- ideToolwindowsDataflow.svg -> AppIcons.ideToolwindowsDataflow
- ideToolwindowsDebug.svg -> AppIcons.ideToolwindowsDebug
- ideToolwindowsDependencies.svg -> AppIcons.ideToolwindowsDependencies
- ideToolwindowsDocumentation.svg -> AppIcons.ideToolwindowsDocumentation
- ideToolwindowsFind.svg -> AppIcons.ideToolwindowsFind
- ideToolwindowsHierarchy.svg -> AppIcons.ideToolwindowsHierarchy
- ideToolwindowsLearn.svg -> AppIcons.ideToolwindowsLearn
- ideToolwindowsMeetNewUi.svg -> AppIcons.ideToolwindowsMeetNewUi
- ideToolwindowsMessages.svg -> AppIcons.ideToolwindowsMessages
- ideToolwindowsPalette.svg -> AppIcons.ideToolwindowsPalette
- ideToolwindowsProblems.svg -> AppIcons.ideToolwindowsProblems
- ideToolwindowsProfiler.svg -> AppIcons.ideToolwindowsProfiler
- ideToolwindowsProfilerAndroid.svg -> AppIcons.ideToolwindowsProfilerAndroid
- ideToolwindowsProject.svg -> AppIcons.ideToolwindowsProject
- ideToolwindowsRepositories.svg -> AppIcons.ideToolwindowsRepositories
- ideToolwindowsRun.svg -> AppIcons.ideToolwindowsRun
- ideToolwindowsServices.svg -> AppIcons.ideToolwindowsServices
- ideToolwindowsSettingSync.svg -> AppIcons.ideToolwindowsSettingSync
- ideToolwindowsStructure.svg -> AppIcons.ideToolwindowsStructure
- ideToolwindowsTask.svg -> AppIcons.ideToolwindowsTask
- ideToolwindowsTodo.svg -> AppIcons.ideToolwindowsTodo
- ideToolwindowsToolWindowAskAI.svg -> AppIcons.ideToolwindowsToolWindowAskAI
- ideToolwindowsToolWindowComponents.svg -> AppIcons.ideToolwindowsToolWindowComponents
- ideToolwindowsToolWindowDataView.svg -> AppIcons.ideToolwindowsToolWindowDataView
- ideToolwindowsToolWindowDevMode.svg -> AppIcons.ideToolwindowsToolWindowDevMode
- ideToolwindowsToolWindowDuplicates.svg -> AppIcons.ideToolwindowsToolWindowDuplicates
- ideToolwindowsToolWindowInspection.svg -> AppIcons.ideToolwindowsToolWindowInspection
- ideToolwindowsToolWindowInternal.svg -> AppIcons.ideToolwindowsToolWindowInternal
- ideToolwindowsToolWindowJsonPath.svg -> AppIcons.ideToolwindowsToolWindowJsonPath
- ideToolwindowsToolWindowOverflow.svg -> AppIcons.ideToolwindowsToolWindowOverflow
- ideToolwindowsToolWindowRunWithCoverage.svg -> AppIcons.ideToolwindowsToolWindowRunWithCoverage
- ideToolwindowsToolWindowVariableView.svg -> AppIcons.ideToolwindowsToolWindowVariableView
- ideToolwindowsVcs.svg -> AppIcons.ideToolwindowsVcs
- ideToolwindowsWeb.svg -> AppIcons.ideToolwindowsWeb
- ideToolwindowsWebServer.svg -> AppIcons.ideToolwindowsWebServer

## Vcs (19)

- ideVcsAbort.svg -> AppIcons.ideVcsAbort
- ideVcsAbortStroke.svg -> AppIcons.ideVcsAbortStroke
- ideVcsArrowLeft.svg -> AppIcons.ideVcsArrowLeft
- ideVcsArrowRight.svg -> AppIcons.ideVcsArrowRight
- ideVcsChangelist.svg -> AppIcons.ideVcsChangelist
- ideVcsChanges.svg -> AppIcons.ideVcsChanges
- ideVcsCommit.svg -> AppIcons.ideVcsCommit
- ideVcsDiff.svg -> AppIcons.ideVcsDiff
- ideVcsFetch.svg -> AppIcons.ideVcsFetch
- ideVcsMerge.svg -> AppIcons.ideVcsMerge
- ideVcsPatch.svg -> AppIcons.ideVcsPatch
- ideVcsPatchApplied.svg -> AppIcons.ideVcsPatchApplied
- ideVcsPush.svg -> AppIcons.ideVcsPush
- ideVcsRemove.svg -> AppIcons.ideVcsRemove
- ideVcsRevert.svg -> AppIcons.ideVcsRevert
- ideVcsShelve.svg -> AppIcons.ideVcsShelve
- ideVcsShowUnversionedFiles.svg -> AppIcons.ideVcsShowUnversionedFiles
- ideVcsUnshelve.svg -> AppIcons.ideVcsUnshelve
- ideVcsUpdate.svg -> AppIcons.ideVcsUpdate

## WebReferences (4)

- ideWebReferencesMessageQueue.svg -> AppIcons.ideWebReferencesMessageQueue
- ideWebReferencesOpenApi.svg -> AppIcons.ideWebReferencesOpenApi
- ideWebReferencesServer.svg -> AppIcons.ideWebReferencesServer
- ideWebReferencesWebSocket.svg -> AppIcons.ideWebReferencesWebSocket

## Welcome (3)

- ideWelcomeCreateNewProjectTab.svg -> AppIcons.ideWelcomeCreateNewProjectTab
- ideWelcomeFromVCSTab.svg -> AppIcons.ideWelcomeFromVCSTab
- ideWelcomeOpen.svg -> AppIcons.ideWelcomeOpen

## Windows (14)

- ideWindowsClose.svg -> AppIcons.ideWindowsClose
- ideWindowsCloseActive.svg -> AppIcons.ideWindowsCloseActive
- ideWindowsCloseInactive.svg -> AppIcons.ideWindowsCloseInactive
- ideWindowsCloseSmall.svg -> AppIcons.ideWindowsCloseSmall
- ideWindowsCollapse.svg -> AppIcons.ideWindowsCollapse
- ideWindowsHelp.svg -> AppIcons.ideWindowsHelp
- ideWindowsHelpInactive.svg -> AppIcons.ideWindowsHelpInactive
- ideWindowsMaximizeInactive.svg -> AppIcons.ideWindowsMaximizeInactive
- ideWindowsMaximizeSmall.svg -> AppIcons.ideWindowsMaximizeSmall
- ideWindowsMinimizeInactive.svg -> AppIcons.ideWindowsMinimizeInactive
- ideWindowsMinimizeSmall.svg -> AppIcons.ideWindowsMinimizeSmall
- ideWindowsMouseCursorText.svg -> AppIcons.ideWindowsMouseCursorText
- ideWindowsRestoreInactive.svg -> AppIcons.ideWindowsRestoreInactive
- ideWindowsRestoreSmall.svg -> AppIcons.ideWindowsRestoreSmall

## Xml (2)

- ideXmlCssClass.svg -> AppIcons.ideXmlCssClass
- ideXmlId.svg -> AppIcons.ideXmlId
