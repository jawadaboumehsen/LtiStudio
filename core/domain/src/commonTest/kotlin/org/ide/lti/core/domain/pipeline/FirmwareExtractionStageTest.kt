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

import org.ide.lti.core.domain.pipeline.stages.FirmwareExtractionStage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FirmwareExtractionStageTest {

    private val fx = PipelineFixture()
    private val stage = FirmwareExtractionStage()
    private val prevKey = "prev-extraction-cache-key"

    @Test
    fun `happy default preserves todays plan step labels for global ota`() {
        val ctx = fx.globalOtaContext()
        val defaultSteps = stage.plan(ctx)

        val labels = defaultSteps.map { it.label }
        val expectedLabels = listOf(
            "mkdir extracted",
            "unzip payload",
            "payload-dumper-go",
            "rm payload",
            "detect filesystem odm",
            "detect filesystem product",
            "detect filesystem system",
            "detect filesystem system_dlkm",
            "detect filesystem system_ext",
            "detect filesystem vendor",
            "detect filesystem vendor_dlkm",
            "mkdir mount odm",
            "mount odm",
            "stat dump odm",
            "selinux dump odm",
            "clear tree odm",
            "copy tree odm",
            "drop lost+found odm",
            "unmount odm",
            "mkdir mount product",
            "mount product",
            "stat dump product",
            "selinux dump product",
            "clear tree product",
            "copy tree product",
            "drop lost+found product",
            "unmount product",
            "mkdir mount system",
            "mount system",
            "stat dump system",
            "selinux dump system",
            "clear tree system",
            "copy tree system",
            "drop lost+found system",
            "unmount system",
            "mkdir mount system_dlkm",
            "mount system_dlkm",
            "stat dump system_dlkm",
            "selinux dump system_dlkm",
            "clear tree system_dlkm",
            "copy tree system_dlkm",
            "drop lost+found system_dlkm",
            "unmount system_dlkm",
            "mkdir mount system_ext",
            "mount system_ext",
            "stat dump system_ext",
            "selinux dump system_ext",
            "clear tree system_ext",
            "copy tree system_ext",
            "drop lost+found system_ext",
            "unmount system_ext",
            "mkdir mount vendor",
            "mount vendor",
            "stat dump vendor",
            "selinux dump vendor",
            "clear tree vendor",
            "copy tree vendor",
            "drop lost+found vendor",
            "unmount vendor",
            "mkdir mount vendor_dlkm",
            "mount vendor_dlkm",
            "stat dump vendor_dlkm",
            "selinux dump vendor_dlkm",
            "clear tree vendor_dlkm",
            "copy tree vendor_dlkm",
            "drop lost+found vendor_dlkm",
            "unmount vendor_dlkm",
            "avbtool info boot",
            "avbtool info init_boot",
            "avbtool info vendor_boot",
            "avbtool info dtbo",
            "unpack_bootimg boot",
            "unpack_bootimg init_boot",
            "unpack_bootimg vendor_boot",
            "read stock build.prop",
        )
        assertEquals(expectedLabels, labels)
        assertFalse(labels.any { it.contains("clean") })
    }

    @Test
    fun `unsupported extraction adapter plans failing check UNSUPPORTED_EXTRACTION_ADAPTER`() {
        val ctx = fx.globalOtaContext()
        val mutatedSnap = ctx.snapshot.copy(
            extraction = ctx.snapshot.extraction.copy(adapter = "UNKNOWN_TOOL"),
        )
        val steps = stage.plan(ctx.copy(snapshot = mutatedSnap))
        val failingCheck = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()

        assertNotNull(failingCheck, "Must plan a check on unsupported adapter")
        val error = failingCheck.verify(emptyMap())
        assertNotNull(error)
        assertTrue(
            error.contains("UNSUPPORTED_EXTRACTION_ADAPTER"),
            "Error message must contain UNSUPPORTED_EXTRACTION_ADAPTER: $error",
        )
    }

    @Test
    fun `explicit adapter selects that extraction tool`() {
        // Explicit lpunpack selects raw extraction steps even for OTA archive source kind
        val otaCtx = fx.globalOtaContext()
        val lpunpackSnap = otaCtx.snapshot.copy(
            extraction = otaCtx.snapshot.extraction.copy(adapter = "lpunpack"),
        )
        val lpunpackSteps = stage.plan(otaCtx.copy(snapshot = lpunpackSnap))
        assertTrue(lpunpackSteps.any { it.label.contains("unzip raw archive") })
        assertFalse(lpunpackSteps.any { it.label.contains("payload-dumper-go") })

        // Explicit payload-dumper-go selects ota steps even for RAW archive source kind
        val rawCtx = fx.chinaRawContext()
        val payloadSnap = rawCtx.snapshot.copy(
            extraction = rawCtx.snapshot.extraction.copy(adapter = "payload-dumper-go"),
        )
        val payloadSteps = stage.plan(rawCtx.copy(snapshot = payloadSnap))
        assertTrue(payloadSteps.any { it.label.contains("payload-dumper-go") })
        assertFalse(payloadSteps.any { it.label.contains("unzip raw archive") })
    }

    @Test
    fun `narrowing dynamicPartitions and bootPartitions changes planned steps and outputs`() {
        val ctx = fx.globalOtaContext()
        val narrowedSnap = ctx.snapshot.copy(
            extraction = ctx.snapshot.extraction.copy(
                dynamicPartitions = listOf("system"),
                bootPartitions = listOf("boot"),
            ),
        )
        val narrowedCtx = ctx.copy(snapshot = narrowedSnap)
        val steps = stage.plan(narrowedCtx)
        val labels = steps.map { it.label }

        assertTrue(labels.contains("mount system"))
        assertFalse(labels.contains("mount vendor"))
        assertFalse(labels.contains("mount odm"))
        assertTrue(labels.contains("avbtool info boot"))
        assertFalse(labels.contains("avbtool info dtbo"))

        val outputs = stage.outputs(narrowedCtx)
        assertEquals(
            listOf(
                "firmware/extracted/system.img",
                "firmware/extracted/fs_config-system",
                "firmware/extracted/file_context-system",
                "firmware/extracted/boot.img",
            ),
            outputs,
        )
    }

    @Test
    fun `partition not on target plans failing check PARTITION_NOT_ON_TARGET`() {
        val ctx = fx.globalOtaContext()
        val invalidDynamic = ctx.snapshot.copy(
            extraction = ctx.snapshot.extraction.copy(dynamicPartitions = listOf("nonexistent_part")),
        )
        val stepsDynamic = stage.plan(ctx.copy(snapshot = invalidDynamic))
        val checkDynamic = stepsDynamic.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(checkDynamic)
        val errorDynamic = checkDynamic.verify(emptyMap())
        assertNotNull(errorDynamic)
        assertTrue(errorDynamic.contains("PARTITION_NOT_ON_TARGET"))

        val invalidBoot = ctx.snapshot.copy(
            extraction = ctx.snapshot.extraction.copy(bootPartitions = listOf("nonexistent_boot")),
        )
        val stepsBoot = stage.plan(ctx.copy(snapshot = invalidBoot))
        val checkBoot = stepsBoot.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(checkBoot)
        val errorBoot = checkBoot.verify(emptyMap())
        assertNotNull(errorBoot)
        assertTrue(errorBoot.contains("PARTITION_NOT_ON_TARGET"))
    }

    @Test
    fun `extraction slot override is used in extraction steps when valid and rejects invalid slot`() {
        val chinaCtx = fx.chinaRawContext().copy(
            runtimeValues = mapOf(RuntimeKeys.SUPER_MAGIC to "3aff26ed"),
        )
        val validSlotSnap = chinaCtx.snapshot.copy(
            extraction = chinaCtx.snapshot.extraction.copy(slot = "_b"),
        )
        val steps = stage.plan(chinaCtx.copy(snapshot = validSlotSnap))
        val lpunpackStep = steps.filterIsInstance<PipelineStep.Tool>().first { it.command.toolId == "lpunpack" }
        assertTrue(
            lpunpackStep.command.args.contains("-p") && lpunpackStep.command.args.contains("system_b"),
            "lpunpack must use slot _b: ${lpunpackStep.command.args}",
        )

        val invalidSlotSnap = chinaCtx.snapshot.copy(
            extraction = chinaCtx.snapshot.extraction.copy(slot = "_z"),
        )
        val invalidSteps = stage.plan(chinaCtx.copy(snapshot = invalidSlotSnap))
        val check = invalidSteps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("UNSUPPORTED_SLOT"))
    }

    @Test
    fun `reuseVerifiedExtraction false plans explicit clean step`() {
        val ctx = fx.globalOtaContext()
        val noReuseSnap = ctx.snapshot.copy(
            extraction = ctx.snapshot.extraction.copy(reuseVerifiedExtraction = false),
        )
        val steps = stage.plan(ctx.copy(snapshot = noReuseSnap))
        val cleanStep = steps.firstOrNull { it.label == "clean extracted" }
        assertNotNull(cleanStep, "Must plan explicit clean step when reuseVerifiedExtraction is false")
        assertTrue(cleanStep is PipelineStep.Tool)
        assertEquals("rm", cleanStep.command.toolId)
        assertTrue(cleanStep.command.args.contains("-rf"))
    }

    @Test
    fun `each extraction setting field changes the cache key`() {
        val ctx = fx.globalOtaContext()
        val baseKey = stage.computeCacheKey(ctx, prevKey)

        val mutatedAdapter = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(adapter = "lpunpack"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedAdapter, prevKey))

        val mutatedDynamic = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(dynamicPartitions = listOf("system")),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedDynamic, prevKey))

        val mutatedBoot = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(bootPartitions = listOf("boot")),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedBoot, prevKey))

        val mutatedSlot = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(slot = "_b"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedSlot, prevKey))

        val mutatedReuse = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(reuseVerifiedExtraction = false),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedReuse, prevKey))
    }

    @Test
    fun archivePackageManifestPlansArchiveExtractionSteps() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(
                RuntimeKeys.PACKAGE_MANIFEST to """{"format":"SevenZ","partitions":[]}""",
            ),
        )
        val steps = stage.plan(ctx)
        val toolStep = steps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.label == "extract archive (SevenZ)" }
        assertNotNull(toolStep)
        assertEquals("7zz", toolStep.command.toolId)
        assertTrue(toolStep.command.args.contains("x"))
    }

    @Test
    fun writesExtractImagesAndToolkitFactsWhenBuildPropRead() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(
                RuntimeKeys.BUILD_PROP to "ro.build.version.release=15\nro.product.model=RedMagic",
            ),
        )
        val steps = stage.plan(ctx)

        val writeSteps = steps.filterIsInstance<PipelineStep.WriteFile>()
        val extractImagesWrite = writeSteps.firstOrNull { it.relPath.endsWith("extract.images.json") }
        val basicInfoWrite = writeSteps.firstOrNull { it.relPath == "facts/basic_info.json" }
        val partitionInfoWrite = writeSteps.firstOrNull { it.relPath == "facts/partition_info.json" }

        assertNotNull(extractImagesWrite)
        assertNotNull(basicInfoWrite)
        assertNotNull(partitionInfoWrite)

        assertTrue(extractImagesWrite.content.decodeToString().contains("system.img"))
        assertTrue(basicInfoWrite.content.decodeToString().contains(ctx.target.id))
        assertTrue(partitionInfoWrite.content.decodeToString().contains("system"))

        val catStep = steps.filterIsInstance<PipelineStep.Tool>()
            .firstOrNull { it.resultKey == RuntimeKeys.EXTRACT_IMAGES }
        assertNotNull(catStep)
        assertEquals("read extract.images", catStep.label)
    }

    @Test
    fun refusesIncrementalManifestImmediately() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(
                RuntimeKeys.PACKAGE_MANIFEST to """{
                    "format": "AndroidPayload",
                    "refusal": {
                        "cause": "The package is an incremental update.",
                        "item": "payload.bin",
                        "nextStep": "Import the full update instead."
                    }
                }
                """.trimIndent(),
            ),
        )
        val steps = stage.plan(ctx)
        val refusal = steps.single() as PipelineStep.Check
        assertEquals("refuse payload.bin", refusal.label)
        assertTrue(refusal.verify(emptyMap())!!.contains("The package is an incremental update."))
    }

    @Test
    fun androidPayloadManifestStreamsDirectlyWithoutUnzip() {
        val ctx = fx.globalOtaContext().copy(
            runtimeValues = mapOf(
                RuntimeKeys.PACKAGE_MANIFEST to """{"format":"AndroidPayload","partitions":[]}""",
            ),
        )
        val steps = stage.plan(ctx)
        val toolSteps = steps.filterIsInstance<PipelineStep.Tool>()

        assertFalse(toolSteps.any { it.label == "unzip payload" })
        assertFalse(toolSteps.any { it.label == "rm payload" })

        val payloadDumperStep = toolSteps.firstOrNull { it.label == "payload-dumper-go" }
        assertNotNull(payloadDumperStep)
        assertTrue(payloadDumperStep.command.args.last().endsWith("NP05J_GB.zip"))
    }

    @Test
    fun `a replaced partition gets the users image after the firmware is unpacked, checked by its hash`() {
        val ctx = fx.globalOtaContext()
        val sha = "a".repeat(64)
        val replaced = ctx.copy(
            snapshot = ctx.snapshot.copy(
                extraction = ctx.snapshot.extraction.copy(replacementImages = mapOf("product" to sha, "nope" to sha)),
            ),
        )
        val steps = stage.plan(replaced)
        val labels = steps.map { it.label }
        val replace = labels.indexOf("replace product")
        assertTrue(replace > labels.indexOf("hash replacement product"), "hash first: $labels")
        assertTrue(labels.none { it.endsWith("nope") }, "only partitions of the device")
        val copy = steps.filterIsInstance<PipelineStep.Tool>().single { it.label == "replace product" }
        assertEquals(
            listOf("-f", ctx.abs("firmware/replacements/product.img"), "${ctx.abs("firmware/extracted")}/product.img"),
            copy.command.args,
        )
        val check = steps.filterIsInstance<PipelineStep.Check>().single { it.label == "check replacement product" }
        assertEquals(
            null,
            check.verify(mapOf("firmware.product.replacement.sha256" to "$sha  /ws/firmware/replacements/product.img")),
        )
        assertTrue(
            check.verify(
                mapOf("firmware.product.replacement.sha256" to "b".repeat(64)),
            )!!.contains("REPLACEMENT_CHANGED"),
        )
    }

    @Test
    fun `an ext4 partition mounts with fuse2fs and an F2FS one is dumped by dump f2fs`() {
        val ctx = fx.globalOtaContext()
        val partitions = ctx.target.dynamicPartitions
        val ext4 = "00".repeat(56) + "53ef" + "00".repeat(6)
        val erofs = "e2e1f5e0" + "00".repeat(60)
        val f2fs = "1020f5f2" + "00".repeat(60)
        val (first, rest) = partitions.first() to partitions.drop(1)
        val values = mapOf("firmware.$first.fstype" to ext4) + rest.associate { "firmware.$it.fstype" to erofs }
        val mounts = stage.plan(ctx.copy(runtimeValues = values)).filterIsInstance<PipelineStep.Tool>()
            .filter { it.label.startsWith("mount ") }
        val img = "${ctx.abs("firmware/extracted")}/$first.img"
        val mnt = "${ctx.abs("firmware/extracted")}/${first}_mnt"
        assertEquals("fuse2fs", mounts.single { it.label == "mount $first" }.command.toolId)
        assertEquals(listOf(img, mnt, "-o", "ro,fakeroot"), mounts.single { it.label == "mount $first" }.command.args)
        assertTrue(mounts.filter { it.label != "mount $first" }.all { it.command.toolId == "erofsfuse" })

        // F2FS has no FUSE driver: the tree is dumped straight into firmware/extracted/<p> inside fakeroot.
        val tree = "${ctx.abs("firmware/extracted")}/$first"
        val withF2fs = values + ("firmware.$first.fstype" to f2fs)
        val f2fsSteps = stage.plan(ctx.copy(runtimeValues = withF2fs))
        val dump = f2fsSteps.filterIsInstance<PipelineStep.Tool>().single { it.label == "dump $first" }.command
        assertEquals("fakeroot", dump.toolId)
        assertEquals(listOf("dump.f2fs", img, tree, "keep"), dump.args.takeLast(4))
        assertTrue(f2fsSteps.none { it.label == "mount $first" || it.label == "check filesystems" })

        // The readback becomes the sidecars; a symlink has no mode on Linux, so it is written as 777.
        val readback = "0 0 755 $tree\n0 0 644 $tree/a.txt\n0 0 L $tree/link\n#--\n" +
            listOf(tree, "$tree/a.txt", "$tree/link").joinToString("\n") {
                "# file: $it\nsecurity.selinux=\"u:object_r:system_file:s0\"\n"
            }
        val written = stage.plan(ctx.copy(runtimeValues = withF2fs + (RuntimeKeys.statLines(first) to readback)))
            .filterIsInstance<PipelineStep.WriteFile>().single { it.relPath == "firmware/extracted/fs_config-$first" }
        val fsConfig = written.content.decodeToString()
        assertTrue(fsConfig.lines().any { it.endsWith("link 0 0 777 capabilities=0x0") }, fsConfig)
        assertTrue(fsConfig.lines().any { it.endsWith("a.txt 0 0 644 capabilities=0x0") }, fsConfig)
    }
}
