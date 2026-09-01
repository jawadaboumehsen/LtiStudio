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
import org.ide.lti.core.designsystem.generated.resources.ideGraphGraphLayout
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "graph" icon category.
 * Auto-generated from the bulk-imported expui/graph/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideGraphGraphLayout: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGraphGraphLayout) }
