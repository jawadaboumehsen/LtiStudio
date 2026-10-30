/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

public typealias StepCommand = org.ide.lti.core.model.run.StepCommand

/** An argument the orchestrator replaces with the run's pinned path of a catalog tool. */
public const val PINNED_TOOL_ARG: String = "@tool:"

/** [toolId] as an argument of another command (`fakeroot sh -c … sh <dump.f2fs>`), resolved like a step's tool. */
public fun pinnedToolArg(toolId: String): String = PINNED_TOOL_ARG + toolId

public fun stepCommand(toolId: String, args: List<String>, workingDir: String, timeoutMs: Long = 0L): StepCommand =
    StepCommand(
        toolId = toolId,
        args = args,
        workingDir = workingDir,
        timeoutMs = timeoutMs,
    )
