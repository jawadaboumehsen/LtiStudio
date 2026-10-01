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

public typealias StudioRouteArgs = org.ide.lti.feature.workspace.api.StudioRouteArgs
public typealias RunRouteArgs = org.ide.lti.feature.workspace.api.RunRouteArgs
public typealias PluginsRouteArgs = org.ide.lti.feature.workspace.api.PluginsRouteArgs
public typealias StudioRoutes = org.ide.lti.feature.workspace.api.StudioRoutes

public fun buildStudioRoute(workspaceId: String, stageId: StageId? = null, objectId: String? = null): String =
    apiBuildStudioRoute(workspaceId, stageId, objectId)

public fun buildStudioRoute(workspaceId: String, stageId: String?, objectId: String? = null): String =
    apiBuildStudioRoute(workspaceId, stageId, objectId)

public fun parseStudioRoute(route: String): StudioRouteArgs? = apiParseStudioRoute(route)

public fun buildRunRoute(workspaceId: String, runId: String): String = apiBuildRunRoute(workspaceId, runId)

public fun parseRunRoute(route: String): RunRouteArgs? = apiParseRunRoute(route)

public fun buildPluginsRoute(destination: String, workspaceId: String? = null, packageId: String? = null): String =
    apiBuildPluginsRoute(
        destination = destination,
        workspaceId = workspaceId,
        packageId = packageId,
    )

public fun parsePluginsRoute(route: String): PluginsRouteArgs? = apiParsePluginsRoute(route)

public fun NavController.navigateToStudio(
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

public fun NavController.navigateToRun(workspaceId: String, runId: String, navOptions: NavOptions? = null): Unit =
    apiNavigateToRun(workspaceId = workspaceId, runId = runId, navOptions = navOptions)

public fun NavController.navigateToPlugins(
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
