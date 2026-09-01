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
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerAddToWatch
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerClassLevelWatch
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerDbArray
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerDbObject
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerDbPrimitive
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerDebuggerSync
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerEvaluationResult
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerExecuteCurrentStatement
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerFrame
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerFreeze
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerFreezeAll
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerInspectionSeverity
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerNextStatement
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerOverhead
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerPinToTopPinnedItem
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerPinToTopUnpinnedItem
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerPromptInput
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerPromptInputHistory
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerSelfReference
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerSpecialVar
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerStepIntoMyCode
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThaw
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThawAll
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadAtBreakpoint
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadCurrent
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadDaemon
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadFrozen
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadGroupCurrent
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadRunning
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreadVirtual
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerThreads
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerValue
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerVariablesTab
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerWatch
import org.ide.lti.core.designsystem.generated.resources.ideDebuggerWatchLastReturnValue
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "debugger" icon category.
 * Auto-generated from the bulk-imported expui/debugger/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideDebuggerAddToWatch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerAddToWatch) }
val AppIcons.ideDebuggerClassLevelWatch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerClassLevelWatch) }
val AppIcons.ideDebuggerDbArray: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerDbArray) }
val AppIcons.ideDebuggerDbObject: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerDbObject) }
val AppIcons.ideDebuggerDbPrimitive: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerDbPrimitive) }
val AppIcons.ideDebuggerDebuggerSync: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerDebuggerSync) }
val AppIcons.ideDebuggerEvaluationResult: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerEvaluationResult) }
val AppIcons.ideDebuggerExecuteCurrentStatement: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerExecuteCurrentStatement) }
val AppIcons.ideDebuggerFrame: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerFrame) }
val AppIcons.ideDebuggerFreeze: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerFreeze) }
val AppIcons.ideDebuggerFreezeAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerFreezeAll) }
val AppIcons.ideDebuggerInspectionSeverity: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerInspectionSeverity) }
val AppIcons.ideDebuggerNextStatement: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerNextStatement) }
val AppIcons.ideDebuggerOverhead: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerOverhead) }
val AppIcons.ideDebuggerPinToTopPinnedItem: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerPinToTopPinnedItem) }
val AppIcons.ideDebuggerPinToTopUnpinnedItem: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerPinToTopUnpinnedItem) }
val AppIcons.ideDebuggerPromptInput: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerPromptInput) }
val AppIcons.ideDebuggerPromptInputHistory: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerPromptInputHistory) }
val AppIcons.ideDebuggerSelfReference: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerSelfReference) }
val AppIcons.ideDebuggerSpecialVar: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerSpecialVar) }
val AppIcons.ideDebuggerStepIntoMyCode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerStepIntoMyCode) }
val AppIcons.ideDebuggerThaw: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThaw) }
val AppIcons.ideDebuggerThawAll: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThawAll) }
val AppIcons.ideDebuggerThreadAtBreakpoint: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadAtBreakpoint) }
val AppIcons.ideDebuggerThreadCurrent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadCurrent) }
val AppIcons.ideDebuggerThreadDaemon: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadDaemon) }
val AppIcons.ideDebuggerThreadFrozen: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadFrozen) }
val AppIcons.ideDebuggerThreadGroupCurrent: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadGroupCurrent) }
val AppIcons.ideDebuggerThreadRunning: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadRunning) }
val AppIcons.ideDebuggerThreadVirtual: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreadVirtual) }
val AppIcons.ideDebuggerThreads: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerThreads) }
val AppIcons.ideDebuggerValue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerValue) }
val AppIcons.ideDebuggerVariablesTab: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerVariablesTab) }
val AppIcons.ideDebuggerWatch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerWatch) }
val AppIcons.ideDebuggerWatchLastReturnValue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideDebuggerWatchLastReturnValue) }
