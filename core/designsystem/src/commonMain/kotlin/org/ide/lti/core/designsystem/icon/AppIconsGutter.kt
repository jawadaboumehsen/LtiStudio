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
import org.ide.lti.core.designsystem.generated.resources.ideGutterBookmark
import org.ide.lti.core.designsystem.generated.resources.ideGutterColors
import org.ide.lti.core.designsystem.generated.resources.ideGutterDataSchema
import org.ide.lti.core.designsystem.generated.resources.ideGutterExtAnnotation
import org.ide.lti.core.designsystem.generated.resources.ideGutterFold
import org.ide.lti.core.designsystem.generated.resources.ideGutterFoldBottom
import org.ide.lti.core.designsystem.generated.resources.ideGutterImplementedMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterImplementingFunctionalInterface
import org.ide.lti.core.designsystem.generated.resources.ideGutterImplementingMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterJavadocEdit
import org.ide.lti.core.designsystem.generated.resources.ideGutterJavadocRead
import org.ide.lti.core.designsystem.generated.resources.ideGutterMnemonic
import org.ide.lti.core.designsystem.generated.resources.ideGutterOverridenMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterOverridingMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterReadAccess
import org.ide.lti.core.designsystem.generated.resources.ideGutterRecursiveMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterRerun
import org.ide.lti.core.designsystem.generated.resources.ideGutterRun
import org.ide.lti.core.designsystem.generated.resources.ideGutterRunError
import org.ide.lti.core.designsystem.generated.resources.ideGutterRunFailed
import org.ide.lti.core.designsystem.generated.resources.ideGutterRunInQueue
import org.ide.lti.core.designsystem.generated.resources.ideGutterRunSuccess
import org.ide.lti.core.designsystem.generated.resources.ideGutterSiblingInheritedMethod
import org.ide.lti.core.designsystem.generated.resources.ideGutterSuggestedRefactoring
import org.ide.lti.core.designsystem.generated.resources.ideGutterSuggestedRefactoringDisabled
import org.ide.lti.core.designsystem.generated.resources.ideGutterUnfold
import org.ide.lti.core.designsystem.generated.resources.ideGutterWeb
import org.ide.lti.core.designsystem.generated.resources.ideGutterWriteAccess
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "gutter" icon category.
 * Auto-generated from the bulk-imported expui/gutter/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideGutterBookmark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterBookmark) }
val AppIcons.ideGutterColors: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterColors) }
val AppIcons.ideGutterDataSchema: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterDataSchema) }
val AppIcons.ideGutterExtAnnotation: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterExtAnnotation) }
val AppIcons.ideGutterFold: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterFold) }
val AppIcons.ideGutterFoldBottom: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterFoldBottom) }
val AppIcons.ideGutterImplementedMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterImplementedMethod) }
val AppIcons.ideGutterImplementingFunctionalInterface: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterImplementingFunctionalInterface) }
val AppIcons.ideGutterImplementingMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterImplementingMethod) }
val AppIcons.ideGutterJavadocEdit: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterJavadocEdit) }
val AppIcons.ideGutterJavadocRead: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterJavadocRead) }
val AppIcons.ideGutterMnemonic: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterMnemonic) }
val AppIcons.ideGutterOverridenMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterOverridenMethod) }
val AppIcons.ideGutterOverridingMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterOverridingMethod) }
val AppIcons.ideGutterReadAccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterReadAccess) }
val AppIcons.ideGutterRecursiveMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRecursiveMethod) }
val AppIcons.ideGutterRerun: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRerun) }
val AppIcons.ideGutterRun: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRun) }
val AppIcons.ideGutterRunError: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRunError) }
val AppIcons.ideGutterRunFailed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRunFailed) }
val AppIcons.ideGutterRunInQueue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRunInQueue) }
val AppIcons.ideGutterRunSuccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterRunSuccess) }
val AppIcons.ideGutterSiblingInheritedMethod: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterSiblingInheritedMethod) }
val AppIcons.ideGutterSuggestedRefactoring: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterSuggestedRefactoring) }
val AppIcons.ideGutterSuggestedRefactoringDisabled: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterSuggestedRefactoringDisabled) }
val AppIcons.ideGutterUnfold: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterUnfold) }
val AppIcons.ideGutterWeb: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterWeb) }
val AppIcons.ideGutterWriteAccess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideGutterWriteAccess) }
