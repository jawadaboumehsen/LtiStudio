/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.setup.tools

import org.ide.lti.core.domain.setup.ToolGroupId
import org.ide.lti.core.domain.tools.GroupSetupResult
import org.ide.lti.core.domain.tools.GroupState
import org.ide.lti.core.domain.tools.InstalledVersion
import org.ide.lti.core.domain.tools.OperationKind
import org.ide.lti.core.domain.tools.OperationPhase
import org.ide.lti.core.domain.tools.RecipeSummary
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.domain.tools.SetupToolsSummary
import org.ide.lti.core.domain.tools.ToolError
import org.ide.lti.core.domain.tools.ToolOperation
import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ToolsPresentationMapperTest {
    @Test
    fun `maps ready group state to success presentation`() {
        val version =
            InstalledVersion(
                group = ToolGroupId("erofs-utils"),
                id = "v1-id",
                label = "v1.8.1",
                source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = true,
            )
        val state =
            GroupState(
                group = ToolGroupId("erofs-utils"),
                current = version,
                installed = listOf(version),
            )

        val presentation = ToolsPresentationMapper.mapRow(state)
        assertEquals("erofs-utils", presentation.groupId)
        assertEquals("v1-id", presentation.inUseLabel)
        assertEquals("Ready", presentation.statusLabel)
        assertEquals(ToolRowStatusKind.Ready, presentation.statusKind)
        assertFalse(presentation.canCancel)
        assertEquals(null, presentation.actionLabel)
    }

    @Test
    fun `maps picked group state to include (yours) indicator`() {
        val version =
            InstalledVersion(
                group = ToolGroupId("erofs-utils"),
                id = "v1-id",
                label = "v1.8.1",
                source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = true,
            )
        val state =
            GroupState(
                group = ToolGroupId("erofs-utils"),
                current = version,
                selection = org.ide.lti.core.domain.tools.Selection(version.id, version.source),
                installed = listOf(version),
            )

        val presentation = ToolsPresentationMapper.mapRow(state)
        assertEquals("v1-id (yours)", presentation.inUseLabel)
    }

    @Test
    fun `maps needs repair group state to status Needs repair with action Repair`() {
        val version =
            InstalledVersion(
                group = ToolGroupId("erofs-utils"),
                id = "v1-id",
                label = "v1.8.1",
                source = ResolvedSource.Git("https://example.com", ToolRef.Commit("1" * 40), "1" * 40),
                recipe = RecipeSummary("cmake", 1),
                outputs = emptyList(),
                builtAt = "2026-10-01",
                intact = false,
            )
        val state =
            GroupState(
                group = ToolGroupId("erofs-utils"),
                current = version,
                installed = listOf(version),
            )

        val presentation = ToolsPresentationMapper.mapRow(state)
        assertEquals("erofs-utils", presentation.groupId)
        assertEquals("v1-id", presentation.inUseLabel)
        assertEquals("Needs repair", presentation.statusLabel)
        assertEquals(ToolRowStatusKind.NeedsRepair, presentation.statusKind)
        assertEquals("Repair", presentation.actionLabel)
        assertFalse(presentation.canCancel)
    }

    @Test
    fun `maps active operation to installing presentation with cancel option`() {
        val op =
            ToolOperation(
                opId = "op-1",
                group = ToolGroupId("img2sdat"),
                kind = OperationKind.Install,
                phase = OperationPhase.Building,
            )
        val state =
            GroupState(
                group = ToolGroupId("img2sdat"),
                current = null,
                installed = emptyList(),
                operation = op,
            )

        val presentation = ToolsPresentationMapper.mapRow(state)
        assertEquals("img2sdat", presentation.groupId)
        assertEquals("Installing", presentation.statusLabel)
        assertEquals("Building…", presentation.phaseLabel)
        assertEquals(ToolRowStatusKind.Installing, presentation.statusKind)
        assertTrue(presentation.canCancel)
    }

    @Test
    fun `maps partial setup summary to summary row presentation`() {
        val summary =
            SetupToolsSummary(
                groupResults =
                mapOf(
                    ToolGroupId("android-tools") to GroupSetupResult.Installed("v1"),
                    ToolGroupId("erofs-utils") to GroupSetupResult.Reused("v2"),
                    ToolGroupId("img2sdat") to GroupSetupResult.Failed(ToolError.BuildFailed("clang error")),
                    ToolGroupId("apktool") to GroupSetupResult.NotAttempted(ToolError.BuildFailed("blocked")),
                ),
            )

        val pres = ToolsPresentationMapper.mapSummary(summary)
        assertFalse(pres.isComplete)
        assertTrue(pres.hasFailures)
        assertTrue(pres.canRetry)
        assertEquals("2 ready · 1 failed · 1 not attempted", pres.summaryText)
    }
}

private operator fun String.times(n: Int): String = repeat(n)
