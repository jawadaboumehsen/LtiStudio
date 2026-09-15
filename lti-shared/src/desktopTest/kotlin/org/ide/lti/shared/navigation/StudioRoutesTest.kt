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

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class StudioRoutesTest {

    @Test
    fun studioRouteBuildAndParseRoundTripWithAllArguments() {
        val built = StudioRoutes.studio(
            workspaceId = "workspace-alpha",
            stageId = StageId.DEBLOAT,
            objectId = "inventory",
        )
        assertEquals("studio/workspace-alpha?stageId=DEBLOAT&objectId=inventory", built)

        val parsed = StudioRoutes.parseStudio(built)
        assertNotNull(parsed)
        assertEquals("workspace-alpha", parsed.workspaceId)
        assertEquals(StageId.DEBLOAT, parsed.stageId)
        assertEquals("inventory", parsed.objectId)
    }

    @Test
    fun studioRouteBuildAndParseRoundTripWithAbsentOptionalArguments() {
        // workspaceId only
        val workspaceOnly = StudioRoutes.studio(workspaceId = "ws-minimal")
        assertEquals("studio/ws-minimal", workspaceOnly)

        val parsedWorkspaceOnly = StudioRoutes.parseStudio(workspaceOnly)
        assertNotNull(parsedWorkspaceOnly)
        assertEquals("ws-minimal", parsedWorkspaceOnly.workspaceId)
        assertNull(parsedWorkspaceOnly.stageId)
        assertNull(parsedWorkspaceOnly.objectId)

        // workspaceId + stageId only
        val withStage = StudioRoutes.studio(workspaceId = "ws-stage", stageId = StageId.FIRMWARE_ACQUISITION)
        assertEquals("studio/ws-stage?stageId=FIRMWARE_ACQUISITION", withStage)

        val parsedWithStage = StudioRoutes.parseStudio(withStage)
        assertNotNull(parsedWithStage)
        assertEquals("ws-stage", parsedWithStage.workspaceId)
        assertEquals(StageId.FIRMWARE_ACQUISITION, parsedWithStage.stageId)
        assertNull(parsedWithStage.objectId)

        // workspaceId + objectId only
        val withObject = StudioRoutes.studio(workspaceId = "ws-obj", stageId = null as StageId?, objectId = "source")
        assertEquals("studio/ws-obj?objectId=source", withObject)

        val parsedWithObject = StudioRoutes.parseStudio(withObject)
        assertNotNull(parsedWithObject)
        assertEquals("ws-obj", parsedWithObject.workspaceId)
        assertNull(parsedWithObject.stageId)
        assertEquals("source", parsedWithObject.objectId)
    }

    @Test
    fun studioRouteParsesCaseInsensitiveAndDashedStageNames() {
        val parsedLower = StudioRoutes.parseStudio("studio/ws-1?stageId=debloat")
        assertNotNull(parsedLower)
        assertEquals(StageId.DEBLOAT, parsedLower.stageId)

        val parsedDashed = StudioRoutes.parseStudio("studio/ws-1?stageId=firmware-extraction")
        assertNotNull(parsedDashed)
        assertEquals(StageId.FIRMWARE_EXTRACTION, parsedDashed.stageId)
    }

    @Test
    fun studioRouteParseRejectsInvalidRoutes() {
        assertNull(StudioRoutes.parseStudio(""))
        assertNull(StudioRoutes.parseStudio("workspace"))
        assertNull(StudioRoutes.parseStudio("run/ws-1/run-1"))
        assertNull(StudioRoutes.parseStudio("studio/"))
    }

    @Test
    fun runRouteBuildAndParseRoundTrip() {
        val built = StudioRoutes.run(workspaceId = "ws-run", runId = "run-20260915-001")
        assertEquals("run/ws-run/run-20260915-001", built)

        val parsed = StudioRoutes.parseRun(built)
        assertNotNull(parsed)
        assertEquals("ws-run", parsed.workspaceId)
        assertEquals("run-20260915-001", parsed.runId)
    }

    @Test
    fun runRouteParseRejectsInvalidRoutes() {
        assertNull(StudioRoutes.parseRun(""))
        assertNull(StudioRoutes.parseRun("run/"))
        assertNull(StudioRoutes.parseRun("run/only-workspace"))
        assertNull(StudioRoutes.parseRun("studio/ws-1"))
    }

    @Test
    fun pluginsRouteBuildAndParseRoundTripWithAllArguments() {
        val built = StudioRoutes.plugins(
            destination = "marketplace",
            workspaceId = "ws-plugins",
            packageId = "org.ide.lti.plugin.magisk",
        )
        assertEquals("plugins/marketplace?workspaceId=ws-plugins&packageId=org.ide.lti.plugin.magisk", built)

        val parsed = StudioRoutes.parsePlugins(built)
        assertNotNull(parsed)
        assertEquals("marketplace", parsed.destination)
        assertEquals("ws-plugins", parsed.workspaceId)
        assertEquals("org.ide.lti.plugin.magisk", parsed.packageId)
    }

    @Test
    fun pluginsRouteBuildAndParseRoundTripWithAbsentOptionalArguments() {
        // destination only
        val destOnly = StudioRoutes.plugins(destination = "installed")
        assertEquals("plugins/installed", destOnly)

        val parsedDestOnly = StudioRoutes.parsePlugins(destOnly)
        assertNotNull(parsedDestOnly)
        assertEquals("installed", parsedDestOnly.destination)
        assertNull(parsedDestOnly.workspaceId)
        assertNull(parsedDestOnly.packageId)

        // destination + workspaceId only
        val withWs = StudioRoutes.plugins(destination = "catalog", workspaceId = "ws-test")
        assertEquals("plugins/catalog?workspaceId=ws-test", withWs)

        val parsedWithWs = StudioRoutes.parsePlugins(withWs)
        assertNotNull(parsedWithWs)
        assertEquals("catalog", parsedWithWs.destination)
        assertEquals("ws-test", parsedWithWs.workspaceId)
        assertNull(parsedWithWs.packageId)

        // destination + packageId only
        val withPkg = StudioRoutes.plugins(destination = "details", packageId = "pkg-123")
        assertEquals("plugins/details?packageId=pkg-123", withPkg)

        val parsedWithPkg = StudioRoutes.parsePlugins(withPkg)
        assertNotNull(parsedWithPkg)
        assertEquals("details", parsedWithPkg.destination)
        assertNull(parsedWithPkg.workspaceId)
        assertEquals("pkg-123", parsedWithPkg.packageId)
    }

    @Test
    fun pluginsRouteParseRejectsInvalidRoutes() {
        assertNull(StudioRoutes.parsePlugins(""))
        assertNull(StudioRoutes.parsePlugins("plugins/"))
        assertNull(StudioRoutes.parsePlugins("studio/ws-1"))
    }

    // feature:workspace cannot depend on this module (the dependency runs the other way), so its
    // studioScreen() composable registration carries a second, independently-typed copy of this exact
    // pattern string. lti-shared is the only module that can see both - if they ever diverge, the
    // composable() a user navigates into stops matching the route navigateToStudio() actually builds.
    @Test
    fun studioRoutePatternMatchesFeatureWorkspacesIndependentCopy() {
        assertEquals(
            org.ide.lti.feature.workspace.studio.STUDIO_ROUTE_PATTERN,
            StudioRoutes.STUDIO_PATTERN,
        )
    }

    @Test
    fun pluginsRoutePatternMatchesFeaturePluginsIndependentCopy() {
        assertEquals(
            org.ide.lti.feature.plugins.PLUGINS_ROUTE_PATTERN,
            StudioRoutes.PLUGINS_PATTERN,
        )
    }
}
