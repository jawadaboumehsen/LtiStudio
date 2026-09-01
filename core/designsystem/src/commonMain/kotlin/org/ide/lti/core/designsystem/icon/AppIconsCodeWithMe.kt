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
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmAccess
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmCamAvatarOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmCamAvatarOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmCamOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmCamOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmDisableCall
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmEnableCall
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmIconModificator
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmIconModificatorMenu
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmInvite
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmMicAvatarOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmMicAvatarOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmMicOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmMicOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissionEdit
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissionFull
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissionView
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissions
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissionsDenied
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmPermissionsGranted
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmScreenInBrowserOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmScreenInBrowserOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmScreenOff
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmScreenOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmShare
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmSharingAvatarOn
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmUsers
import org.ide.lti.core.designsystem.generated.resources.ideCodeWithMeCwmVerified
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "codeWithMe" icon category.
 * Auto-generated from the bulk-imported expui/codeWithMe/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideCodeWithMeCwmAccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmAccess) }
val AppIcons.ideCodeWithMeCwmCamAvatarOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmCamAvatarOff) }
val AppIcons.ideCodeWithMeCwmCamAvatarOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmCamAvatarOn) }
val AppIcons.ideCodeWithMeCwmCamOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmCamOff) }
val AppIcons.ideCodeWithMeCwmCamOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmCamOn) }
val AppIcons.ideCodeWithMeCwmDisableCall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmDisableCall) }
val AppIcons.ideCodeWithMeCwmEnableCall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmEnableCall) }
val AppIcons.ideCodeWithMeCwmIconModificator: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmIconModificator) }
val AppIcons.ideCodeWithMeCwmIconModificatorMenu: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmIconModificatorMenu) }
val AppIcons.ideCodeWithMeCwmInvite: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmInvite) }
val AppIcons.ideCodeWithMeCwmMicAvatarOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmMicAvatarOff) }
val AppIcons.ideCodeWithMeCwmMicAvatarOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmMicAvatarOn) }
val AppIcons.ideCodeWithMeCwmMicOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmMicOff) }
val AppIcons.ideCodeWithMeCwmMicOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmMicOn) }
val AppIcons.ideCodeWithMeCwmPermissionEdit: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissionEdit) }
val AppIcons.ideCodeWithMeCwmPermissionFull: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissionFull) }
val AppIcons.ideCodeWithMeCwmPermissionView: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissionView) }
val AppIcons.ideCodeWithMeCwmPermissions: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissions) }
val AppIcons.ideCodeWithMeCwmPermissionsDenied: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissionsDenied) }
val AppIcons.ideCodeWithMeCwmPermissionsGranted: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmPermissionsGranted) }
val AppIcons.ideCodeWithMeCwmScreenInBrowserOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmScreenInBrowserOff) }
val AppIcons.ideCodeWithMeCwmScreenInBrowserOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmScreenInBrowserOn) }
val AppIcons.ideCodeWithMeCwmScreenOff: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmScreenOff) }
val AppIcons.ideCodeWithMeCwmScreenOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmScreenOn) }
val AppIcons.ideCodeWithMeCwmShare: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmShare) }
val AppIcons.ideCodeWithMeCwmSharingAvatarOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmSharingAvatarOn) }
val AppIcons.ideCodeWithMeCwmUsers: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmUsers) }
val AppIcons.ideCodeWithMeCwmVerified: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeWithMeCwmVerified) }
