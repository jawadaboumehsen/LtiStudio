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
import org.ide.lti.core.designsystem.generated.resources.ideImageActualZoom
import org.ide.lti.core.designsystem.generated.resources.ideImageColorPicker
import org.ide.lti.core.designsystem.generated.resources.ideImageColorPickerRollover
import org.ide.lti.core.designsystem.generated.resources.ideImageFitContent
import org.ide.lti.core.designsystem.generated.resources.ideImageGrid
import org.ide.lti.core.designsystem.generated.resources.ideImageZoomIn
import org.ide.lti.core.designsystem.generated.resources.ideImageZoomOut
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "image" icon category.
 * Auto-generated from the bulk-imported expui/image/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideImageActualZoom: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageActualZoom) }
val AppIcons.ideImageColorPicker: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageColorPicker) }
val AppIcons.ideImageColorPickerRollover: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageColorPickerRollover) }
val AppIcons.ideImageFitContent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageFitContent) }
val AppIcons.ideImageGrid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageGrid) }
val AppIcons.ideImageZoomIn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageZoomIn) }
val AppIcons.ideImageZoomOut: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideImageZoomOut) }
