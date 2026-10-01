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

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.bottomPanel
import org.ide.lti.core.designsystem.generated.resources.ideActionsSplit
import org.ide.lti.core.designsystem.generated.resources.ideAdd
import org.ide.lti.core.designsystem.generated.resources.ideCheck
import org.ide.lti.core.designsystem.generated.resources.ideChevronDown
import org.ide.lti.core.designsystem.generated.resources.ideChevronLeft
import org.ide.lti.core.designsystem.generated.resources.ideChevronRight
import org.ide.lti.core.designsystem.generated.resources.ideClose
import org.ide.lti.core.designsystem.generated.resources.ideCopy
import org.ide.lti.core.designsystem.generated.resources.ideDelete
import org.ide.lti.core.designsystem.generated.resources.ideEdit
import org.ide.lti.core.designsystem.generated.resources.ideFile
import org.ide.lti.core.designsystem.generated.resources.ideFolder
import org.ide.lti.core.designsystem.generated.resources.ideGeneralChevronUp
import org.ide.lti.core.designsystem.generated.resources.ideHide
import org.ide.lti.core.designsystem.generated.resources.ideInfo
import org.ide.lti.core.designsystem.generated.resources.ideLocked
import org.ide.lti.core.designsystem.generated.resources.ideMenu
import org.ide.lti.core.designsystem.generated.resources.ideMoreVertical
import org.ide.lti.core.designsystem.generated.resources.ideNodesHomeFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesSymlink
import org.ide.lti.core.designsystem.generated.resources.ideNotifications
import org.ide.lti.core.designsystem.generated.resources.ideRefresh
import org.ide.lti.core.designsystem.generated.resources.ideRunRun
import org.ide.lti.core.designsystem.generated.resources.ideSave
import org.ide.lti.core.designsystem.generated.resources.ideSearch
import org.ide.lti.core.designsystem.generated.resources.ideSettings
import org.ide.lti.core.designsystem.generated.resources.ideShow
import org.ide.lti.core.designsystem.generated.resources.ideToolwindowsToolWindowAskAI
import org.ide.lti.core.designsystem.generated.resources.ideUser
import org.ide.lti.core.designsystem.generated.resources.ideVcsArrowRight
import org.ide.lti.core.designsystem.generated.resources.leftPanel_1
import org.ide.lti.core.designsystem.generated.resources.rightPanel
import org.ide.lti.core.designsystem.theme.IconSize
import org.jetbrains.compose.resources.painterResource

/**
 * Curated, semantically-named icon accessors for icons actively used by LtiRom Studio, backed
 * exclusively by the JetBrains IntelliJ Platform "Expressive UI" SVG icon library and painter
 * resources.
 *
 * Additional category-specific IntelliJ icons are exposed as extension properties across
 * `AppIcons<Category>.kt` files in this package (e.g. `AppIcons.ideActionsExecute`, `AppIcons.ideNodesFolder`).
 */
object AppIcons {

    // ==================== PAINTER RESOURCES ====================

    val FolderPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideFolder) }
    val FilePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideFile) }
    val SearchPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideSearch) }
    val SettingsPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideSettings) }
    val RefreshPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideRefresh) }
    val SavePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideSave) }
    val ClearPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideClose) }
    val MenuPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideMenu) }
    val AddPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideAdd) }
    val DeletePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideDelete) }
    val EditPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideEdit) }
    val CopyPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideCopy) }
    val ClosePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideClose) }
    val MoreVertPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideMoreVertical) }
    val ChevronRightPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideChevronRight) }
    val ChevronLeftPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideChevronLeft) }
    val ChevronDownPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideChevronDown) }
    val ChevronUpPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideGeneralChevronUp) }
    val UserPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideUser) }
    val LockedPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideLocked) }
    val ShowPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideShow) }
    val HidePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideHide) }
    val InfoPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideInfo) }
    val SparklesPainterResource: @Composable () -> Painter = {
        painterResource(Res.drawable.ideToolwindowsToolWindowAskAI)
    }
    val CheckPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideCheck) }
    val NotificationsPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideNotifications) }
    val SendPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideVcsArrowRight) }
    val FolderOpenPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideFolder) }
    val LinkPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideNodesSymlink) }
    val HomePainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideNodesHomeFolder) }
    val ArrowBackPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideChevronLeft) }
    val LayoutSplitPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideActionsSplit) }
    val PlayPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.ideRunRun) }

    val leftPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.leftPanel_1) }
    val rightPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.rightPanel) }
    val bottomPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.bottomPanel) }

    // ==================== APP ICON WRAPPERS ====================

    val Folder: AppIcon = AppIcon.Painted(FolderPainterResource)
    val FolderOpen: AppIcon = AppIcon.Painted(FolderOpenPainterResource)
    val File: AppIcon = AppIcon.Painted(FilePainterResource)
    val Search: AppIcon = AppIcon.Painted(SearchPainterResource)
    val Settings: AppIcon = AppIcon.Painted(SettingsPainterResource)
    val SettingsOutlined: AppIcon = AppIcon.Painted(SettingsPainterResource)
    val Refresh: AppIcon = AppIcon.Painted(RefreshPainterResource)
    val Save: AppIcon = AppIcon.Painted(SavePainterResource)
    val Clear: AppIcon = AppIcon.Painted(ClearPainterResource)
    val Menu: AppIcon = AppIcon.Painted(MenuPainterResource)
    val Add: AppIcon = AppIcon.Painted(AddPainterResource)
    val Delete: AppIcon = AppIcon.Painted(DeletePainterResource)
    val Edit: AppIcon = AppIcon.Painted(EditPainterResource)
    val Copy: AppIcon = AppIcon.Painted(CopyPainterResource)
    val Close: AppIcon = AppIcon.Painted(ClosePainterResource)
    val MoreVert: AppIcon = AppIcon.Painted(MoreVertPainterResource)
    val ChevronRight: AppIcon = AppIcon.Painted(ChevronRightPainterResource)
    val ChevronLeft: AppIcon = AppIcon.Painted(ChevronLeftPainterResource)
    val ChevronDown: AppIcon = AppIcon.Painted(ChevronDownPainterResource)
    val ChevronUp: AppIcon = AppIcon.Painted(ChevronUpPainterResource)
    val ArrowBack: AppIcon = AppIcon.Painted(ArrowBackPainterResource)
    val Send: AppIcon = AppIcon.Painted(SendPainterResource)
    val Link: AppIcon = AppIcon.Painted(LinkPainterResource)
    val Home: AppIcon = AppIcon.Painted(HomePainterResource)
    val HomeBoarder: AppIcon = AppIcon.Painted(HomePainterResource)
    val Payment: AppIcon = AppIcon.Painted(SettingsPainterResource)
    val Finance: AppIcon = AppIcon.Painted(SettingsPainterResource)
    val LayoutSplit: AppIcon = AppIcon.Painted(LayoutSplitPainterResource)
    val LayoutSingle: AppIcon = AppIcon.Painted(FolderPainterResource)
    val User: AppIcon = AppIcon.Painted(UserPainterResource)
    val Profile: AppIcon = AppIcon.Painted(UserPainterResource)
    val ProfileBoarder: AppIcon = AppIcon.Painted(UserPainterResource)
    val Contact: AppIcon = AppIcon.Painted(UserPainterResource)
    val Person: AppIcon = AppIcon.Painted(UserPainterResource)
    val AccountCircle: AppIcon = AppIcon.Painted(UserPainterResource)
    val Locked: AppIcon = AppIcon.Painted(LockedPainterResource)
    val Show: AppIcon = AppIcon.Painted(ShowPainterResource)
    val Hide: AppIcon = AppIcon.Painted(HidePainterResource)
    val Info: AppIcon = AppIcon.Painted(InfoPainterResource)
    val Sparkles: AppIcon = AppIcon.Painted(SparklesPainterResource)
    val Check: AppIcon = AppIcon.Painted(CheckPainterResource)
    val Notifications: AppIcon = AppIcon.Painted(NotificationsPainterResource)
    val OutlinedNotifications: AppIcon = AppIcon.Painted(NotificationsPainterResource)
    val OutlinedInfo: AppIcon = AppIcon.Painted(InfoPainterResource)
    val OutlinedLock: AppIcon = AppIcon.Painted(LockedPainterResource)
    val OutlinedVisibility: AppIcon = AppIcon.Painted(ShowPainterResource)
    val OutlinedVisibilityOff: AppIcon = AppIcon.Painted(HidePainterResource)
    val LeftPanel: AppIcon = AppIcon.Painted(leftPanelPainterResource)
    val RightPanel: AppIcon = AppIcon.Painted(rightPanelPainterResource)
    val BottomPanel: AppIcon = AppIcon.Painted(bottomPanelPainterResource)
    val Play: AppIcon = AppIcon.Painted(PlayPainterResource)

    // ==================== EMBEDDED PANEL ICONS ====================

    val rightPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.rightPanel),
            contentDescription = "Right Panel",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }

    val leftPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.leftPanel_1),
            contentDescription = "Left Panel",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }

    val bottomPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.bottomPanel),
            contentDescription = "Bottom Panel",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }
}
