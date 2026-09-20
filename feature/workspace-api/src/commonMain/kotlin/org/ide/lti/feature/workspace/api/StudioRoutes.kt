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
import org.ide.lti.core.model.run.StageId

/**
 * Parsed route arguments for the ROM Setup Studio route.
 */
public data class StudioRouteArgs(val workspaceId: String, val stageId: StageId? = null, val objectId: String? = null)

/**
 * Parsed route arguments for the Run view route.
 */
public data class RunRouteArgs(val workspaceId: String, val runId: String)

/**
 * Parsed route arguments for the Plugins host route.
 */
public data class PluginsRouteArgs(
    val destination: String,
    val workspaceId: String? = null,
    val packageId: String? = null,
)

/**
 * Pure route builders and argument parsers for host routes: Studio, Run, and Plugins.
 */
public object StudioRoutes {
    public const val STUDIO_BASE: String = "studio"
    public const val RUN_BASE: String = "run"
    public const val PLUGINS_BASE: String = "plugins"

    public const val WORKSPACE_ID_ARG: String = "workspaceId"
    public const val STAGE_ID_ARG: String = "stageId"
    public const val OBJECT_ID_ARG: String = "objectId"
    public const val RUN_ID_ARG: String = "runId"
    public const val PACKAGE_ID_ARG: String = "packageId"
    public const val DESTINATION_ARG: String = "destination"

    public const val STUDIO_PATTERN: String =
        "$STUDIO_BASE/{$WORKSPACE_ID_ARG}?$STAGE_ID_ARG={$STAGE_ID_ARG}&$OBJECT_ID_ARG={$OBJECT_ID_ARG}"
    public const val RUN_PATTERN: String = "$RUN_BASE/{$WORKSPACE_ID_ARG}/{$RUN_ID_ARG}"
    public const val PLUGINS_PATTERN: String =
        "$PLUGINS_BASE/{$DESTINATION_ARG}?$WORKSPACE_ID_ARG={$WORKSPACE_ID_ARG}&$PACKAGE_ID_ARG={$PACKAGE_ID_ARG}"

    public fun studio(workspaceId: String, stageId: StageId? = null, objectId: String? = null): String =
        buildStudioRoute(workspaceId, stageId, objectId)

    public fun studio(workspaceId: String, stageId: String?, objectId: String? = null): String =
        buildStudioRoute(workspaceId, stageId, objectId)

    public fun run(workspaceId: String, runId: String): String = buildRunRoute(workspaceId, runId)

    public fun plugins(destination: String, workspaceId: String? = null, packageId: String? = null): String =
        buildPluginsRoute(destination = destination, workspaceId = workspaceId, packageId = packageId)

    public fun parseStudio(route: String): StudioRouteArgs? = parseStudioRoute(route)

    public fun parseRun(route: String): RunRouteArgs? = parseRunRoute(route)

    public fun parsePlugins(route: String): PluginsRouteArgs? = parsePluginsRoute(route)
}

public fun buildStudioRoute(workspaceId: String, stageId: StageId? = null, objectId: String? = null): String =
    buildStudioRoute(workspaceId = workspaceId, stageId = stageId?.name, objectId = objectId)

public fun buildStudioRoute(workspaceId: String, stageId: String?, objectId: String? = null): String {
    require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
    return buildString {
        append(StudioRoutes.STUDIO_BASE)
        append("/")
        append(workspaceId)
        val queryParams = mutableListOf<String>()
        if (!stageId.isNullOrBlank()) {
            queryParams.add("${StudioRoutes.STAGE_ID_ARG}=$stageId")
        }
        if (!objectId.isNullOrBlank()) {
            queryParams.add("${StudioRoutes.OBJECT_ID_ARG}=$objectId")
        }
        if (queryParams.isNotEmpty()) {
            append("?")
            append(queryParams.joinToString("&"))
        }
    }
}

public fun parseStudioRoute(route: String): StudioRouteArgs? {
    val (pathPart, queryMap) = splitPathAndQuery(route.trim())
    val segments = pathPart.split('/').filter { it.isNotEmpty() }
    val workspaceId = requiredId(segments, 1, queryMap, StudioRoutes.WORKSPACE_ID_ARG)
    if (segments.getOrNull(0) != StudioRoutes.STUDIO_BASE || workspaceId == null) return null

    return StudioRouteArgs(
        workspaceId = workspaceId,
        stageId = queryMap[StudioRoutes.STAGE_ID_ARG]?.let(::parseStageId),
        objectId = queryMap[StudioRoutes.OBJECT_ID_ARG]?.ifBlank { null },
    )
}

public fun buildRunRoute(workspaceId: String, runId: String): String {
    require(workspaceId.isNotBlank()) { "workspaceId must not be blank" }
    require(runId.isNotBlank()) { "runId must not be blank" }
    return "${StudioRoutes.RUN_BASE}/$workspaceId/$runId"
}

public fun parseRunRoute(route: String): RunRouteArgs? {
    val (pathPart, queryMap) = splitPathAndQuery(route.trim())
    val segments = pathPart.split('/').filter { it.isNotEmpty() }
    val workspaceId = requiredId(segments, 1, queryMap, StudioRoutes.WORKSPACE_ID_ARG)
    val runId = requiredId(segments, 2, queryMap, StudioRoutes.RUN_ID_ARG)
    if (segments.getOrNull(0) != StudioRoutes.RUN_BASE || workspaceId == null || runId == null) return null

    return RunRouteArgs(workspaceId = workspaceId, runId = runId)
}

public fun buildPluginsRoute(destination: String, workspaceId: String? = null, packageId: String? = null): String {
    require(destination.isNotBlank()) { "destination must not be blank" }
    return buildString {
        append(StudioRoutes.PLUGINS_BASE)
        append("/")
        append(destination)
        val queryParams = mutableListOf<String>()
        if (!workspaceId.isNullOrBlank()) {
            queryParams.add("${StudioRoutes.WORKSPACE_ID_ARG}=$workspaceId")
        }
        if (!packageId.isNullOrBlank()) {
            queryParams.add("${StudioRoutes.PACKAGE_ID_ARG}=$packageId")
        }
        if (queryParams.isNotEmpty()) {
            append("?")
            append(queryParams.joinToString("&"))
        }
    }
}

public fun parsePluginsRoute(route: String): PluginsRouteArgs? {
    val (pathPart, queryMap) = splitPathAndQuery(route.trim())
    val segments = pathPart.split('/').filter { it.isNotEmpty() }
    val destination = requiredId(segments, 1, queryMap, StudioRoutes.DESTINATION_ARG)
    if (segments.getOrNull(0) != StudioRoutes.PLUGINS_BASE || destination == null) return null

    return PluginsRouteArgs(
        destination = destination,
        workspaceId = queryMap[StudioRoutes.WORKSPACE_ID_ARG]?.ifBlank { null },
        packageId = queryMap[StudioRoutes.PACKAGE_ID_ARG]?.ifBlank { null },
    )
}

public fun NavController.navigateToStudio(
    workspaceId: String,
    stageId: StageId? = null,
    objectId: String? = null,
    navOptions: NavOptions? = null,
) {
    navigate(buildStudioRoute(workspaceId, stageId, objectId), navOptions)
}

public fun NavController.navigateToRun(workspaceId: String, runId: String, navOptions: NavOptions? = null) {
    navigate(buildRunRoute(workspaceId, runId), navOptions)
}

public fun NavController.navigateToPlugins(
    destination: String,
    workspaceId: String? = null,
    packageId: String? = null,
    navOptions: NavOptions? = null,
) {
    navigate(buildPluginsRoute(destination, workspaceId, packageId), navOptions)
}

private fun segmentOrQuery(segments: List<String>, index: Int, queryMap: Map<String, String>, key: String): String? =
    segments.getOrNull(index) ?: queryMap[key]

private fun requiredId(segments: List<String>, index: Int, queryMap: Map<String, String>, key: String): String? =
    segmentOrQuery(segments, index, queryMap, key)?.ifBlank { null }

private fun splitPathAndQuery(route: String): Pair<String, Map<String, String>> {
    val questionIdx = route.indexOf('?')
    if (questionIdx < 0) return route to emptyMap()

    val pathPart = route.substring(0, questionIdx)
    val queryPart = route.substring(questionIdx + 1)
    val map = queryPart.split('&')
        .filter { it.isNotEmpty() }
        .associate { pair ->
            val equalsIdx = pair.indexOf('=')
            if (equalsIdx < 0) pair to "" else pair.substring(0, equalsIdx) to pair.substring(equalsIdx + 1)
        }
    return pathPart to map
}

private fun parseStageId(value: String): StageId? {
    val trimmed = value.trim()
    return StageId.entries.firstOrNull {
        it.name.equals(trimmed, ignoreCase = true) ||
            it.name.replace('_', '-').equals(trimmed, ignoreCase = true)
    }
}
