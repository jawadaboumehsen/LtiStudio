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
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpoint
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointException
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointExceptionDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointField
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldMuted
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldMutedDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldMutedDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldUnsuspendent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldUnsuspendentDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldUnsuspendentValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointFieldValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointInvalid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointLambda
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethod
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodMuted
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodMutedDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodMutedDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodUnsuspendent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodUnsuspendentDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodUnsuspendentValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMethodValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMuted
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMutedDependent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointMutedDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointObsolete
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointUnsuspendent
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointUnsuspendentDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointUnsuspendentValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsBreakpointValid
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsConditionalInstrumentation
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsLoggingInstrumentation
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsMultipleBreakpoints
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsMultipleBreakpointsDisabled
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsMultipleBreakpointsMuted
import org.ide.lti.core.designsystem.generated.resources.ideBreakpointsQuestionBadge
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "breakpoints" icon category.
 * Auto-generated from the bulk-imported expui/breakpoints/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideBreakpointsBreakpoint: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpoint) }
val AppIcons.ideBreakpointsBreakpointDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointDependent) }
val AppIcons.ideBreakpointsBreakpointDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointDisabled) }
val AppIcons.ideBreakpointsBreakpointException: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointException) }
val AppIcons.ideBreakpointsBreakpointExceptionDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointExceptionDisabled) }
val AppIcons.ideBreakpointsBreakpointField: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointField) }
val AppIcons.ideBreakpointsBreakpointFieldDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldDependent) }
val AppIcons.ideBreakpointsBreakpointFieldDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldDisabled) }
val AppIcons.ideBreakpointsBreakpointFieldMuted: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldMuted) }
val AppIcons.ideBreakpointsBreakpointFieldMutedDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldMutedDependent) }
val AppIcons.ideBreakpointsBreakpointFieldMutedDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldMutedDisabled) }
val AppIcons.ideBreakpointsBreakpointFieldUnsuspendent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldUnsuspendent) }
val AppIcons.ideBreakpointsBreakpointFieldUnsuspendentDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldUnsuspendentDisabled) }
val AppIcons.ideBreakpointsBreakpointFieldUnsuspendentValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldUnsuspendentValid) }
val AppIcons.ideBreakpointsBreakpointFieldValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointFieldValid) }
val AppIcons.ideBreakpointsBreakpointInvalid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointInvalid) }
val AppIcons.ideBreakpointsBreakpointLambda: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointLambda) }
val AppIcons.ideBreakpointsBreakpointMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethod) }
val AppIcons.ideBreakpointsBreakpointMethodDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodDependent) }
val AppIcons.ideBreakpointsBreakpointMethodDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodDisabled) }
val AppIcons.ideBreakpointsBreakpointMethodMuted: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodMuted) }
val AppIcons.ideBreakpointsBreakpointMethodMutedDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodMutedDependent) }
val AppIcons.ideBreakpointsBreakpointMethodMutedDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodMutedDisabled) }
val AppIcons.ideBreakpointsBreakpointMethodUnsuspendent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodUnsuspendent) }
val AppIcons.ideBreakpointsBreakpointMethodUnsuspendentDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodUnsuspendentDisabled) }
val AppIcons.ideBreakpointsBreakpointMethodUnsuspendentValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodUnsuspendentValid) }
val AppIcons.ideBreakpointsBreakpointMethodValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMethodValid) }
val AppIcons.ideBreakpointsBreakpointMuted: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMuted) }
val AppIcons.ideBreakpointsBreakpointMutedDependent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMutedDependent) }
val AppIcons.ideBreakpointsBreakpointMutedDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointMutedDisabled) }
val AppIcons.ideBreakpointsBreakpointObsolete: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointObsolete) }
val AppIcons.ideBreakpointsBreakpointUnsuspendent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointUnsuspendent) }
val AppIcons.ideBreakpointsBreakpointUnsuspendentDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointUnsuspendentDisabled) }
val AppIcons.ideBreakpointsBreakpointUnsuspendentValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointUnsuspendentValid) }
val AppIcons.ideBreakpointsBreakpointValid: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsBreakpointValid) }
val AppIcons.ideBreakpointsConditionalInstrumentation: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsConditionalInstrumentation) }
val AppIcons.ideBreakpointsLoggingInstrumentation: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsLoggingInstrumentation) }
val AppIcons.ideBreakpointsMultipleBreakpoints: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsMultipleBreakpoints) }
val AppIcons.ideBreakpointsMultipleBreakpointsDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsMultipleBreakpointsDisabled) }
val AppIcons.ideBreakpointsMultipleBreakpointsMuted: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsMultipleBreakpointsMuted) }
val AppIcons.ideBreakpointsQuestionBadge: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBreakpointsQuestionBadge) }
