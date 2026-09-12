/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import org.ide.lti.core.model.workspace.Workspace

/** Internal tab selector for [SetupScreen]'s own multi-step UI (Projects / Environment / Launchpad). */
enum class SetupTab(val id: String) {
    PROJECTS("projects"),
    ENVIRONMENT("environment"),
    LAUNCHPAD("launchpad");

    companion object {
        fun fromId(id: String): SetupTab = entries.firstOrNull { it.id == id } ?: PROJECTS
    }
}

/**
 * Route-boundary destination for the Setup feature's navigation graph.
 * Legacy launchpad/projects identifiers resolve to [WORKSPACES] here (FR-001, FR-004, FR-006).
 */
enum class SetupDestination(val id: String) {
    WORKSPACES("workspaces"),
    ENVIRONMENT("environment"),
    TOOLS("tools");

    companion object {
        fun fromId(id: String): SetupDestination = when (id) {
            "workspaces" -> WORKSPACES
            "environment" -> ENVIRONMENT
            "tools" -> TOOLS
            "projects", "launchpad" -> WORKSPACES
            else -> WORKSPACES
        }
    }
}

const val SETUP_ROUTE = "setup"

fun NavController.navigateToSetup(navOptions: NavOptions? = null) {
    navigate(SETUP_ROUTE, navOptions)
}

fun NavGraphBuilder.setupScreen(
    onOpenWorkspace: (Workspace) -> Unit,
    onOpenSettings: () -> Unit = {},
    onCreateWorkspace: () -> Unit = {},
    onOpenConfigurations: () -> Unit = {},
    onOpenManageTargets: () -> Unit = onOpenConfigurations,
    onOpenHelp: () -> Unit = {},
) {
    composable(route = SETUP_ROUTE) {
        SetupScreen(
            onOpenWorkspace = onOpenWorkspace,
            onOpenSettings = onOpenSettings,
            onCreateWorkspace = onCreateWorkspace,
            onOpenConfigurations = onOpenConfigurations,
            onOpenManageTargets = onOpenManageTargets,
            onOpenHelp = onOpenHelp,
        )
    }
}
