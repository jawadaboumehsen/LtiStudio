/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup.doctor

import org.ide.lti.core.domain.setup.DiagnosticCheckItem

/**
 * Use case to run the full pre-flight system diagnostics ("lti doctor") suite.
 */
class RunSystemDiagnosticsUseCase(
    private val doctorPort: SystemDoctorPort,
) {
    suspend operator fun invoke(distro: String, binariesPresent: Boolean): List<DiagnosticCheckItem> =
        doctorPort.runDiagnostics(distro, binariesPresent)
}
