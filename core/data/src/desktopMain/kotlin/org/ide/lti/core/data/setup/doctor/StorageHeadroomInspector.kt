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
 * Inspector verifying host ext4 storage headroom (minimum 45 GB recommended for ROM staging).
 *
 * Adheres to:
 * - Single Responsibility Principle (SRP): Dedicated exclusively to storage capacity evaluation.
 */
public class StorageHeadroomInspector(
    private val cli: WslCliExecutor = WslCliExecutor(),
) : SystemDiagnosticInspector {

    override suspend fun inspect(context: DiagnosticContext): DiagnosticCheckItem {
        val distro = context.distro
        val home = context.home
        val dfRes = cli.execute(distro, listOf("df", "-BG", home))
        val freeGb = runCatching {
            if (dfRes.exitCode != 0) return@runCatching null
            val lines = dfRes.output.lines()
            if (lines.size >= 2) {
                val tokens = lines[1].trim().split("\\s+".toRegex())
                tokens.getOrNull(3)?.removeSuffix("G")?.toLongOrNull()
            } else {
                null
            }
        }.getOrNull()

        if (freeGb == null) {
            return DiagnosticCheckItem(
                id = "disk_headroom",
                title = "ext4 Storage Headroom (>= 45 GB)",
                category = DiagnosticCategory.STORAGE,
                status = StepStatus.FAILED,
                detail = "Unable to measure available disk space in $home: ${dfRes.error.ifBlank { "df command failed" }}",
                remediation = "Verify WSL filesystem permissions and available mount storage.",
            )
        }

        val diskStatus = when {
            freeGb >= 45L -> StepStatus.SUCCESS
            freeGb >= 25L -> StepStatus.WARNING
            else -> StepStatus.FAILED
        }

        return DiagnosticCheckItem(
            id = "disk_headroom",
            title = "ext4 Storage Headroom (>= 45 GB)",
            category = DiagnosticCategory.STORAGE,
            status = diskStatus,
            detail = "$freeGb GB free in $home (minimum 45 GB recommended).",
            remediation = if (diskStatus == StepStatus.SUCCESS) null else "Free disk space in WSL rootfs to ensure safe ROM packaging.",
        )
    }
}
