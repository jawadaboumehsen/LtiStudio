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
