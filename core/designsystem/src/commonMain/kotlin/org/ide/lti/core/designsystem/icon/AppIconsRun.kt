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
import org.ide.lti.core.designsystem.generated.resources.ideRunAttachToProcess
import org.ide.lti.core.designsystem.generated.resources.ideRunDebug
import org.ide.lti.core.designsystem.generated.resources.ideRunDebugStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunDumpThreads
import org.ide.lti.core.designsystem.generated.resources.ideRunEvaluateExpression
import org.ide.lti.core.designsystem.generated.resources.ideRunForceRunToCursor
import org.ide.lti.core.designsystem.generated.resources.ideRunForceStepInto
import org.ide.lti.core.designsystem.generated.resources.ideRunForceStepOver
import org.ide.lti.core.designsystem.generated.resources.ideRunKillProcess
import org.ide.lti.core.designsystem.generated.resources.ideRunMuteBreakpoints
import org.ide.lti.core.designsystem.generated.resources.ideRunPause
import org.ide.lti.core.designsystem.generated.resources.ideRunProfile
import org.ide.lti.core.designsystem.generated.resources.ideRunRerun
import org.ide.lti.core.designsystem.generated.resources.ideRunRerunStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunRestart
import org.ide.lti.core.designsystem.generated.resources.ideRunRestartDebug
import org.ide.lti.core.designsystem.generated.resources.ideRunRestartDebugStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunRestartFailedTests
import org.ide.lti.core.designsystem.generated.resources.ideRunRestartFrame
import org.ide.lti.core.designsystem.generated.resources.ideRunResume
import org.ide.lti.core.designsystem.generated.resources.ideRunResumeStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunRun
import org.ide.lti.core.designsystem.generated.resources.ideRunRunStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunRunToCursor
import org.ide.lti.core.designsystem.generated.resources.ideRunRunWithCoverage
import org.ide.lti.core.designsystem.generated.resources.ideRunShowCurrentFrame
import org.ide.lti.core.designsystem.generated.resources.ideRunShowIgnored
import org.ide.lti.core.designsystem.generated.resources.ideRunShowPassed
import org.ide.lti.core.designsystem.generated.resources.ideRunSmartStepInto
import org.ide.lti.core.designsystem.generated.resources.ideRunStepInto
import org.ide.lti.core.designsystem.generated.resources.ideRunStepOut
import org.ide.lti.core.designsystem.generated.resources.ideRunStepOutCodeBlock
import org.ide.lti.core.designsystem.generated.resources.ideRunStepOver
import org.ide.lti.core.designsystem.generated.resources.ideRunStop
import org.ide.lti.core.designsystem.generated.resources.ideRunStopStroke
import org.ide.lti.core.designsystem.generated.resources.ideRunTestCustom
import org.ide.lti.core.designsystem.generated.resources.ideRunTestError
import org.ide.lti.core.designsystem.generated.resources.ideRunTestFailed
import org.ide.lti.core.designsystem.generated.resources.ideRunTestIgnored
import org.ide.lti.core.designsystem.generated.resources.ideRunTestNotRunYet
import org.ide.lti.core.designsystem.generated.resources.ideRunTestPassed
import org.ide.lti.core.designsystem.generated.resources.ideRunTestPassedIgnored
import org.ide.lti.core.designsystem.generated.resources.ideRunTestPaused
import org.ide.lti.core.designsystem.generated.resources.ideRunTestSkipped
import org.ide.lti.core.designsystem.generated.resources.ideRunTestTerminated
import org.ide.lti.core.designsystem.generated.resources.ideRunTestUnknown
import org.ide.lti.core.designsystem.generated.resources.ideRunViewBreakpoints
import org.ide.lti.core.designsystem.generated.resources.ideRunWidgetBuild
import org.ide.lti.core.designsystem.generated.resources.ideRunWidgetRestart
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "run" icon category.
 * Auto-generated from the bulk-imported expui/run/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideRunAttachToProcess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunAttachToProcess) }
val AppIcons.ideRunDebug: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunDebug) }
val AppIcons.ideRunDebugStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunDebugStroke) }
val AppIcons.ideRunDumpThreads: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunDumpThreads) }
val AppIcons.ideRunEvaluateExpression: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunEvaluateExpression) }
val AppIcons.ideRunForceRunToCursor: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunForceRunToCursor) }
val AppIcons.ideRunForceStepInto: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunForceStepInto) }
val AppIcons.ideRunForceStepOver: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunForceStepOver) }
val AppIcons.ideRunKillProcess: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunKillProcess) }
val AppIcons.ideRunMuteBreakpoints: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunMuteBreakpoints) }
val AppIcons.ideRunPause: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunPause) }
val AppIcons.ideRunProfile: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunProfile) }
val AppIcons.ideRunRerun: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRerun) }
val AppIcons.ideRunRerunStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRerunStroke) }
val AppIcons.ideRunRestart: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRestart) }
val AppIcons.ideRunRestartDebug: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRestartDebug) }
val AppIcons.ideRunRestartDebugStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRestartDebugStroke) }
val AppIcons.ideRunRestartFailedTests: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRestartFailedTests) }
val AppIcons.ideRunRestartFrame: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRestartFrame) }
val AppIcons.ideRunResume: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunResume) }
val AppIcons.ideRunResumeStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunResumeStroke) }
val AppIcons.ideRunRun: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRun) }
val AppIcons.ideRunRunStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRunStroke) }
val AppIcons.ideRunRunToCursor: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRunToCursor) }
val AppIcons.ideRunRunWithCoverage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunRunWithCoverage) }
val AppIcons.ideRunShowCurrentFrame: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunShowCurrentFrame) }
val AppIcons.ideRunShowIgnored: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunShowIgnored) }
val AppIcons.ideRunShowPassed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunShowPassed) }
val AppIcons.ideRunSmartStepInto: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunSmartStepInto) }
val AppIcons.ideRunStepInto: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStepInto) }
val AppIcons.ideRunStepOut: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStepOut) }
val AppIcons.ideRunStepOutCodeBlock: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStepOutCodeBlock) }
val AppIcons.ideRunStepOver: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStepOver) }
val AppIcons.ideRunStop: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStop) }
val AppIcons.ideRunStopStroke: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunStopStroke) }
val AppIcons.ideRunTestCustom: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestCustom) }
val AppIcons.ideRunTestError: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestError) }
val AppIcons.ideRunTestFailed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestFailed) }
val AppIcons.ideRunTestIgnored: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestIgnored) }
val AppIcons.ideRunTestNotRunYet: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestNotRunYet) }
val AppIcons.ideRunTestPassed: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestPassed) }
val AppIcons.ideRunTestPassedIgnored: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestPassedIgnored) }
val AppIcons.ideRunTestPaused: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestPaused) }
val AppIcons.ideRunTestSkipped: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestSkipped) }
val AppIcons.ideRunTestTerminated: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestTerminated) }
val AppIcons.ideRunTestUnknown: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunTestUnknown) }
val AppIcons.ideRunViewBreakpoints: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunViewBreakpoints) }
val AppIcons.ideRunWidgetBuild: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunWidgetBuild) }
val AppIcons.ideRunWidgetRestart: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunWidgetRestart) }
