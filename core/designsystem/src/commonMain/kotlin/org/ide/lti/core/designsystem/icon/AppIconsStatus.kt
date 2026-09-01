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
import org.ide.lti.core.designsystem.generated.resources.ideStatusError
import org.ide.lti.core.designsystem.generated.resources.ideStatusErrorOutline
import org.ide.lti.core.designsystem.generated.resources.ideStatusFailedInProgress
import org.ide.lti.core.designsystem.generated.resources.ideStatusInfo
import org.ide.lti.core.designsystem.generated.resources.ideStatusInfoOutline
import org.ide.lti.core.designsystem.generated.resources.ideStatusSuccess
import org.ide.lti.core.designsystem.generated.resources.ideStatusWarning
import org.ide.lti.core.designsystem.generated.resources.ideStatusWarningOutline
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "status" icon category.
 * Auto-generated from the bulk-imported expui/status/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideStatusError: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusError) }
val AppIcons.ideStatusErrorOutline: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusErrorOutline) }
val AppIcons.ideStatusFailedInProgress: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusFailedInProgress) }
val AppIcons.ideStatusInfo: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusInfo) }
val AppIcons.ideStatusInfoOutline: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusInfoOutline) }
val AppIcons.ideStatusSuccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusSuccess) }
val AppIcons.ideStatusWarning: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusWarning) }
val AppIcons.ideStatusWarningOutline: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideStatusWarningOutline) }
