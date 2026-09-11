package org.ide.lti.feature.setup

import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.Workspace

fun SetupViewModel.onSearchQueryChange(query: String) {
    _searchQuery.value = query
}

fun SetupViewModel.selectTab(tab: SetupTab) {
    _selectedTab.value = tab
}

fun SetupViewModel.dismissEnvironmentNotice() {
    _environmentNotice.value = null
}

fun SetupViewModel.navigateToEnvironment() {
    _selectedTab.value = SetupTab.ENVIRONMENT
    if (!toolchainSetupState.value.canLaunchWorkspace) {
        autoRunEnvironmentSetup(autoFix = true)
    }
}

fun SetupViewModel.selectCockpitTab(tab: String) {
    _selectedCockpitTab.value = tab
}

fun SetupViewModel.onToolSearchQueryChange(query: String) {
    _toolSearchQuery.value = query
}

fun SetupViewModel.selectToolCategory(category: String) {
    _selectedToolCategory.value = category
}

fun SetupViewModel.setAutoDoctorEnabled(enabled: Boolean) {
    _isAutoDoctorEnabled.value = enabled
}

internal fun fallbackTargetWorkspace(target: TargetDevice, targetPath: String): Workspace = Workspace(
    id = "target_${target.targetKey.lowercase()}",
    name = target.friendlyTitle,
    path = targetPath,
)
