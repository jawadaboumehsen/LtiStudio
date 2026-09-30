/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
@file:Suppress("MatchingDeclarationName")

package org.ide.lti.core.designsystem.component.tooltip

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.ide.lti.core.designsystem.theme.MotionDuration

/**
 * Alignment placement for [GlassTooltipArea].
 */
enum class GlassTooltipPlacement {
    Above,
    Below,
    End,
    Start,
}

/**
 * Wraps [content] with a hover-triggered tooltip showing [tooltipText] after [delayMillis].
 *
 * Pass `delayMillis = Int.MAX_VALUE` to effectively disable the tooltip (used by callers that
 * only want it to appear conditionally).
 */
@Composable
expect fun GlassTooltipArea(
    tooltipText: String,
    modifier: Modifier = Modifier,
    delayMillis: Int = MotionDuration.TooltipDelay,
    tooltipPlacement: GlassTooltipPlacement = GlassTooltipPlacement.Above,
    content: @Composable () -> Unit,
)
