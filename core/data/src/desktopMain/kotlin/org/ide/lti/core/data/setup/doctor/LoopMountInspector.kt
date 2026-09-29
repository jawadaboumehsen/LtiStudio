/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.doctor

import io.ltirom.tooling.client.wsl.WslCliExecutor
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying Linux loop device control (/dev/loop-control) and passwordless sudo mount elevation.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating loop mount permissions.
 */
public class LoopMountInspector(private val cli: WslCliExecutor = WslCliExecutor()) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val hasLoopControl = if (context.deviceNodes.isNotEmpty()) {
            "loop-control" in context.deviceNodes
        } else {
            cli.execute(distro, listOf("test", "-e", "/dev/loop-control")).exitCode == 0
        }

        return DiagnosticCheckItem(
            id = "loop_mount",
            title = "Linux Loop Device Control",
            category = DiagnosticCategory.KERNEL_AND_MOUNT,
            status = if (hasLoopControl) StepStatus.SUCCESS else StepStatus.WARNING,
            detail = if (hasLoopControl) {
                "/dev/loop-control character device node available for loop partition workflows."
            } else {
                "/dev/loop-control not found; kernel loop device support may be restricted in this WSL instance."
            },
            remediation = if (hasLoopControl) {
                null
            } else {
                "Kernel loop device support is optional. Verify WSL kernel configuration if raw partition mounting " +
                    "is needed."
            },
            copyableCommand = null,
            isOptionalForRuntime = true,
        )
    }
}
