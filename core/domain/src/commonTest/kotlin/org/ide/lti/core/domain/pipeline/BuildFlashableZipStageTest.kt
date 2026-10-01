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

import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.model.target.MetadataPolicy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BuildFlashableZipStageTest {

    private val fx = PipelineFixture()
    private val stage = BuildFlashableZipStage()
    private val prevKey = "prev-build-cache-key"

    private fun fullContext(): StageContext {
        val base = fx.globalOtaContext()
        val values = mutableMapOf(
            RuntimeKeys.AVB_PUBKEY_SHA1 to "avb123  pubkey",
            BuildFlashableZipStage.PLATFORM_CERT_SHA1 to "plat123  cert",
            RuntimeKeys.RUN_TIMESTAMP to "1726056000",
            RuntimeKeys.RUN_DATE to "20260914",
            RuntimeKeys.BUILD_PROP to SimulatedExecutionPort.STOCK_BUILD_PROP,
            RuntimeKeys.ZIP_SHA256 to "zipsha256  rom.zip",
            RuntimeKeys.ZIP_SIZE to "1000000",
        )
        base.target.dynamicPartitions.forEach { p ->
            values[RuntimeKeys.imageSha256(p)] = "sha_$p  $p.img"
            values[RuntimeKeys.imageSize(p)] = "100000000"
            values[RuntimeKeys.partitionSize(p)] = "101000000"
            values[BuildFlashableZipStage.footeredSize(p)] = "101000000"
        }
        return base.copy(runtimeValues = values)
    }

    @Test
    fun `unsupported filesystem plans failing check UNSUPPORTED_FILESYSTEM`() {
        val ctx = fx.globalOtaContext()
        val invalidSnap = ctx.snapshot.copy(
            build = ctx.snapshot.build.copy(filesystem = "f2fs"),
        )
        val steps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val check = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("UNSUPPORTED_FILESYSTEM"), "Error must contain UNSUPPORTED_FILESYSTEM: $error")
    }

    @Test
    fun `level and alignment reach mkfs and packaging arguments when non null`() {
        val ctx = fullContext()
        val tunedSnap = ctx.snapshot.copy(
            build = ctx.snapshot.build.copy(
                level = 5,
                alignment = 4096,
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = tunedSnap))
        val tools = steps.filterIsInstance<PipelineStep.Tool>()

        // level in mkfs.erofs or zip
        val mkfsStep = tools.first { it.command.toolId == "mkfs.erofs" }
        val zipDeflatedStep = tools.first { it.label == "zip deflated members" }
        val levelReached = mkfsStep.command.args.any { it.contains("5") } ||
            zipDeflatedStep.command.args.contains("-5")
        assertTrue(levelReached, "Level 5 must reach mkfs or zip arguments")

        // alignment in lpmake and signapk
        val lpmakeStep = tools.first { it.command.toolId == "lpmake" }
        val signapkStep = tools.first { it.command.toolId == "signapk" }
        assertTrue(
            lpmakeStep.command.args.contains("--alignment") && lpmakeStep.command.args.contains("4096"),
            "Alignment must reach lpmake: ${lpmakeStep.command.args}",
        )
        assertTrue(
            signapkStep.command.args.contains("-a") && signapkStep.command.args.contains("4096"),
            "Alignment must reach signapk: ${signapkStep.command.args}",
        )
    }

    @Test
    fun `signPackage false skips signapk and writes UNSIGNED marker`() {
        val ctx = fullContext()
        val unsignedSnap = ctx.snapshot.copy(
            build = ctx.snapshot.build.copy(
                signing = ctx.snapshot.build.signing.copy(signPackage = false),
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = unsignedSnap))

        assertFalse(steps.any { it.label == "signapk" }, "signPackage=false must skip signapk")
        val markerStep = steps.filterIsInstance<PipelineStep.WriteFile>().firstOrNull {
            it.relPath == "out/package/UNSIGNED"
        }
        assertNotNull(markerStep, "Must plan WriteFile out/package/UNSIGNED when signPackage=false")

        val outputs = stage.outputs(ctx.copy(snapshot = unsignedSnap))
        assertTrue(outputs.contains("out/package/UNSIGNED"), "outputs must include UNSIGNED marker")
    }

    @Test
    fun `packagePolicy fields reach the updater script`() {
        val ctx = fullContext()
        val customPolicySnap = ctx.snapshot.copy(
            build = ctx.snapshot.build.copy(
                packagePolicy = ctx.snapshot.build.packagePolicy.copy(
                    excludeVbmeta = false,
                    recoverySystemMountPoint = "/custom_root",
                    metadataPolicy = MetadataPolicy.PACKAGE_ONLY,
                    bootSlots = listOf("a"),
                ),
            ),
        )
        val steps = stage.plan(ctx.copy(snapshot = customPolicySnap))
        val scriptStep = steps.filterIsInstance<PipelineStep.WriteFile>().first {
            it.relPath.endsWith("updater-script")
        }
        val scriptContent = scriptStep.content.decodeToString()

        assertTrue(scriptContent.contains("/custom_root"), "Mount point must reach updater-script")
        assertTrue(scriptContent.contains("boot_a"), "bootSlots must reach updater-script")
        assertFalse(scriptContent.contains("boot_b"), "Slot b must not be present when bootSlots has only a")
        assertTrue(scriptContent.contains("PACKAGE_ONLY"), "metadataPolicy must reach updater-script")
        assertTrue(scriptContent.contains("excludeVbmeta=false"), "excludeVbmeta must reach updater-script")
    }

    @Test
    fun `each build setting field changes the cache key`() {
        val ctx = fx.globalOtaContext()
        val baseKey = stage.computeCacheKey(ctx, prevKey)

        val mutatedFs = ctx.copy(
            snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(filesystem = "f2fs")),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedFs, prevKey))

        val mutatedComp = ctx.copy(
            snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(compression = "lz4")),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedComp, prevKey))

        val mutatedLevel = ctx.copy(
            snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(level = 4)),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedLevel, prevKey))

        val mutatedBlockSize = ctx.copy(
            snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(blockSize = 8192)),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedBlockSize, prevKey))

        val mutatedAlign = ctx.copy(
            snapshot = ctx.snapshot.copy(build = ctx.snapshot.build.copy(alignment = 4096)),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedAlign, prevKey))

        val mutatedBootSlots = ctx.copy(
            snapshot = ctx.snapshot.copy(
                build = ctx.snapshot.build.copy(
                    packagePolicy = ctx.snapshot.build.packagePolicy.copy(bootSlots = listOf("a")),
                ),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedBootSlots, prevKey))

        val mutatedExcludeVbmeta = ctx.copy(
            snapshot = ctx.snapshot.copy(
                build = ctx.snapshot.build.copy(
                    packagePolicy = ctx.snapshot.build.packagePolicy.copy(excludeVbmeta = false),
                ),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedExcludeVbmeta, prevKey))

        val mutatedMountPoint = ctx.copy(
            snapshot = ctx.snapshot.copy(
                build = ctx.snapshot.build.copy(
                    packagePolicy = ctx.snapshot.build.packagePolicy.copy(recoverySystemMountPoint = "/system"),
                ),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedMountPoint, prevKey))

        val mutatedMetadataPolicy = ctx.copy(
            snapshot = ctx.snapshot.copy(
                build = ctx.snapshot.build.copy(
                    packagePolicy = ctx.snapshot.build.packagePolicy.copy(metadataPolicy = MetadataPolicy.PACKAGE_ONLY),
                ),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedMetadataPolicy, prevKey))
    }
}
