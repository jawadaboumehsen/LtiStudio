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

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.pipeline.stages.*
import org.ide.lti.core.domain.pipeline.text.*
import org.ide.lti.core.model.target.*
import org.ide.lti.core.model.workspace.*
import kotlin.test.*

class StageDefinitionsTest {

    private val fx = PipelineFixture()
    private val testTargetGlobal get() = fx.targetGlobal
    private val testSnapshotGlobalOta get() = fx.snapshotGlobalOta

    private fun loadResource(name: String): String {
        val stream = StageDefinitionsTest::class.java.classLoader.getResourceAsStream("pipeline/$name")
            ?: error("Resource pipeline/$name not found on classpath")
        return stream.bufferedReader().use { it.readText().replace("\r\n", "\n") }
    }

    @Test
    fun testRequiredToolIdsUnionEqualsReadinessSet() {
        // Mirrors EnvironmentReadinessAdapter.DEFAULT_REQUIRED_PRODUCT_TOOLS ∪ DEFAULT_REQUIRED_HOST_PACKAGES
        // (core/data cannot be referenced from here); `java` is needed only to launch signapk.jar.
        val readinessSet = setOf(
            "payload-dumper-go", "lpunpack", "lpmake", "simg2img", "erofsfuse", "mkfs.erofs", "avbtool",
            "unpack_bootimg", "img2sdat", "signapk",
            "curl", "unzip", "zip", "sha256sum", "sha1sum", "rsync", "getfattr", "fusermount3", "brotli", "file",
            "xxd", "truncate", "find", "stat", "cat", "test", "cp", "mv", "rm", "mkdir", "java",
        )
        assertEquals(readinessSet - setOf("java"), DefaultPipelineStages.allRequiredToolIds)
    }

    @Test
    fun testGeneratedTextsMatchGoldenFixtures() {
        // 1. updater-script
        val updaterScript = UpdaterScriptWriter.generate(
            target = testTargetGlobal,
            policy = testTargetGlobal.packagePolicy,
            romVersion = "1.0.0",
        ).replace("\r\n", "\n")
        assertEquals(loadResource("updater-script.golden"), updaterScript)

        // 2. build_info.txt
        val buildInfo = BuildInfoWriter.generate(
            BuildInfoWriter.BuildInfoParams(
                device = "PQ84P01",
                version = "1.0.0",
                timestamp = 1726056000L,
                incremental = "20260911.120000",
                securityPatch = "2026-02-01",
            ),
        ).replace("\r\n", "\n")
        assertEquals(loadResource("build_info.txt.golden"), buildInfo)

        // 3. metadata
        val metadata = OtaMetadataWriter.generate(
            OtaMetadataWriter.OtaMetadataParams(
                postBuild = "PQ84P01:15/AQ3A.240812.002/20260311.222527",
                postBuildIncremental = "20260911.120000",
                postSdkLevel = "35",
                postSecurityPatchLevel = "2026-02-01",
                postTimestamp = 1726056000L,
                preDevice = "PQ84P01",
            ),
        ).replace("\r\n", "\n")
        assertEquals(loadResource("metadata.golden"), metadata)

        // 4. dynamic_partitions_op_list & avb-images.txt
        val partitions = listOf(
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "odm",
                100000000L,
                "sha_odm",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "product",
                200000000L,
                "sha_product",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "system",
                300000000L,
                "sha_system",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "system_dlkm",
                50000000L,
                "sha_system_dlkm",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "system_ext",
                150000000L,
                "sha_system_ext",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "vendor",
                250000000L,
                "sha_vendor",
                "key_sha1",
            ),
            DynamicPartitionsOpListWriter.PartitionImageInfo(
                "vendor_dlkm",
                60000000L,
                "sha_vendor_dlkm",
                "key_sha1",
            ),
        )
        val opList = DynamicPartitionsOpListWriter.generateOpList(
            groupName = "qti_dynamic_partitions",
            groupSizeBytes = 17175674880L,
            partitions = partitions,
        ).replace("\r\n", "\n")
        assertEquals(loadResource("dynamic_partitions_op_list.golden"), opList)

        val avbImages = DynamicPartitionsOpListWriter.generateAvbImages(
            partitions,
        ).replace("\r\n", "\n")
        assertEquals(loadResource("avb-images.txt.golden"), avbImages)

        // 5. manifest.json
        val manifest = OtaManifestWriter.generate(
            datetime = 1726056000L,
            device = "PQ84P01",
            filename = "Lti_1.0.0_20260911_PQ84P01-sign.zip",
            patch = "1.0.0",
            size = 2500000000L,
            otaBaseUrl = "https://ota.ltirom.org/updates",
            version = "1.0.0",
            incremental = "20260911.120000",
            sha256 = "c2b647f1146f8c79a29e4726bfcf1a58a7da09f193758bdf214ff94e0192e4ab",
            changelog = "Initial release",
        ).replace("\r\n", "\n")
        assertEquals(loadResource("manifest.json.golden").trim(), manifest.trim())
    }

    @Test
    fun testSidecarNormalizer() {
        val rawStats = listOf(
            "/mnt/system 0 0 755 capabilities=0x0",
            "/mnt/system/bin 0 2000 755 capabilities=0x0",
            "/mnt/system/bin/run-as 0 2000 750 capabilities=0x0",
            "/mnt/system/bin/simpleperf_app_runner 0 2000 750 capabilities=0x0",
            "/mnt/system/app[test]/v1.0+beta 0 0 644 capabilities=0x0",
        )
        val rawSelinux = listOf(
            "u:object_r:system_file:s0",
            "u:object_r:system_file:s0",
            "u:object_r:run_as_exec:s0",
            "u:object_r:simpleperf_app_runner_exec:s0",
            "u:object_r:app_data_file:s0",
        )

        val result = SidecarNormalizer.normalize(
            partition = "system",
            mountPrefix = "/mnt/system",
            rawStatLines = rawStats,
            rawSelinuxLines = rawSelinux,
        )

        // Verify capabilities=0xc0 applied to run-as and simpleperf_app_runner
        assertTrue(
            result.fsConfigContent.contains("system/bin/run-as 0 2000 750 capabilities=0xc0"),
        )
        assertTrue(
            result.fsConfigContent.contains(
                "system/bin/simpleperf_app_runner 0 2000 750 capabilities=0xc0",
            ),
        )

        // Verify system-as-root root path
        assertTrue(result.fsConfigContent.contains("system 0 0 755 capabilities=0x0"))
        assertTrue(result.fileContextContent.contains("""/ u:object_r:system_file:s0"""))

        // Verify regex escaping
        assertTrue(
            result.fileContextContent.contains(
                """/system/app\[test\]/v1\.0\+beta u:object_r:app_data_file:s0""",
            ),
        )

        // Verify line count matching
        assertEquals(rawStats.size, result.fsConfigContent.trim().lines().size)
        assertEquals(rawSelinux.size, result.fileContextContent.trim().lines().size)

        // Line count mismatch throws
        assertFailsWith<IllegalArgumentException> {
            SidecarNormalizer.normalize("system", "/mnt", rawStats, rawSelinux.take(2))
        }
    }

    @Test
    fun testAvbSizeCalculator() {
        val imageSize = 100_000_000L
        val reserve = AvbSizeCalculator.computeReserveBytes(imageSize)
        assertTrue(reserve >= 262144L)
        assertEquals(0L, reserve % 4096L)

        val rounded = AvbSizeCalculator.roundTo4KiB(1001L)
        assertEquals(4096L, rounded)

        runBlocking {
            // Fake responder where max image size is partitionSize - 256 KiB
            val partitionSize = AvbSizeCalculator.findPartitionSize(imageSize) { partSize ->
                partSize - 262144L
            }
            assertTrue(partitionSize >= imageSize + 262144L)
            assertEquals(0L, partitionSize % 4096L)
        }
    }

    private fun tools(steps: List<PipelineStep>): List<StepCommand> = steps.filterIsInstance<PipelineStep.Tool>().map {
        it.command
    }

    @Test
    fun testGlobalOtaPlansFollowTheContract() {
        val ctx = fx.globalOtaContext()
        val ws = ctx.wsPath

        // Stage 1: resumable curl, mime check, checksum via captured result; archive under firmware/downloaded.
        val s1 = tools(FirmwareAcquisitionStage().plan(ctx))
        val curl = s1.single { it.toolId == "curl" }
        assertEquals(
            listOf(
                "-fL",
                "--retry",
                "3",
                "-C",
                "-",
                "-o",
                "$ws/firmware/downloaded/NP05J_GB.zip.partial",
                "https://update.redmagic.gg/firmware/NP05J_GB.zip",
            ),
            curl.args,
        )
        assertEquals(
            listOf("curl", "file", "mv", "sha256sum"),
            s1.map {
                it.toolId
            }.filter { it != "mkdir" },
        )
        val withSha = FirmwareAcquisitionStage().plan(
            ctx.copy(runtimeValues = mapOf(RuntimeKeys.ARCHIVE_SHA256 to "abc  x")),
        )
        assertTrue(withSha.last() is PipelineStep.Check)

        // Stage 2 (OTA): payload extraction then per-partition erofsfuse dumps; sidecars appear once dumps exist.
        val s2 = FirmwareExtractionStage().plan(ctx)
        val s2tools = tools(s2)
        assertEquals(
            listOf(
                "-o",
                "$ws/firmware/extracted",
                "$ws/firmware/payload.bin",
            ),
            s2tools.single {
                it.toolId == "payload-dumper-go"
            }.args,
        )
        assertEquals(
            listOf(
                "$ws/firmware/extracted/odm.img",
                "$ws/firmware/extracted/odm_mnt",
            ),
            s2tools.first {
                it.toolId == "erofsfuse"
            }.args,
        )
        assertTrue(
            s2tools.any {
                it.toolId == "find" && it.args.containsAll(listOf("stat", "-c", "%u %g %a %n"))
            },
        )
        assertTrue(
            s2tools.any {
                it.toolId == "getfattr" &&
                    it.args.containsAll(listOf("-R", "-h", "-n", "security.selinux", "--absolute-names"))
            },
        )
        assertTrue(
            s2tools.any {
                it.toolId == "cp" &&
                    it.args == listOf("-a", "-T", "$ws/firmware/extracted/odm_mnt", "$ws/firmware/extracted/odm")
            },
        )
        assertTrue(s2.none { it is PipelineStep.WriteFile })
        val withDumps = FirmwareExtractionStage().plan(
            ctx.copy(
                runtimeValues = mapOf(
                    RuntimeKeys.statLines("odm") to "0 0 755 $ws/firmware/extracted/odm_mnt",
                    RuntimeKeys.selinuxDump("odm") to
                        "# file: $ws/firmware/extracted/odm_mnt\nsecurity.selinux=\"u:object_r:vendor_file:s0\"",
                ),
            ),
        )
        val sidecar = withDumps.filterIsInstance<PipelineStep.WriteFile>().first()
        assertEquals("firmware/extracted/fs_config-odm", sidecar.relPath)
        assertEquals("odm 0 0 755 capabilities=0x0\n", sidecar.content.decodeToString())

        // Stage 3: rsync + sidecars, conditional erase_footer, AOT purge, build.prop stamp.
        val s3 = WorkTreeAssemblyStage().plan(ctx)
        assertEquals(
            listOf(
                "-a",
                "--delete",
                "$ws/firmware/extracted/odm/",
                "$ws/work/odm/",
            ),
            tools(s3).first {
                it.toolId == "rsync"
            }.args,
        )
        assertTrue(tools(s3).none { it.args.firstOrNull() == "erase_footer" })
        val erased = WorkTreeAssemblyStage().plan(
            ctx.copy(
                runtimeValues = mapOf(RuntimeKeys.exitOf(RuntimeKeys.kernelInfo("boot")) to "0"),
            ),
        )
        assertEquals(
            listOf(
                "erase_footer",
                "--image",
                "$ws/work/kernel/boot.img",
            ),
            tools(erased).first {
                it.args.firstOrNull() == "erase_footer"
            }.args,
        )
        assertTrue(s3.last() is PipelineStep.EditFile)
        assertTrue(
            tools(s3).none { it.toolId == "rsync" && it.args.last().contains("system/system_ext") },
        )

        // Stage 4: writes applied.json with empty record when no packages are enabled.
        val s4 = ModuleApplicationStage().plan(ctx)
        assertEquals(1, s4.size)
        assertEquals("work/modules/applied.json", (s4.single() as PipelineStep.WriteFile).relPath)

        // Stage 5 before any capture: staging, key digests, mkfs.erofs/sha256sum/stat per partition — nothing else.
        val s5 = tools(BuildFlashableZipStage().plan(ctx))
        val mkfs = s5.first { it.toolId == "mkfs.erofs" }
        assertEquals(
            listOf(
                "--quiet", "-z", "lz4hc,9", "-b", "4096", "--mount-point", "odm",
                "--fs-config-file", "$ws/work/configs/fs_config-odm", "--file-contexts", "$ws/work/configs/file_context-odm",
                "-T", "1640995200", "$ws/out/images/odm.img", "$ws/work/odm",
            ),
            mkfs.args,
        )
        assertEquals(
            "/",
            s5.first {
                it.toolId == "mkfs.erofs" && "$ws/out/images/system.img" in it.args
            }.args[6],
        )
        assertTrue(s5.none { it.toolId in setOf("lpmake", "zip", "signapk", "img2sdat") })
        assertTrue(s5.none { it.args.firstOrNull() == "add_hashtree_footer" })

        // Stage 6 waits for the captured zip digest/size before writing the manifest.
        assertTrue(GenerateOtaManifestStage().plan(ctx).none { it is PipelineStep.WriteFile })
    }

    @Test
    fun testChinaRawPlansFollowTheContract() {
        val ctx = fx.chinaRawContext()
        val ws = ctx.wsPath
        val s2 = tools(FirmwareExtractionStage().plan(ctx))
        assertEquals(
            listOf(
                "-o",
                "$ws/firmware/downloaded/NP05J_CN.zip",
                "-d",
                "$ws/firmware/raw",
            ),
            s2.first {
                it.toolId == "unzip"
            }.args,
        )
        assertTrue(s2.none { it.toolId == "lpunpack" }) // needs the super magic first
        val sparse =
            tools(
                FirmwareExtractionStage().plan(
                    ctx.copy(runtimeValues = mapOf(RuntimeKeys.SUPER_MAGIC to "3aff26ed")),
                ),
            )
        assertTrue(sparse.any { it.toolId == "simg2img" })
        val lpunpack = sparse.single { it.toolId == "lpunpack" }
        assertEquals("--slot=0", lpunpack.args.first())
        assertEquals("$ws/firmware/raw/super.raw.img", lpunpack.args[lpunpack.args.size - 2])
        assertTrue(lpunpack.args.containsAll(listOf("-p", "system_a", "-p", "vendor_a")))
        val raw =
            tools(
                FirmwareExtractionStage().plan(
                    ctx.copy(runtimeValues = mapOf(RuntimeKeys.SUPER_MAGIC to "0000")),
                ),
            )
        assertTrue(raw.none { it.toolId == "simg2img" })

        val s3 = tools(WorkTreeAssemblyStage().plan(ctx))
        assertTrue(
            s3.any {
                it.toolId == "rsync" && it.args.last() == "$ws/work/system/system/system_ext/"
            },
        )
        assertTrue(s3.none { it.toolId == "rsync" && it.args.last() == "$ws/work/system_ext/" })
    }

    @Test
    fun testBuildPropStamp() {
        val stamped = BuildPropStamper.stamp(
            original = "ro.build.type=user\norg.lti.build.date=old\nro.product.name=x\n",
            date = "20260911",
            device = "PQ84P01",
            version = "1.0.0",
            buildType = "userdebug",
        )
        assertEquals(
            "ro.build.type=userdebug\nro.product.name=x\norg.lti.build.date=20260911\norg.lti.build.device=PQ84P01\n" +
                "org.lti.build.version=1.0.0\norg.lti.build.variant=userdebug\n",
            stamped,
        )
    }

    @Test
    fun testSidecarsFromDumpsJoinByPath() {
        val mnt = "/w/firmware/extracted/system_mnt"
        val result = SidecarNormalizer.fromDumps(
            partition = "system",
            mountPrefix = mnt,
            statDump = "0 0 755 $mnt\n0 2000 750 $mnt/bin/run-as\n0 0 644 $mnt/app/My App/x.apk\n",
            getfattrDump = "# file: $mnt/app/My App/x.apk\nsecurity.selinux=\"u:object_r:app:s0\"\n\n" +
                "# file: $mnt\nsecurity.selinux=\"u:object_r:rootfs:s0\"\n\n# file: $mnt/bin/run-as\nsecurity.selinux=\"u:object_r:runas_exec:s0\"\n",
        )
        assertTrue(
            result.fsConfigContent.contains("system/bin/run-as 0 2000 750 capabilities=0xc0"),
        )
        assertTrue(
            result.fsConfigContent.contains("system/app/My App/x.apk 0 0 644 capabilities=0x0"),
        )
        assertTrue(
            result.fileContextContent.contains("/system/app/My App/x\\.apk u:object_r:app:s0"),
        )
        assertFailsWith<IllegalArgumentException> {
            SidecarNormalizer.fromDumps("system", mnt, "0 0 755 $mnt/missing\n", "")
        }
    }

    @Test
    fun testCacheKeySensitivity() {
        val ctx1 = fx.globalOtaContext()
        val key1 = FirmwareAcquisitionStage().computeCacheKey(ctx1, "")

        val ctxDifferentUrl = ctx1.copy(
            snapshot = ctx1.snapshot.copy(
                acquisition = ctx1.snapshot.acquisition.copy(
                    firmware = ctx1.snapshot.acquisition.firmware.copy(
                        acquisitionUrl = "https://different.org/fw.zip",
                    ),
                ),
            ),
        )
        assertNotEquals(key1, FirmwareAcquisitionStage().computeCacheKey(ctxDifferentUrl, ""))

        val key2 = CacheKeys.stage2ExtractionKey(key1, testTargetGlobal)
        val key2DifferentSlot = CacheKeys.stage2ExtractionKey(
            key1,
            testTargetGlobal.copy(activeSlotSuffix = "_b"),
        )
        assertNotEquals(key2, key2DifferentSlot)

        val key3 = CacheKeys.stage3AssemblyKey(key2, testTargetGlobal, ctx1.snapshot)
        val key3DifferentVersion = CacheKeys.stage3AssemblyKey(
            key2,
            testTargetGlobal,
            ctx1.snapshot.copy(assembly = ctx1.snapshot.assembly.copy(romVersion = "2.0.0")),
        )
        assertNotEquals(key3, key3DifferentVersion)

        val key4 = CacheKeys.stage4ModuleKey(key3, ctx1.snapshot)
        val key4Modules = CacheKeys.stage4ModuleKey(
            key3,
            ctx1.snapshot.copy(
                customization = ctx1.snapshot.customization.copy(enabledPackages = listOf("mod_1")),
            ),
        )
        assertNotEquals(key4, key4Modules)

        val key5 = CacheKeys.stage5BuildZipKey(key4, ctx1.snapshot, "avb_sha", "plat_sha")
        val key5DifferentCert = CacheKeys.stage5BuildZipKey(
            key4,
            ctx1.snapshot,
            "avb_sha",
            "plat_sha_2",
        )
        assertNotEquals(key5, key5DifferentCert)

        val key6 = CacheKeys.stage6ManifestKey(key5, ctx1.snapshot)
        val key6DifferentBase = CacheKeys.stage6ManifestKey(
            key5,
            ctx1.snapshot.copy(
                release = ctx1.snapshot.release.copy(otaBaseUrl = "https://ota.new.org"),
            ),
        )
        assertNotEquals(key6, key6DifferentBase)
    }
}
