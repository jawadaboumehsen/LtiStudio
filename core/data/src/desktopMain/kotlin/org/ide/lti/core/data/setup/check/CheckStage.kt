/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup.check

import io.ltirom.tooling.core.ports.RemoteTransportPort
import org.ide.lti.core.data.setup.ToolVerifier
import org.ide.lti.core.data.setup.state.CheckScope
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.repository.setup.ToolchainSetupRepository
import org.ide.lti.core.domain.setup.SetupLogKind
import org.ide.lti.core.domain.setup.SetupStepStage
import org.ide.lti.core.model.setup.SetupEnvironment

public sealed interface StageOutcome {
    public val description: String

    public data class Success(override val description: String) : StageOutcome
    public data class Failed(override val description: String, val error: String? = null) : StageOutcome
    public data class Warning(override val description: String, val warning: String? = null) : StageOutcome
    public data class Pending(override val description: String) : StageOutcome
}

public data class CheckContext(
    val environment: SetupEnvironment,
    val readOnlyCommands: ReadOnlyCommands,
    val toolVerifier: ToolVerifier,
    val userHome: String,
    val workDir: String,
    val binDir: String,
    val extDir: String,
    val toolPublicationPort: ToolPublicationPort? = null,
    val transport: RemoteTransportPort? = null,
    val repository: ToolchainSetupRepository? = null,
    val log: (String, SetupLogKind) -> Unit = { _, _ -> },
)

public interface CheckStage {
    public val stage: SetupStepStage
    public suspend fun run(ctx: CheckContext, scope: CheckScope): StageOutcome
}
