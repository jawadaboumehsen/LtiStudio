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

import org.ide.lti.core.domain.pipeline.stages.BuildChecks
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.text.OtaMetadataWriter
import org.ide.lti.core.domain.pipeline.toolkit.AbOtaPackage
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.model.target.MetadataPolicy
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.PackageType
import kotlin.test.Test
import kotlin.test.assertEquals
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
            BuildFlashableZipStage.PACKAGE_BUILD_PROP to SimulatedExecutionPort.STOCK_BUILD_PROP,
            RuntimeKeys.ZIP_SHA256 to "zipsha256  rom.zip",
            RuntimeKeys.ZIP_SIZE to "1000000",
            BuildFlashableZipStage.COVERAGE_RESULT_KEY to CoverageResult.encode(CoverageResult()),
            // A kernel older than 6.1: images are not deduplicated (the dedupe test reads a 6.6 one).
            BuildChecks.KERNEL_KEY to "version 5.15.0\nnoconfig",
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
            build = ctx.snapshot.build.copy(filesystem = "btrfs"),
        )
        val steps = stage.plan(ctx.copy(snapshot = invalidSnap))
        val check = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check)
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("UNSUPPORTED_FILESYSTEM"), "Error must contain UNSUPPORTED_FILESYSTEM: $error")
    }

    @Test
    fun `every built image is checked by fsck right after mkfs`() {
        val labels = stage.plan(fullContext()).map { it.label }
        fullContext().target.dynamicPartitions.forEach { p ->
            assertEquals(labels.indexOf("mkfs.erofs $p") + 1, labels.indexOf("fsck.erofs $p"), "fsck after mkfs $p")
        }
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
        val customPolicy = ctx.target.copy(
            packagePolicy = ctx.target.packagePolicy.copy(
                excludeVbmeta = false,
                recoverySystemMountPoint = "/custom_root",
                metadataPolicy = MetadataPolicy.PACKAGE_ONLY,
                bootSlots = listOf("a"),
            ),
        )
        val steps = stage.plan(ctx.copy(target = customPolicy))
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
            target = ctx.target.copy(packagePolicy = ctx.target.packagePolicy.copy(bootSlots = listOf("a"))),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedBootSlots, prevKey))

        val mutatedExcludeVbmeta = ctx.copy(
            target = ctx.target.copy(packagePolicy = ctx.target.packagePolicy.copy(excludeVbmeta = false)),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedExcludeVbmeta, prevKey))

        val mutatedMountPoint = ctx.copy(
            target = ctx.target.copy(
                packagePolicy = ctx.target.packagePolicy.copy(recoverySystemMountPoint = "/system"),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedMountPoint, prevKey))

        val mutatedMetadataPolicy = ctx.copy(
            target = ctx.target.copy(
                packagePolicy = ctx.target.packagePolicy.copy(metadataPolicy = MetadataPolicy.PACKAGE_ONLY),
            ),
        )
        assertNotEquals(baseKey, stage.computeCacheKey(mutatedMetadataPolicy, prevKey))
    }

    @Test
    fun `missing coverage result plans check coverage host step`() {
        val base = fullContext()
        val withoutCoverage = base.copy(
            runtimeValues = base.runtimeValues - BuildFlashableZipStage.COVERAGE_RESULT_KEY,
        )
        val steps = stage.plan(withoutCoverage)
        val hostStep = steps.filterIsInstance<PipelineStep.Host>().firstOrNull()
        assertNotNull(hostStep, "Must plan host step for checking coverage")
        assertEquals("check coverage", hostStep.label)
        assertEquals(BuildFlashableZipStage.COVERAGE_RESULT_KEY, hostStep.resultKey)
    }

    @Test
    fun `unclean coverage result plans failing check COVERAGE_REFUSAL`() {
        val base = fullContext()
        val dirtyResult = CoverageResult(
            uncoveredFsConfig = listOf("system/bin/uncovered_tool"),
            uncoveredContexts = listOf("system/bin/uncovered_tool"),
        )
        val withDirtyCoverage = base.copy(
            runtimeValues = base.runtimeValues + (
                BuildFlashableZipStage.COVERAGE_RESULT_KEY to CoverageResult.encode(
                    dirtyResult,
                )
                ),
        )
        val steps = stage.plan(withDirtyCoverage)
        val check = steps.filterIsInstance<PipelineStep.Check>().firstOrNull()
        assertNotNull(check, "Must plan check step for coverage gate failure")
        val error = check.verify(emptyMap())
        assertNotNull(error)
        assertTrue(error.contains("COVERAGE_REFUSAL"), "Error must contain COVERAGE_REFUSAL: $error")
        assertTrue(error.contains("system/bin/uncovered_tool"), "Error must mention uncovered file")
    }

    @Test
    fun `a link the build broke stops it until accepted`() {
        val base = fullContext()
        val listing = "P\tsystem_ext\nL\tsystem_ext\tpriv-app/Wfd/lib/arm64/libwfd.so\t/system_ext/lib64/libwfd.so"
        val values = base.runtimeValues + mapOf(
            "build.links.work" to listing,
            "build.links.work.missing" to "system_ext/lib64/libwfd.so\n",
            "build.links.firmware/extracted" to listing,
            "build.links.firmware/extracted.missing" to "",
        )
        val steps = stage.plan(base.copy(runtimeValues = values))
        val labels = steps.map { it.label }
        assertTrue(labels.indexOf("link check") in 0 until labels.indexOfFirst { it.startsWith("mkfs") }, "$labels")
        val check = steps.filterIsInstance<PipelineStep.Check>().single { it.label == "link check" }
        assertTrue(check.verify(emptyMap())!!.startsWith("BROKEN_LINKS: 1 link(s)"))

        val accepted = base.copy(
            runtimeValues = values,
            snapshot = base.snapshot.copy(
                build = base.snapshot.build.copy(acknowledgedWarnings = setOf(BuildWarningKind.BrokenLinks.name)),
            ),
        )
        assertTrue(stage.plan(accepted).none { it.label == "link check" })
    }

    @Test
    fun `a partition built as ext4 is sized by a trial, checked with e2fsck and read back through fuse2fs`() {
        val base = fullContext()
        val vendorLine = "vendor  /vendor  erofs  ro  wait,slotselect,avb=vbmeta,logical,first_stage_mount"
        val values = base.runtimeValues + mapOf(
            BuildFlashableZipStage.ext4MeasureKey("vendor") to "1929366941 4059",
            BuildFlashableZipStage.ext4TrialKey("vendor") to "Free blocks:              76229",
            "build.mount.tables" to "work/vendor/etc/fstab.qcom\t$vendorLine\n" +
                "vendor_boot.img:first_stage_ramdisk/fstab.qcom\t$vendorLine",
        )
        val ctx = base.copy(
            runtimeValues = values,
            snapshot = base.snapshot.copy(
                build = base.snapshot.build.copy(partitionFilesystem = mapOf("vendor" to "ext4")),
            ),
        )
        val steps = stage.plan(ctx)
        val tools = steps.filterIsInstance<PipelineStep.Tool>()
        val img = ctx.abs("out/images/vendor.img")
        val mke2fs = tools.single { it.label == "mke2fs vendor" }
        assertEquals("mke2fs", mke2fs.command.toolId)
        assertEquals(listOf(img, "458767"), mke2fs.command.args.takeLast(2))
        assertEquals("e2fsdroid", tools.single { it.label == "e2fsdroid vendor" }.command.toolId)
        assertEquals(listOf("-fn", img), tools.single { it.label == "e2fsck vendor" }.command.args)
        assertEquals("fuse2fs", tools.single { it.label == "mount built vendor" }.command.toolId)
        assertTrue(tools.none { it.label == "mkfs.erofs vendor" })
        assertEquals("mkfs.erofs", tools.single { it.label == "mkfs.erofs system" }.command.toolId)

        // vendor/etc gets the ext4 line from the build; the first-stage fstab is inside vendor_boot: refused.
        assertTrue(steps.filterIsInstance<PipelineStep.EditFile>().any { it.relPath == "work/vendor/etc/fstab.qcom" })
        val refusal = steps.filterIsInstance<PipelineStep.Check>().single { it.label == "mount table" }
        assertTrue(
            refusal.verify(emptyMap())!!.startsWith("MOUNT_TABLE: vendor_boot.img:first_stage_ramdisk/fstab.qcom"),
        )
        val labels = steps.map { it.label }
        assertTrue(labels.indexOf("mount table") < labels.indexOf("mke2fs vendor"), "refused before any image")
    }

    @Test
    fun `a partition built as F2FS is sized by sload's max image size and read back by dump f2fs in fakeroot`() {
        val base = fullContext()
        // Real fw-baseline vendor: prepare output and the trial sload's check line.
        val values = base.runtimeValues + mapOf(
            BuildFlashableZipStage.f2fsPrepareKey("vendor") to "1929366941 4059 0:2000",
            BuildFlashableZipStage.f2fsTrialKey("vendor") to "[FSCK] Max image size: 1884 MB, Free space: 186 MB",
        )
        val ctx = base.copy(
            runtimeValues = values,
            snapshot = base.snapshot.copy(
                build = base.snapshot.build.copy(partitionFilesystem = mapOf("vendor" to "f2fs")),
            ),
        )
        val tools = stage.plan(ctx).filterIsInstance<PipelineStep.Tool>()
        val img = ctx.abs("out/images/vendor.img")
        assertEquals(listOf("-s", "2011168768", img), tools.single { it.label == "size vendor" }.command.args)
        val make = tools.single { it.label == "make_f2fs vendor" }.command.args
        assertEquals(listOf("-R", "0:2000"), make.subList(make.indexOf("-R"), make.indexOf("-R") + 2))
        assertEquals("sload_f2fs", tools.single { it.label == "sload_f2fs vendor" }.command.toolId)
        assertEquals(listOf("--dry-run", "-f", img), tools.single { it.label == "fsck.f2fs vendor" }.command.args)
        val readback = tools.single { it.label == "read back built vendor" }.command
        assertEquals("fakeroot", readback.toolId)
        assertTrue("dump.f2fs" in readback.args, "the pinned path when the run has one")
        assertTrue(tools.none { it.label == "mkfs.erofs vendor" || it.label == "mount built vendor" })
    }

    @Test
    fun `an unsigned apk in the work tree stops the build until accepted`() {
        val base = fullContext()
        val found = base.copy(
            runtimeValues = base.runtimeValues + ("build.apks.unsigned" to "product/app/Mod/Mod.apk\n"),
        )
        val refusal = stage.plan(found).filterIsInstance<PipelineStep.Check>().single { it.label == "apk signatures" }
        assertTrue(refusal.verify(emptyMap())!!.startsWith("UNSIGNED_APK: 1 APK(s) in work/ carry no signature"))
        val accepted = found.copy(
            snapshot = found.snapshot.copy(
                build = found.snapshot.build.copy(acknowledgedWarnings = setOf(BuildWarningKind.UnsignedApks.name)),
            ),
        )
        assertTrue(stage.plan(accepted).none { it.label == "apk signatures" })
        val clean = base.copy(runtimeValues = base.runtimeValues + ("build.apks.unsigned" to ""))
        assertTrue(stage.plan(clean).none { it.label == "apk signatures" }, "fw-baseline: every APK is signed")
    }

    @Test
    fun `free space grows an ext4 image and the brotli level reaches the package`() {
        val base = fullContext()
        val ctx = base.copy(
            runtimeValues = base.runtimeValues + mapOf(
                BuildFlashableZipStage.ext4MeasureKey("vendor") to "1929366941 4059",
                BuildFlashableZipStage.ext4TrialKey("vendor") to "Free blocks:              76229",
            ),
            snapshot = base.snapshot.copy(
                build = base.snapshot.build.copy(
                    partitionFilesystem = mapOf("vendor" to "ext4"),
                    partitionFreeMib = mapOf("vendor" to 256),
                    brotliLevel = 11,
                ),
            ),
        )
        val tools = stage.plan(ctx).filterIsInstance<PipelineStep.Tool>()
        val mke2fs = tools.single { it.label == "mke2fs vendor" }.command.args
        // 458767 blocks as built tight, 65536 more for 256 MiB; 64 inodes per MiB on top of the 4123.
        assertEquals("524303", mke2fs.last())
        assertEquals("20507", mke2fs[mke2fs.indexOf("-N") + 1])
        assertTrue(tools.filter { it.label.startsWith("brotli ") }.all { "--quality=11" in it.command.args })
    }

    @Test
    fun `a fixed size sets an ext4 image's size and refuses one its content does not fit`() {
        val base = fullContext()
        fun plan(bytes: Long) = stage.plan(
            base.copy(
                runtimeValues = base.runtimeValues + mapOf(
                    BuildFlashableZipStage.ext4MeasureKey("vendor") to "1929366941 4059",
                    BuildFlashableZipStage.ext4TrialKey("vendor") to "Free blocks:              76229",
                ),
                snapshot = base.snapshot.copy(
                    build = base.snapshot.build.copy(
                        partitionFilesystem = mapOf("vendor" to "ext4"),
                        partitionSizeBytes = mapOf("vendor" to bytes),
                    ),
                ),
            ),
        )
        // 2 GiB = 524288 blocks; the 65521 beyond the content's 458767 get 64 inodes per whole MiB (255).
        val mke2fs = plan(2L shl 30).filterIsInstance<PipelineStep.Tool>().single { it.label == "mke2fs vendor" }
        assertEquals("524288", mke2fs.command.args.last())
        assertEquals("20443", mke2fs.command.args[mke2fs.command.args.indexOf("-N") + 1])
        val small = plan(1L shl 30)
        assertTrue(small.none { it.label == "mke2fs vendor" })
        val refusal = small.filterIsInstance<PipelineStep.Check>().single { it.label == "partition size vendor" }
        assertEquals(
            "PARTITION_TOO_SMALL: vendor needs 1835068 KiB as ext4, but its size is set to 1048576 KiB. Raise it in " +
                "Build & Sign > Compression, or set it back to automatic.",
            refusal.verify(emptyMap()),
        )
    }

    @Test
    fun `erofs images are deduplicated when the boot kernel reads it, unless a partition says otherwise`() {
        val base = fullContext()
        fun mkfs(kernel: String, dedupe: Map<String, Boolean> = emptyMap()) = stage.plan(
            base.copy(
                snapshot = base.snapshot.copy(build = base.snapshot.build.copy(partitionDedupe = dedupe)),
                runtimeValues = base.runtimeValues + (BuildChecks.KERNEL_KEY to kernel),
            ),
        ).filterIsInstance<PipelineStep.Tool>().filter { it.label.startsWith("mkfs.erofs ") }
            .associate { it.label.removePrefix("mkfs.erofs ") to ("-Ededupe,fragments" in it.command.args) }
        val gki = "version 6.6.92-android15-8\nCONFIG_EROFS_FS=y\nCONFIG_EROFS_FS_ZIP=y\n"
        assertEquals(
            listOf(false, true, true, true, false, false),
            listOf("6.0.9", "6.1", "6.1.57-android14", "7.0.1", "5.15.149", null).map(BuildFilesystems::kernelDedupes),
        )
        // PQ84P01's 6.6 kernel: every partition is deduplicated; vendor can be turned off by hand.
        assertTrue(mkfs(gki).values.all { it })
        assertEquals(false, mkfs(gki, mapOf("vendor" to false))["vendor"])
        // A 5.15 kernel (or no boot kernel at all) gets none, unless forced, which is then refused.
        assertTrue(mkfs("version 5.15.0\nnoconfig").values.none { it })
        assertTrue(mkfs("nokernel").values.none { it })
        val forced = stage.plan(
            base.copy(
                snapshot = base.snapshot.copy(
                    build = base.snapshot.build.copy(partitionDedupe = mapOf("vendor" to true)),
                ),
            ),
        ).filterIsInstance<PipelineStep.Check>().single { it.label == "kernel erofs dedupe" }.verify(emptyMap())!!
        assertTrue(forced.startsWith("KERNEL_FILESYSTEM: the boot kernel 5.15.0 cannot read deduplicated"), forced)
        // Before the kernel is read, no image is built: the kernel decides.
        assertTrue(
            stage.plan(base.copy(runtimeValues = base.runtimeValues - BuildChecks.KERNEL_KEY))
                .none { it.label.startsWith("mkfs.erofs ") },
        )
    }

    @Test
    fun `a cluster size reaches mkfs erofs unless uncompressed, and the files are measured`() {
        val base = fullContext()
        fun args(build: BuildSettings) = stage.plan(base.copy(snapshot = base.snapshot.copy(build = build)))
            .filterIsInstance<PipelineStep.Tool>().single { it.label == "mkfs.erofs vendor" }.command.args
        val b = base.snapshot.build
        assertTrue("-C" !in args(b))
        assertEquals(
            "65536",
            args(b.copy(partitionClusterKib = mapOf("vendor" to 64))).let { it[it.indexOf("-C") + 1] },
        )
        assertTrue(
            "-C" !in args(
                b.copy(partitionClusterKib = mapOf("vendor" to 64), partitionCompression = mapOf("vendor" to "none")),
            ),
        )
        val du = stage.plan(base).filterIsInstance<PipelineStep.Tool>().single { it.label == "content size vendor" }
        assertEquals(BuildFlashableZipStage.contentSize("vendor"), du.resultKey)
        assertEquals(listOf("-sb", "--apparent-size", base.abs("work/vendor")), du.command.args)
    }

    @Test
    fun `the package type decides between the flashable zip and the A-B OTA, or builds both`() {
        val base = fullContext()
        fun plan(type: PackageType) = stage.plan(
            base.copy(snapshot = base.snapshot.copy(build = base.snapshot.build.copy(packageType = type))),
        )
        fun labels(type: PackageType) = plan(type).map { it.label }
        // Flashable only: no payload; A/B only: no block-dat, no recovery zip; both: each.
        assertTrue(labels(PackageType.FLASHABLE).none { it == "payload.bin" })
        assertTrue(labels(PackageType.FLASHABLE).any { it.startsWith("img2sdat ") })
        assertTrue(labels(PackageType.AB_OTA).none { it.startsWith("img2sdat ") || it == "signapk" })
        assertTrue(labels(PackageType.BOTH).containsAll(listOf("payload.bin", "signapk", "signapk A/B package")))
        // The payload: the dynamic images, then the signed boot chain from staging.
        val payload = plan(
            PackageType.AB_OTA,
        ).filterIsInstance<PipelineStep.Tool>().single { it.label == "payload.bin" }
        val args = payload.command.args
        assertEquals("python3", payload.command.toolId)
        assertEquals(listOf("-I", base.abs("work/package/ab/ota_payload.py"), "payload"), args.take(3))
        assertTrue(base.abs("keys/platform.key.pem") in args)
        assertTrue(
            "${base.target.dynamicPartitions.first()}=${base.abs(
                "out/images/${base.target.dynamicPartitions.first()}.img",
            )}:dynamic" in args,
        )
        val boot = base.target.packagePolicy.flashableBootPartitions.first()
        assertTrue("$boot=${base.abs("out/package/.stage/$boot.img")}" in args)
        // The OTA is the main package when it is the only one: Release metadata and Publish use it.
        val ota = base.copy(
            snapshot = base.snapshot.copy(build = base.snapshot.build.copy(packageType = PackageType.AB_OTA)),
        )
        assertTrue(BuildFlashableZipStage.zipRelPath(ota).endsWith("-ota.zip"))
        assertEquals(BuildFlashableZipStage.flashableRelPath(base), BuildFlashableZipStage.zipRelPath(base))
        assertEquals("lz4:3:65536", AbOtaPackage.vab(virtualAb = true, sdk = 35))
        assertEquals("lz4:2:0", AbOtaPackage.vab(virtualAb = true, sdk = 34))
        assertEquals("none", AbOtaPackage.vab(virtualAb = false, sdk = 35))
        // The ROM's ro.virtual_ab.* properties win: PQ84P01's vendor sets only enabled and compression.enabled.
        val pq = "ro.virtual_ab.enabled=true\nro.virtual_ab.compression.enabled=true\n"
        assertEquals("lz4:3:65536", AbOtaPackage.vab(virtualAb = true, sdk = 35, vendorProps = pq))
        assertEquals("none", AbOtaPackage.vab(virtualAb = true, sdk = 35, vendorProps = "ro.virtual_ab.enabled=false"))
        val custom = "ro.virtual_ab.enabled=true\nro.virtual_ab.compression.method=gz\nro.virtual_ab.cow_version=2\n"
        assertEquals("gz:2:0", AbOtaPackage.vab(virtualAb = false, sdk = 35, vendorProps = custom))
        assertEquals(
            "none:3:65536",
            AbOtaPackage.vab(virtualAb = true, sdk = 35, vendorProps = "ro.virtual_ab.compression.enabled=false"),
        )
    }

    @Test
    fun `the A-B metadata carries the AOSP keys with room for the members' offsets`() {
        val text = OtaMetadataWriter.generateAb(
            OtaMetadataWriter.OtaMetadataParams("fp", "inc", "35", "2025-12-01", 1761920000, "PQ84P01"),
        )
        val keys = text.lines().filter { it.isNotEmpty() }.map { it.substringBefore('=') }
        assertEquals(keys.sorted(), keys)
        assertTrue("ota-type=AB" in text && "pre-device=PQ84P01" in text && "ota-required-cache=0" in text)
        val blank = text.lines().single { it.startsWith("ota-property-files=") }.substringAfter('=')
        assertEquals(OtaMetadataWriter.PROPERTY_FILES_WIDTH, blank.length)
    }

    @Test
    fun `a standalone image asked for as sparse goes into the fastboot package as one`() {
        val base = fullContext()
        val ctx = base.copy(
            snapshot = base.snapshot.copy(
                build = base.snapshot.build.copy(
                    extraPackages = setOf("FullPackage"),
                    standaloneImages = listOf("vendor_dlkm", "odm"),
                    sparseImages = setOf("vendor_dlkm"),
                    acknowledgedWarnings = setOf(BuildWarningKind.SuperOverlap.name),
                ),
            ),
        )
        val tools = stage.plan(ctx).filterIsInstance<PipelineStep.Tool>()
        val sparse = tools.single { it.label == "sparse vendor_dlkm" }.command
        assertEquals("img2simg", sparse.toolId)
        val zip = tools.single { it.label == "zip fastboot images" }.command.args
        assertTrue(zip.any { it.endsWith("/out/extra/vendor_dlkm.img") }, zip.toString())
        assertTrue(zip.any { it.endsWith("/out/images/odm.img") }, zip.toString())
        assertTrue(tools.none { it.label == "sparse odm" })
    }

    @Test
    fun `odin comes verified, as a plain tar or as lz4 images, the payload alone, each with a checksum`() {
        val base = fullContext()
        fun plan(type: PackageType, vararg kinds: PackageKind) = stage.plan(
            base.copy(
                snapshot = base.snapshot.copy(
                    build = base.snapshot.build.copy(packageType = type, extraPackages = kinds.map { it.name }.toSet()),
                ),
            ),
        )
        fun labels(vararg kinds: PackageKind, type: PackageType = PackageType.FLASHABLE) =
            plan(type, *kinds).filterIsInstance<PipelineStep.Tool>().map { it.label }

        val verified = labels(PackageKind.Odin)
        assertTrue("md5 odin AP" in verified && "drop plain tar" in verified, verified.toString())
        assertTrue(verified.any { it.startsWith("sha256 AP_") && it.endsWith(".tar.md5") }, verified.toString())
        val both = labels(PackageKind.Odin, PackageKind.OdinTar)
        assertTrue("drop plain tar" !in both && both.count { it.startsWith("sha256 AP_") } == 2, both.toString())
        val lz4 = labels(PackageKind.OdinLz4)
        assertTrue("copy odin lz4" in lz4 && "tar odin AP" !in lz4, lz4.toString())
        assertTrue("sha256 super.img.lz4" in lz4, lz4.toString())
        // The payload is copied out of the A/B OTA: no full super is made for it.
        val payload = labels(PackageKind.Payload, type = PackageType.BOTH)
        assertTrue("copy payload.bin" in payload && "lpmake full super" !in payload, payload.toString())
        val noOta = plan(PackageType.FLASHABLE, PackageKind.Payload).filterIsInstance<PipelineStep.Check>()
            .single { it.label == "payload alone" }.verify(emptyMap())!!
        assertTrue(noOta.startsWith("PAYLOAD_NEEDS_AB_OTA"), noOta)
    }

    @Test
    fun `a partition built as a filesystem the boot kernel cannot mount is refused`() {
        val base = fullContext()
        val vendorF2fs = base.snapshot.copy(
            build = base.snapshot.build.copy(partitionFilesystem = mapOf("vendor" to "f2fs")),
        )
        fun refusal(config: String) = stage.plan(
            base.copy(snapshot = vendorF2fs, runtimeValues = base.runtimeValues + ("build.kernel.config" to config)),
        ).filterIsInstance<PipelineStep.Check>().singleOrNull { it.label == "kernel filesystems" }

        // PQ84P01's 6.6 GKI kernel mounts all three; a kernel without F2FS cannot mount vendor.
        val gki = "version 6.6.92-android15-8\nCONFIG_EROFS_FS=y\nCONFIG_EXT4_FS=y\nCONFIG_F2FS_FS=y\n"
        assertEquals(null, refusal(gki))
        val noF2fs = refusal("version 5.10.1\nCONFIG_EROFS_FS=y\nCONFIG_EXT4_FS=y\n")!!.verify(emptyMap())!!
        assertTrue(noF2fs.startsWith("KERNEL_FILESYSTEM: the boot kernel 5.10.1 cannot mount vendor as f2fs"), noF2fs)
        assertTrue(noF2fs.endsWith("Build them as erofs or ext4."), noF2fs)
        assertEquals(null, refusal("version 5.10.1\nnoconfig\n"), "no embedded config: nothing is known")
    }

    @Test
    fun `a compressed erofs image the boot kernel cannot decode is refused`() {
        val base = fullContext()
        fun refusal(build: BuildSettings, config: String) = stage.plan(
            base.copy(
                snapshot = base.snapshot.copy(build = build),
                runtimeValues = base.runtimeValues + ("build.kernel.config" to config),
            ),
        ).filterIsInstance<PipelineStep.Check>().singleOrNull { it.label == "kernel erofs compression" }
            ?.verify(emptyMap())

        val lz4hc = base.snapshot.build.copy(filesystem = "erofs", compression = "lz4hc", level = 9)
        // PQ84P01's GKI kernel has the EROFS decoders; one without CONFIG_EROFS_FS_ZIP reads uncompressed images only.
        assertEquals(null, refusal(lz4hc, "version 6.6\nCONFIG_EROFS_FS=y\nCONFIG_EROFS_FS_ZIP=y\n"))
        val noZip = refusal(lz4hc, "version 6.6\nCONFIG_EROFS_FS=y\n")!!
        assertTrue(noZip.startsWith("KERNEL_FILESYSTEM: the boot kernel 6.6 cannot read "), noZip)
        assertTrue("system (lz4)" in noZip && "its EROFS reads no compression" in noZip, noZip)
        assertTrue(noZip.endsWith("Build them with none."), noZip)
        val none = lz4hc.copy(partitionCompression = base.target.dynamicPartitions.associateWith { "none" })
        assertEquals(null, refusal(none, "version 6.6\nCONFIG_EROFS_FS=y\n"))
        assertEquals(null, refusal(lz4hc, "version 6.6\nnoconfig\n"), "no embedded config: nothing is known")

        // lzma for vendor: refused on PQ84P01's lz4-only kernel, built on one with the lzma decoder.
        val lzma = lz4hc.copy(partitionCompression = mapOf("vendor" to "lzma"))
        val lz4Only = refusal(lzma, "version 6.1\nCONFIG_EROFS_FS=y\nCONFIG_EROFS_FS_ZIP=y\n")!!
        assertTrue("vendor (lzma)" in lz4Only && "Build them with lz4hc or none." in lz4Only, lz4Only)
        assertEquals(
            null,
            refusal(lzma, "version 6.1\nCONFIG_EROFS_FS=y\nCONFIG_EROFS_FS_ZIP=y\nCONFIG_EROFS_FS_ZIP_LZMA=y\n"),
        )
        assertEquals(
            listOf("lz4", "zstd"),
            KernelFacts.parse("CONFIG_EROFS_FS=y\nCONFIG_EROFS_FS_ZIP=y\nCONFIG_EROFS_FS_ZIP_ZSTD=y\n")
                ?.erofsCompressions,
        )
    }
}
