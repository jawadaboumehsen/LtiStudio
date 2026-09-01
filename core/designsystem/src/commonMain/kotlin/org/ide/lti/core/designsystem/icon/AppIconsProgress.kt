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
import org.ide.lti.core.designsystem.generated.resources.ideProgressPause
import org.ide.lti.core.designsystem.generated.resources.ideProgressPauseHovered
import org.ide.lti.core.designsystem.generated.resources.ideProgressResume
import org.ide.lti.core.designsystem.generated.resources.ideProgressResumeHovered
import org.ide.lti.core.designsystem.generated.resources.ideProgressStop
import org.ide.lti.core.designsystem.generated.resources.ideProgressStopHovered
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "progress" icon category.
 * Auto-generated from the bulk-imported expui/progress/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideProgressPause: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressPause) }
val AppIcons.ideProgressPauseHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressPauseHovered) }
val AppIcons.ideProgressResume: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressResume) }
val AppIcons.ideProgressResumeHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressResumeHovered) }
val AppIcons.ideProgressStop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressStop) }
val AppIcons.ideProgressStopHovered: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProgressStopHovered) }
