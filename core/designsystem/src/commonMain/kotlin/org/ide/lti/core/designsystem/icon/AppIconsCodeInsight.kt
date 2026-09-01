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
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlayGlobe
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlayRenameInComments
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlayRenameInCommentsActive
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlayRenameInNoCodeFiles
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlayRenameInNoCodeFilesActive
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlaySecuredShield
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightInlaySettings
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightIntentionBulb
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightIntentionBulbGrey
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightQuickfixBulb
import org.ide.lti.core.designsystem.generated.resources.ideCodeInsightQuickfixOffBulb
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "codeInsight" icon category.
 * Auto-generated from the bulk-imported expui/codeInsight/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideCodeInsightInlayGlobe: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlayGlobe) }
val AppIcons.ideCodeInsightInlayRenameInComments: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlayRenameInComments) }
val AppIcons.ideCodeInsightInlayRenameInCommentsActive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlayRenameInCommentsActive) }
val AppIcons.ideCodeInsightInlayRenameInNoCodeFiles: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlayRenameInNoCodeFiles) }
val AppIcons.ideCodeInsightInlayRenameInNoCodeFilesActive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlayRenameInNoCodeFilesActive) }
val AppIcons.ideCodeInsightInlaySecuredShield: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlaySecuredShield) }
val AppIcons.ideCodeInsightInlaySettings: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightInlaySettings) }
val AppIcons.ideCodeInsightIntentionBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightIntentionBulb) }
val AppIcons.ideCodeInsightIntentionBulbGrey: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightIntentionBulbGrey) }
val AppIcons.ideCodeInsightQuickfixBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightQuickfixBulb) }
val AppIcons.ideCodeInsightQuickfixOffBulb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideCodeInsightQuickfixOffBulb) }
