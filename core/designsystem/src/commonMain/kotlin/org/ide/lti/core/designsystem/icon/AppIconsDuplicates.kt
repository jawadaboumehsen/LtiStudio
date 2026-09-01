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
import org.ide.lti.core.designsystem.generated.resources.ideDuplicatesSendToTheLeft
import org.ide.lti.core.designsystem.generated.resources.ideDuplicatesSendToTheLeftGrayed
import org.ide.lti.core.designsystem.generated.resources.ideDuplicatesSendToTheRight
import org.ide.lti.core.designsystem.generated.resources.ideDuplicatesSendToTheRightGrayed
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "duplicates" icon category.
 * Auto-generated from the bulk-imported expui/duplicates/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideDuplicatesSendToTheLeft: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDuplicatesSendToTheLeft) }
val AppIcons.ideDuplicatesSendToTheLeftGrayed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDuplicatesSendToTheLeftGrayed) }
val AppIcons.ideDuplicatesSendToTheRight: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDuplicatesSendToTheRight) }
val AppIcons.ideDuplicatesSendToTheRightGrayed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDuplicatesSendToTheRightGrayed) }
