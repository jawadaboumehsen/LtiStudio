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
import org.ide.lti.core.designsystem.generated.resources.ideWindowsClose
import org.ide.lti.core.designsystem.generated.resources.ideWindowsCloseActive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsCloseInactive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsCloseSmall
import org.ide.lti.core.designsystem.generated.resources.ideWindowsCollapse
import org.ide.lti.core.designsystem.generated.resources.ideWindowsHelp
import org.ide.lti.core.designsystem.generated.resources.ideWindowsHelpInactive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsMaximizeInactive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsMaximizeSmall
import org.ide.lti.core.designsystem.generated.resources.ideWindowsMinimizeInactive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsMinimizeSmall
import org.ide.lti.core.designsystem.generated.resources.ideWindowsMouseCursorText
import org.ide.lti.core.designsystem.generated.resources.ideWindowsRestoreInactive
import org.ide.lti.core.designsystem.generated.resources.ideWindowsRestoreSmall
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "windows" icon category.
 * Auto-generated from the bulk-imported expui/windows/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideWindowsClose: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsClose) }
val AppIcons.ideWindowsCloseActive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsCloseActive) }
val AppIcons.ideWindowsCloseInactive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsCloseInactive) }
val AppIcons.ideWindowsCloseSmall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsCloseSmall) }
val AppIcons.ideWindowsCollapse: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsCollapse) }
val AppIcons.ideWindowsHelp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsHelp) }
val AppIcons.ideWindowsHelpInactive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsHelpInactive) }
val AppIcons.ideWindowsMaximizeInactive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsMaximizeInactive) }
val AppIcons.ideWindowsMaximizeSmall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsMaximizeSmall) }
val AppIcons.ideWindowsMinimizeInactive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsMinimizeInactive) }
val AppIcons.ideWindowsMinimizeSmall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsMinimizeSmall) }
val AppIcons.ideWindowsMouseCursorText: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsMouseCursorText) }
val AppIcons.ideWindowsRestoreInactive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsRestoreInactive) }
val AppIcons.ideWindowsRestoreSmall: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWindowsRestoreSmall) }
