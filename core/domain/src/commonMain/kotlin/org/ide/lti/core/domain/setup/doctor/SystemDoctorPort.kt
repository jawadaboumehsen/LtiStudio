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
 * Port for pre-flight environment diagnostics ("lti doctor") and automated remediation.
 */
public interface SystemDoctorPort {
    public suspend fun runDiagnostics(
        distro: String,
        binariesPresent: Boolean,
        onProgress: ((title: String, index: Int, total: Int) -> Unit)? = null,
    ): List<DiagnosticCheckItem>

    public suspend fun autoRemediate(distro: String, onProgress: (String) -> Unit = {}): Boolean
}
