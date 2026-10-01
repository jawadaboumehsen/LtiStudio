/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check.stages

import org.ide.lti.core.data.setup.ToolchainBuildEngine
import org.ide.lti.core.data.setup.check.CheckContext
import org.ide.lti.core.data.setup.check.CheckStage
import org.ide.lti.core.data.setup.check.StageOutcome
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.setup.SetupStepStage

public class ToolchainStage : CheckStage {
    override val stage: SetupStepStage = SetupStepStage.TOOLCHAIN_COMPILATION

    override suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome {
        val distro = ctx.environment.distro
        val missing = mutableListOf<String>()

        for (core in ToolchainBuildEngine.CORE_BINARIES) {
            val res = ctx.toolVerifier.verify(
                distro = distro,
                toolId = core,
                binDir = ctx.binDir,
                binaryName = ToolchainBuildEngine.installedName(core),
            )
            if (!res.isSuccess) {
                missing += core
            }
        }

        return if (missing.isEmpty()) {
            StageOutcome.Success("All core toolchain binaries verified in ${ctx.binDir}.")
        } else {
            StageOutcome.Pending("Missing or unverified toolchain binaries: ${missing.joinToString()}")
        }
    }
}
