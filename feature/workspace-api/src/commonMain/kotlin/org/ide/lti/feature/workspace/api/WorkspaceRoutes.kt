/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.api

import androidx.navigation.NavController
import androidx.navigation.NavOptions

const val WORKSPACE_ROUTE = "workspace"
const val CREATE_WORKSPACE_ROUTE = "workspace/create"

fun NavController.navigateToWorkspace(navOptions: NavOptions? = null) {
    navigate(WORKSPACE_ROUTE, navOptions)
}

fun NavController.navigateToCreateWorkspace(navOptions: NavOptions? = null) {
    navigate(CREATE_WORKSPACE_ROUTE, navOptions)
}
