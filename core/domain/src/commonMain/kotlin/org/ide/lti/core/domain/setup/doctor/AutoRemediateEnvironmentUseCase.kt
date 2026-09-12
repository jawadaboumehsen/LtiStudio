package org.ide.lti.core.domain.setup.doctor

/**
 * Use case to automatically fix and install missing system packages and dependencies
 * detected by [RunSystemDiagnosticsUseCase].
 */
class AutoRemediateEnvironmentUseCase(
    private val doctorPort: SystemDoctorPort,
) {
    suspend operator fun invoke(distro: String, onProgress: (String) -> Unit = {}): Boolean =
        doctorPort.autoRemediate(distro, onProgress)
}
