/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val SETUP_ROUTE = "setup"

fun NavController.navigateToSetup(navOptions: NavOptions? = null) {
    navigate(SETUP_ROUTE, navOptions)
}

/** Internal tab selector for Setup's multi-step UI (Projects / Environment / Launchpad / Tools / Recovery). */
enum class SetupTab(val id: String) {
    PROJECTS("projects"),
    ENVIRONMENT("environment"),
    TOOLS("tools"),
    RECOVERY("recovery"),
    ;

    companion object {
        fun find(id: String): SetupTab? = entries.firstOrNull { it.id == id }
        fun fromId(id: String): SetupTab = find(id) ?: PROJECTS
    }
}

/**
 * Route-boundary destination for the Setup feature's navigation graph.
 */
enum class SetupDestination(val id: String) {
    WORKSPACES("workspaces"),
    ENVIRONMENT("environment"),
    TOOLS("tools"),
    RECOVERY("recovery"),
    ;

    companion object {
        fun fromId(id: String): SetupDestination = when (id) {
            "workspaces" -> WORKSPACES
            "environment" -> ENVIRONMENT
            "tools" -> TOOLS
            "recovery" -> RECOVERY
            "projects", "launchpad" -> WORKSPACES
            else -> WORKSPACES
        }
    }
}
