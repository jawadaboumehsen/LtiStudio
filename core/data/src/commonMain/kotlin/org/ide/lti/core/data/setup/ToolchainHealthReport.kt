/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import io.ltirom.tooling.core.remote.RunPurpose
import org.ide.lti.core.domain.setup.ports.InstallId

/**
 * The four separate health facts required by FR-036 for recovery assessment (008 T083, T086).
 */
public data class ToolchainHealthReport(
    val selectedInstallId: InstallId?,
    val isIntact: Boolean,
    val serviceResolvesTools: Boolean,
    val lastOperationCompleted: Boolean,
    val activeInstallCorrupt: Boolean = selectedInstallId != null && !isIntact,
) {
    public val isHealthy: Boolean
        get() = selectedInstallId != null && isIntact && serviceResolvesTools && lastOperationCompleted
}

/**
 * Evaluates run purpose for recovery-directed repairs (008 T083).
 */
public object RecoveryHealthCheck {
    public fun determineRunPurpose(isRecovery: Boolean): RunPurpose =
        if (isRecovery) RunPurpose.SETUP else RunPurpose.WORKSPACE
}
