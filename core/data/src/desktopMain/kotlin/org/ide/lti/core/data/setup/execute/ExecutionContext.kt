/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.execute

import io.ltirom.tooling.client.wsl.WslCliExecutor
import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.data.setup.SetupJournalContext
import org.ide.lti.core.data.setup.state.InstalledScope
import org.ide.lti.core.data.setup.state.OperationScope
import org.ide.lti.core.domain.ports.DaemonFailureCategory
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.ToolchainFailureCategory
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupPlan
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.domain.setup.StepStatus

public data class ExecutionContext(
    val distro: String,
    val home: String,
    val binDir: String,
    val extDir: String,
    val workDir: String,
    val plan: SetupPlan,
    val scope: OperationScope,
    val installedScope: InstalledScope? = null,
    val journal: SetupJournalContext? = null,
    val actionsToSkip: Set<String> = emptySet(),
    val serviceCli: WslCliExecutor? = null,
    val repository: ToolchainSetupRepository? = null,
    val transport: RemoteTransportPort? = null,
    val toolPublicationPort: ToolPublicationPort? = null,
    val recordRunStarted: (SetupStepStage, String) -> Unit = { _, _ -> },
    val setStepProgress: (SetupStepStage, String, Int, Int) -> Unit = { _, _, _, _ -> },
    val setStepStatus: (
        SetupStepStage,
        StepStatus,
        String?,
        String?,
        DaemonFailureCategory?,
        ToolchainFailureCategory?,
    ) -> Unit = { _, _, _, _, _, _ -> },
    val appendLog: (String) -> Unit = {},
)
