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
