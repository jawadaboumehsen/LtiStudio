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
import org.ide.lti.core.designsystem.generated.resources.ideProfilerCollapseNode
import org.ide.lti.core.designsystem.generated.resources.ideProfilerExpandNode
import org.ide.lti.core.designsystem.generated.resources.ideProfilerRec
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "profiler" icon category.
 * Auto-generated from the bulk-imported expui/profiler/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideProfilerCollapseNode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProfilerCollapseNode) }
val AppIcons.ideProfilerExpandNode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProfilerExpandNode) }
val AppIcons.ideProfilerRec: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideProfilerRec) }
