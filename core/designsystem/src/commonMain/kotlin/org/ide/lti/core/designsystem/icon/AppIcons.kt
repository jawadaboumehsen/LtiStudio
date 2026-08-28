/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterNone
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewQuilt
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.bottomPanel
import org.ide.lti.core.designsystem.generated.resources.leftPanel_1
import org.ide.lti.core.designsystem.generated.resources.rightPanel
import org.ide.lti.core.designsystem.theme.GlassTheme
import org.ide.lti.core.designsystem.theme.IconSize
import org.jetbrains.compose.resources.painterResource


object AppIcons {
    val OutlinedInfo = Icons.Outlined.Info
    val OutlinedLock = Icons.Outlined.Lock
    val OutlinedNotifications = Icons.Outlined.Notifications
    val ChevronRight: ImageVector = Icons.Filled.ChevronRight
    val QrCode: ImageVector = Icons.Filled.QrCode
    val Close: ImageVector = Icons.Filled.Close
    val AttachMoney: ImageVector = Icons.Filled.AttachMoney
    val OutlinedVisibilityOff: ImageVector = Icons.Outlined.VisibilityOff
    val OutlinedVisibility: ImageVector = Icons.Outlined.Visibility
    val VisibilityOff: ImageVector = Icons.Filled.VisibilityOff
    val Visibility: ImageVector = Icons.Filled.Visibility
    val Check: ImageVector = Icons.Default.Check
    val KeyboardArrowDown: ImageVector = Icons.Default.KeyboardArrowDown
    val Home = Icons.Outlined.Home
    val HomeBoarder = Icons.Rounded.Home
    val Payment = Icons.Rounded.SwapHoriz
    val Finance = Icons.Outlined.Wallet
    val FinanceBoarder = Icons.Rounded.Wallet
    val Profile = Icons.Outlined.AccountCircle
    val ProfileBoarder = Icons.Rounded.AccountCircle
    val MoreVert = Icons.Rounded.MoreVert
    val Search = Icons.Rounded.Search
    val Add = Icons.Rounded.Add
    val Back = Icons.AutoMirrored.Outlined.ArrowBack
    val Copy = Icons.Filled.ContentCopy
    val Share = Icons.Filled.Share
    val OutlinedShare = Icons.Outlined.Share
    val ArrowBack = Icons.AutoMirrored.Filled.ArrowBack
    val ArrowBack2 = Icons.Filled.ChevronLeft
    val Cancel = Icons.Outlined.Cancel
    val AccountCircle = Icons.Outlined.AccountCircle
    val SendRightTilted = Icons.Default.ArrowOutward
    val Info = Icons.Default.Info
    val Camera = Icons.Filled.Camera
    val PhotoLibrary = Icons.Filled.PhotoLibrary
    val Delete = Icons.Filled.Delete
    val OutlinedDelete = Icons.Outlined.DeleteOutline
    val RoundedInfo = Icons.Rounded.Info
    val Contact = Icons.Rounded.Contacts
    val Settings = Icons.Rounded.Settings
    val SettingsOutlined = Icons.Outlined.Settings
    val QR = Icons.Rounded.QrCode
    val Bank = Icons.Rounded.AccountBalance
    val Photo = Icons.Default.Photo
    val FlashOn = Icons.Default.FlashOn
    val FlashOff = Icons.Default.FlashOff
    val QrCode2 = Icons.Filled.QrCode2
    val Edit = Icons.Filled.Edit
    val Edit2 = Icons.Outlined.Edit
    val CalenderMonth = Icons.Filled.CalendarMonth
    val OutlinedDoneAll = Icons.Outlined.DoneAll
    val Person = Icons.Filled.Person
    val Badge = Icons.Filled.Badge
    val DataInfo = Icons.Filled.Description
    val Scan = Icons.Outlined.QrCodeScanner
    val RadioButtonUnchecked = Icons.Default.RadioButtonUnchecked
    val RadioButtonChecked = Icons.Filled.RadioButtonChecked

    val ContentCopy = Icons.Default.ContentCopy
    val Save = Icons.Default.Save
    val Send = Icons.Default.Send
    val Clear = Icons.Default.Clear
    val Refresh = Icons.Default.Refresh
    val KeyboardArrowRight = Icons.Default.KeyboardArrowRight
    val Sparkles = Icons.Rounded.AutoAwesome

    // File system icons
    val Folder = Icons.Default.Folder
    val FolderOpen = Icons.Default.FolderOpen
    val File = Icons.Default.InsertDriveFile
    val Link = Icons.Default.Link

    // Layout mode icons (for top app bar)
    val LayoutSingle = Icons.Default.ViewAgenda
    val LayoutSplit = Icons.Default.ViewColumn

    val rightPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.rightPanel),
            contentDescription = "Right Panel",
            tint = GlassTheme.colors.onGlass,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }

    val rightPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.rightPanel) }

    val leftPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.leftPanel_1),
            contentDescription = "Left Panel",
            tint = GlassTheme.colors.onGlass,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }

    val leftPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.leftPanel_1) }

    val bottomPanel: @Composable () -> Unit = {
        Icon(
            painter = painterResource(Res.drawable.bottomPanel),
            contentDescription = "Bottom Panel",
            tint = GlassTheme.colors.onGlass,
            modifier = Modifier.size(IconSize.Segmented),
        )
    }
    val bottomPanelPainterResource: @Composable () -> Painter = { painterResource(Res.drawable.bottomPanel) }

    val LayoutThreePanel = Icons.Default.ViewQuilt
    val LayoutFourPanel = Icons.Default.ViewStream

    // Window control icons
    val WindowMinimize = Icons.Default.Remove
    val WindowMaximize = Icons.Default.CropSquare
    val WindowRestore = Icons.Default.FilterNone

    // Top app bar icons
    val Menu = Icons.Default.Menu
    val MoreApps = Icons.Default.Apps
}
