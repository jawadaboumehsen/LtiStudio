package org.ide.lti.core.domain.pipeline

public typealias StepCommand = org.ide.lti.core.model.run.StepCommand

public fun stepCommand(
    toolId: String,
    args: List<String>,
    workingDir: String,
    timeoutMs: Long = 0L
): StepCommand = StepCommand(
    toolId = toolId,
    args = args,
    workingDir = workingDir,
    timeoutMs = timeoutMs
)
