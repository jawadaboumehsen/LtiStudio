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
import org.ide.lti.core.data.setup.AptCommandBuilder
import org.ide.lti.core.domain.setup.DiagnosticCategory
import org.ide.lti.core.domain.setup.DiagnosticCheckItem
import org.ide.lti.core.domain.setup.StepStatus

/**
 * Inspector verifying WSL2 kernel /dev/fuse node and fuse3/fusermount userspace binaries.
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to validating FUSE mounts.
 */
public class KernelFuseInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val fuseDevOk = if (context.deviceNodes.isNotEmpty()) {
            "fuse" in context.deviceNodes
        } else {
            cli.execute(distro, listOf("test", "-r", "/dev/fuse")).exitCode == 0
        }
        val hasFuseCli = if (context.systemCommands.isNotEmpty()) {
            "fusermount3" in context.systemCommands || "fusermount" in context.systemCommands
        } else {
            cli.execute(distro, listOf("which", "fusermount3")).exitCode == 0 ||
                cli.execute(distro, listOf("which", "fusermount")).exitCode == 0
        }
        val fuseOk = fuseDevOk && hasFuseCli
        val fuseCmd = if (fuseOk) null else AptCommandBuilder.install(listOf("fuse3"))

        return DiagnosticCheckItem(
            id = "dev_fuse",
            title = "Kernel FUSE & Userspace Runtime",
            category = DiagnosticCategory.KERNEL_AND_MOUNT,
            status = when {
                !fuseDevOk -> StepStatus.FAILED
                !hasFuseCli -> StepStatus.WARNING
                else -> StepStatus.SUCCESS
            },
            detail = when {
                !fuseDevOk -> "/dev/fuse device node not accessible in WSL2 kernel."
                !hasFuseCli -> "/dev/fuse is accessible, but userspace fuse3/fusermount is missing."
                else -> "/dev/fuse and fuse3 userspace tools verified for erofsfuse mounting."
            },
            remediation = when {
                !fuseDevOk -> "Enable FUSE support in WSL2 distribution."
                !hasFuseCli -> "Run '$fuseCmd' in WSL."
                else -> null
            },
            copyableCommand = if (!hasFuseCli) fuseCmd else null,
        )
    }
}
