/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.navigation

import androidx.navigation.NavController
import androidx.navigation.NavOptions
import org.ide.lti.core.model.run.StageId
import org.ide.lti.feature.workspace.api.buildPluginsRoute as apiBuildPluginsRoute
import org.ide.lti.feature.workspace.api.buildRunRoute as apiBuildRunRoute
import org.ide.lti.feature.workspace.api.buildStudioRoute as apiBuildStudioRoute
import org.ide.lti.feature.workspace.api.navigateToPlugins as apiNavigateToPlugins
import org.ide.lti.feature.workspace.api.navigateToRun as apiNavigateToRun
import org.ide.lti.feature.workspace.api.navigateToStudio as apiNavigateToStudio
import org.ide.lti.feature.workspace.api.parsePluginsRoute as apiParsePluginsRoute
import org.ide.lti.feature.workspace.api.parseRunRoute as apiParseRunRoute
import org.ide.lti.feature.workspace.api.parseStudioRoute as apiParseStudioRoute

typealias StudioRouteArgs = org.ide.lti.feature.workspace.api.StudioRouteArgs
typealias RunRouteArgs = org.ide.lti.feature.workspace.api.RunRouteArgs
typealias PluginsRouteArgs = org.ide.lti.feature.workspace.api.PluginsRouteArgs
typealias StudioRoutes = org.ide.lti.feature.workspace.api.StudioRoutes

fun buildStudioRoute(workspaceId: String, stageId: StageId? = null, objectId: String? = null): String =
    apiBuildStudioRoute(workspaceId, stageId, objectId)

fun buildStudioRoute(workspaceId: String, stageId: String?, objectId: String? = null): String =
    apiBuildStudioRoute(workspaceId, stageId, objectId)

fun parseStudioRoute(route: String): StudioRouteArgs? = apiParseStudioRoute(route)

fun buildRunRoute(workspaceId: String, runId: String): String = apiBuildRunRoute(workspaceId, runId)

fun parseRunRoute(route: String): RunRouteArgs? = apiParseRunRoute(route)

fun buildPluginsRoute(destination: String, workspaceId: String? = null, packageId: String? = null): String =
    apiBuildPluginsRoute(
        destination = destination,
        workspaceId = workspaceId,
        packageId = packageId,
    )

fun parsePluginsRoute(route: String): PluginsRouteArgs? = apiParsePluginsRoute(route)

fun NavController.navigateToStudio(
    workspaceId: String,
    stageId: StageId? = null,
    objectId: String? = null,
    navOptions: NavOptions? = null,
): Unit = apiNavigateToStudio(
    workspaceId = workspaceId,
    stageId = stageId,
    objectId = objectId,
    navOptions = navOptions,
)

fun NavController.navigateToRun(workspaceId: String, runId: String, navOptions: NavOptions? = null): Unit =
    apiNavigateToRun(workspaceId = workspaceId, runId = runId, navOptions = navOptions)

fun NavController.navigateToPlugins(
    destination: String,
    workspaceId: String? = null,
    packageId: String? = null,
    navOptions: NavOptions? = null,
): Unit = apiNavigateToPlugins(
    destination = destination,
    workspaceId = workspaceId,
    packageId = packageId,
    navOptions = navOptions,
)
