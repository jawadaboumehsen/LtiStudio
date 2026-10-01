/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.domain.pipeline.stages.GenerateOtaManifestStage
import kotlin.test.Test
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GenerateOtaManifestStageTest {

    private val fx = PipelineFixture()
    private val stage = GenerateOtaManifestStage()
    private val prevKey = "prev-manifest-cache-key"

    private fun capturedContext(): StageContext {
        val base = fx.globalOtaContext()
        return base.copy(
            runtimeValues = mapOf(
                RuntimeKeys.ZIP_SHA256 to "c2b647f1146f8c79a29e4726bfcf1a58a7da09f193758bdf214ff94e0192e4ab  rom.zip",
                RuntimeKeys.ZIP_SIZE to "2500000000",
                RuntimeKeys.RUN_DATE to "20260914",
                "package.build_info" to "ro.lti.timestamp=1726056000\nro.build.version.incremental=20260911.120000\n",
                "package.changelog" to "Stock packaged changelog\n",
            ),
        )
    }

    @Test
    fun `channel and changelog flow into the generated manifest`() {
        val ctx = capturedContext()
        val customReleaseSnap = ctx.snapshot.copy(
            release = ctx.snapshot.release.copy(
                channel = "beta",
                changelog = "Custom release changelog notes",
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = customReleaseSnap))
        val manifestStep = steps.filterIsInstance<PipelineStep.WriteFile>().firstOrNull {
            it.relPath == "out/manifest.json"
        }
        assertNotNull(manifestStep, "Must plan WriteFile out/manifest.json when runtime values are captured")

        val manifestJson = manifestStep.content.decodeToString()
        assertTrue(manifestJson.contains("\"channel\": \"beta\""), "channel must reach manifest: $manifestJson")
        assertTrue(
            manifestJson.contains("Custom release changelog notes"),
            "changelog must reach manifest: $manifestJson",
        )
    }

    @Test
    fun `empty changelog falls back to packaged changelog text`() {
        val ctx = capturedContext()
        val defaultReleaseSnap = ctx.snapshot.copy(
            release = ctx.snapshot.release.copy(
                changelog = "",
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = defaultReleaseSnap))
        val manifestStep = steps.filterIsInstance<PipelineStep.WriteFile>().first {
            it.relPath == "out/manifest.json"
        }
        val manifestJson = manifestStep.content.decodeToString()
        assertTrue(manifestJson.contains("Stock packaged changelog"))
    }

    @Test
    fun `each release setting field changes the cache key`() {
        val ctx = fx.globalOtaContext()
        val baseKey = stage.computeCacheKey(ctx, prevKey)

        val mutatedChannel = ctx.copy(
            snapshot = ctx.snapshot.copy(
                release = ctx.snapshot.release.copy(channel = "beta"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedChannel, prevKey))

        val mutatedChangelog = ctx.copy(
            snapshot = ctx.snapshot.copy(
                release = ctx.snapshot.release.copy(changelog = "New changelog"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedChangelog, prevKey))

        val mutatedOtaUrl = ctx.copy(
            snapshot = ctx.snapshot.copy(
                release = ctx.snapshot.release.copy(otaBaseUrl = "https://updates.new.org"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedOtaUrl, prevKey))
    }
}
