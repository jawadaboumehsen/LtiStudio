package org.ide.lti.core.domain.usecase.workspace

import org.ide.lti.core.common.utils.DialogUtils
import org.ide.lti.core.model.workspace.Workspace

/**
 * Use case to show a directory-selection dialog and open the chosen directory as a workspace.
 *
 * Returns `Result.success(null)` if the user cancelled the picker.
 */
class SelectAndOpenDirectoryUseCase(
    private val openWorkspaceByPathUseCase: OpenWorkspaceByPathUseCase,
) {
    suspend operator fun invoke(): Result<Workspace?> {
        val selectedPath = DialogUtils.selectDirectory()
        return if (!selectedPath.isNullOrBlank()) {
            openWorkspaceByPathUseCase(selectedPath).map { it }
        } else {
            Result.success(null)
        }
    }
}
