/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.TrustDecision

public data class PluginManagerActions(
    val onNavigateBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    val onSelectDestination: (PluginDestination) -> Unit = {},
    val onSearchQueryChange: (String) -> Unit = {},
    val onOpenDetails: (InstalledRecord) -> Unit = {},
    val onCloseDetails: () -> Unit = {},
    val onEnableWorkspace: (InstalledRecord, String) -> Unit = { _, _ -> },
    val onDisableWorkspace: (InstalledRecord, String) -> Unit = { _, _ -> },
    val onArchivePackage: (InstalledRecord) -> Unit = {},
    val onUninstallPackage: (InstalledRecord) -> Unit = {},
    val onMarketplaceUrlChange: (String) -> Unit = {},
    val onConfigureMarketplaceSource: (String, String) -> Unit = { _, _ -> },
    val onRefreshMarketplace: () -> Unit = {},
    val onInstallMarketplace: (IndexEntry, TrustDecision) -> Unit = { _, _ -> },
    val onImportPathChange: (String) -> Unit = {},
    val onInspectImportPath: (String) -> Unit = {},
    val onDismissLegacyWarning: () -> Unit = {},
    val onConfirmImportInstall: (TrustDecision) -> Unit = {},
    val onAuthorProjectPathChange: (String) -> Unit = {},
    val onCheckAuthorProject: (String) -> Unit = {},
    val onApproveAuthorProject: () -> Unit = {},
    val onRevokeAuthorProject: () -> Unit = {},
    val onSelectAuthorTask: (String) -> Unit = {},
    val onRunAuthorTask: (String) -> Unit = {},
    val onDismissNotification: () -> Unit = {},
)
