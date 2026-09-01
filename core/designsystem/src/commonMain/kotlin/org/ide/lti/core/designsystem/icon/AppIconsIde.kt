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
import org.ide.lti.core.designsystem.generated.resources.ideIdeConfigFile
import org.ide.lti.core.designsystem.generated.resources.ideIdeExternalLink
import org.ide.lti.core.designsystem.generated.resources.ideIdeExternalLinkWhite
import org.ide.lti.core.designsystem.generated.resources.ideIdeFeedbackRating
import org.ide.lti.core.designsystem.generated.resources.ideIdeFeedbackRatingFocused
import org.ide.lti.core.designsystem.generated.resources.ideIdeFeedbackRatingFocusedOn
import org.ide.lti.core.designsystem.generated.resources.ideIdeFeedbackRatingOn
import org.ide.lti.core.designsystem.generated.resources.ideIdeGift
import org.ide.lti.core.designsystem.generated.resources.ideIdeLocalScope
import org.ide.lti.core.designsystem.generated.resources.ideIdeSharedScope
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "ide" icon category.
 * Auto-generated from the bulk-imported expui/ide/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideIdeConfigFile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeConfigFile) }
val AppIcons.ideIdeExternalLink: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeExternalLink) }
val AppIcons.ideIdeExternalLinkWhite: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeExternalLinkWhite) }
val AppIcons.ideIdeFeedbackRating: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeFeedbackRating) }
val AppIcons.ideIdeFeedbackRatingFocused: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeFeedbackRatingFocused) }
val AppIcons.ideIdeFeedbackRatingFocusedOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeFeedbackRatingFocusedOn) }
val AppIcons.ideIdeFeedbackRatingOn: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeFeedbackRatingOn) }
val AppIcons.ideIdeGift: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeGift) }
val AppIcons.ideIdeLocalScope: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeLocalScope) }
val AppIcons.ideIdeSharedScope: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideIdeSharedScope) }
