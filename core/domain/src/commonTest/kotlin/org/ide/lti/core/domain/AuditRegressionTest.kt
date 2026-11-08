/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain

import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.text.UpdaterScriptWriter
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.ConfigValidators
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import org.ide.lti.core.domain.pipeline.toolkit.ImageVerify
import org.ide.lti.core.domain.pipeline.toolkit.KernelFacts
import org.ide.lti.core.domain.pipeline.toolkit.LinkCheck
import org.ide.lti.core.domain.pipeline.toolkit.MountTable
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.domain.pipeline.toolkit.RemovalItem
import org.ide.lti.core.domain.pipeline.toolkit.RemovalList
import org.ide.lti.core.domain.pipeline.toolkit.RemovalListMatcher
import org.ide.lti.core.domain.pipeline.toolkit.SuperGroup
import org.ide.lti.core.domain.pipeline.toolkit.SuperLayoutPlanner
import org.ide.lti.core.domain.pipeline.toolkit.SuperPartition
import org.ide.lti.core.domain.plugin.edit.EditResult
import org.ide.lti.core.domain.plugin.edit.XmlResourceEditor
import org.ide.lti.core.domain.workbench.CompareWorkbench
import org.ide.lti.core.model.plugin.XmlChange
import org.ide.lti.core.model.plugin.XmlChangeKind
import org.ide.lti.core.model.plugin.XmlMatch
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What an audit of the pipeline's edges found: each test failed before its fix. */
class AuditRegressionTest {
    private val f = PipelineFixture()
    private val strings = "<resources><string name=\"a\">old</string></resources>"

    private fun assemblyRefuses(settings: AssemblySettings) =
        assertTrue(StageSettingsValidator.validateAssembly(settings).hasBlockingErrors)

    private fun buildRefuses(settings: BuildSettings) =
        assertTrue(StageSettingsValidator.validateBuild(settings).hasBlockingErrors)

    @Test
    fun differentPropertyMapsHaveDifferentCacheKeys() {
        val one = f.snapshotGlobalOta.copy(assembly = AssemblySettings(propertyOverrides = mapOf("a" to "b,c=d")))
        val two = one.copy(assembly = one.assembly.copy(propertyOverrides = mapOf("a" to "b", "c" to "d")))
        assertNotEquals(
            CacheKeys.stage3AssemblyKey("same", f.targetGlobal, one),
            CacheKeys.stage3AssemblyKey("same", f.targetGlobal, two),
        )
    }

    @Test
    fun anAbsoluteKeyPathStaysAbsolute() {
        assertEquals("/home/user/private.pem", f.globalOtaContext().abs("/home/user/private.pem"))
        assertTrue(f.globalOtaContext().abs("keys/avb.pem").endsWith("/keys/avb.pem"))
    }

    @Test
    fun buildSettingsAToolWouldRefuseAreRefusedFirst() {
        buildRefuses(BuildSettings(blockSize = 1))
        buildRefuses(BuildSettings(compression = "not-a-compressor"))
        buildRefuses(BuildSettings(compression = "lz4hc", level = 13))
        buildRefuses(BuildSettings(partitionCompression = mapOf("vendor" to "zstd,23")))
        assertFalse(StageSettingsValidator.validateBuild(BuildSettings(blockSize = 16384)).hasBlockingErrors)
    }

    @Test
    fun everyCompressionTheBuildOffersPasses() {
        listOf("lz4", "lz4hc,12", "lz4hc,level=9", "lzma,9", "lzma,109", "deflate,6", "zstd,22", "none").forEach {
            assertNull(BuildFilesystems.compressionProblem(it), it)
        }
    }

    @Test
    fun anUnknownStandaloneImageIsRefused() {
        val settings = BuildSettings(
            extraPackages = setOf(PackageKind.FullPackage.name),
            standaloneImages = listOf("x"),
        )
        assertTrue(StageSettingsValidator.validateBuild(settings, f.targetGlobal).hasBlockingErrors)
    }

    @Test
    fun assemblySettingsCannotAddLinesToBuildProp() {
        assemblyRefuses(AssemblySettings(propertyOverrides = mapOf("#ignored" to "1")))
        assemblyRefuses(AssemblySettings(propertyOverrides = mapOf("a" to "1\rb=2")))
        assemblyRefuses(AssemblySettings(buildType = "root"))
    }

    @Test
    fun aCoverageRuleHasNoNegativeOwner() {
        assertTrue(CoverageRule.parse("/vendor/.* -1 0 644 u:object_r:vendor_file:s0").second.isNotEmpty())
        assertTrue(CoverageRule.parse("/vendor/.* 0 -1 644 u:object_r:vendor_file:s0").second.isNotEmpty())
    }

    @Test
    fun sidecarValidatorsReadTheWholeLine() {
        assertTrue(ConfigValidators.validateFileContexts("/[ u:object_r:system_file:s0").isNotEmpty())
        assertTrue(ConfigValidators.validateFileContexts("/system/a user:role").isNotEmpty())
        assertTrue(ConfigValidators.validateFsConfig("system/bin/a 0 0 755 capabilities=nothex").isNotEmpty())
        assertTrue(ConfigValidators.validateFsConfig("system/bin/a 0 0 755 capabilities=0x0 extra").isNotEmpty())
    }

    @Test
    fun realSidecarLinesStillPass() {
        assertEquals(
            emptyList(),
            ConfigValidators.validateFileContexts(
                """
                /vendor(/.*)? u:object_r:vendor_file:s0
                /system/lib(64)?/libc\+\+\.so -- u:object_r:system_lib_file:s0
                /data/misc/x(/.*)? <<none>>
                /dev/a u:object_r:device:s0:c512,c768
                """.trimIndent(),
            ),
        )
        assertEquals(
            emptyList(),
            ConfigValidators.validateFsConfig("vendor/bin/cnd 1000 1000 755 capabilities=0x1000001400\n/ 0 0 755"),
        )
    }

    @Test
    fun imageCheckFindsLostCapabilities() {
        val diffs = ImageVerify.sidecarDifferences(
            "vendor/bin/a 0 0 755 capabilities=0x400",
            "/vendor/bin/a u:object_r:vendor_file:s0",
            "vendor/bin/a 0 0 755 capabilities=0x0",
            "/vendor/bin/a u:object_r:vendor_file:s0",
        )
        assertEquals(1, diffs.size)
    }

    @Test
    fun noCapabilitiesIsTheSameWrittenEitherWay() {
        val diffs = ImageVerify.sidecarDifferences(
            "vendor/bin/a 0 0 755\nvendor/bin/b 0 0 755 capabilities=0x0c0",
            "",
            "vendor/bin/a 0 0 755 capabilities=0x0\nvendor/bin/b 0 0 755 capabilities=0xc0",
            "",
        )
        assertEquals(emptyList(), diffs)
    }

    @Test
    fun imageCheckFindsAFileTheImageLost() {
        val stat = "0 0 755 /mnt/x\n0 0 644 /mnt/x/bin/a b\n0 0 644 /mnt/x/bin/c"
        assertEquals(listOf("bin/d"), ImageVerify.missingFromImage("bin/a b\nbin/c\nbin/d\n", stat, "/mnt/x"))
    }

    @Test
    fun updaterScriptEscapesItsStrings() {
        val text = UpdaterScriptWriter.generate(f.targetGlobal, f.targetGlobal.packagePolicy, "a\"b\\c")
        assertTrue(text.contains("a\\\"b\\\\c"), text)
    }

    @Test
    fun theFlashableListMatchesTheDynamicPartitions() {
        val dynamic = f.targetGlobal.dynamicPartitions
        val check = { list: List<String> ->
            val policy = f.targetGlobal.packagePolicy.copy(flashablePartitions = list)
            DeviceConfigReadiness.check(f.targetGlobal.copy(packagePolicy = policy), f.snapshotGlobalOta)
                .filter { "flashablePartitions" in it.fieldPath }
        }
        assertEquals(emptyList(), check(dynamic))
        assertTrue(check(dynamic + "efs").isNotEmpty())
        assertTrue(check(dynamic + dynamic.first()).isNotEmpty())
        assertTrue(check(dynamic.drop(1)).isNotEmpty())
    }

    @Test
    fun commentsInARamdiskFstabLeaveTheDeviceTreeInUse() {
        val text = "boot.img:fstab.test\t# only a comment\n" +
            "vendor_boot.img:dtb\tsystem /system erofs ro logical\n"
        assertEquals(1, MountTable.parse(text).size)
        assertTrue(MountTable.deviceTree(text).inUse)
    }

    @Test
    fun aRealRamdiskFstabStillWins() {
        val text = "vendor_boot.img:fstab.test\tsystem /system erofs ro logical\n" +
            "vendor_boot.img:dtb\tvendor /vendor erofs ro logical\n"
        assertEquals(listOf("/system"), MountTable.parse(text).map { it.mountPoint })
    }

    @Test
    fun anUnreadableEmbeddedConfigIsUnknown() {
        assertNull(KernelFacts.parse("version 6.1.0\n")!!.ramdiskFormats)
    }

    @Test
    fun aLinkIsFollowedBeforeTheDotDotAfterIt() {
        val listing = LinkCheck.Listing(
            mapOf("/start" to "/dir/link/../file", "/dir/link" to "/other/sub"),
            emptySet(),
            setOf("system"),
        )
        assertEquals(LinkCheck.Ending.InTree("system/other/file"), LinkCheck.resolve(listing)["/start"])
    }

    @Test
    fun linksStillResolveAndLoopsStillEnd() {
        val listing = LinkCheck.Listing(
            mapOf("/a" to "b/../c", "/b" to "d", "/x" to "y", "/y" to "x", "/up" to "../../etc/hosts"),
            emptySet(),
            setOf("system"),
        )
        val ends = LinkCheck.resolve(listing)
        assertEquals(LinkCheck.Ending.InTree("system/c"), ends["/a"])
        assertEquals(LinkCheck.Ending.Loop, ends["/x"])
        assertEquals(LinkCheck.Ending.InTree("system/etc/hosts"), ends["/up"])
    }

    @Test
    fun aSuperLayoutIsOneADeviceCouldHold() {
        val group = listOf(SuperGroup("known", 50000))
        assertFalse(SuperLayoutPlanner.plan(100000, group, listOf(SuperPartition("system", "missing", 4096))).fits)
        val twice = listOf(SuperPartition("system", "known", 4096), SuperPartition("system", "known", 4096))
        assertFalse(SuperLayoutPlanner.plan(100000, group, twice).fits)
        assertTrue(SuperLayoutPlanner.plan(100000, group, twice.take(1)).fits)
    }

    @Test
    fun everyInstalledCopyOfAPackageIsMatched() {
        val items = listOf(
            RemovalItem("one", "one", "system/app/A", "x.y"),
            RemovalItem("two", "two", "product/app/A", "x.y"),
        )
        assertEquals(2, RemovalListMatcher.match(RemovalList("test", listOf("x.y")), items).effectiveRemovals.size)
    }

    @Test
    fun anAppendToTheRootCannotDeclareAResourceTwice() {
        val appended = XmlChange(XmlChangeKind.APPEND, XmlMatch(tag = "resources"), "<string name=\"a\">new</string>")
        assertIs<EditResult.Failed>(XmlResourceEditor.apply(strings, listOf(appended), "strings.xml"))
    }

    @Test
    fun aTypedItemIsTheResourceItNames() {
        val item = XmlChange(XmlChangeKind.APPEND, fragment = "<item type=\"string\" name=\"a\">new</item>")
        assertIs<EditResult.Failed>(XmlResourceEditor.apply(strings, listOf(item), "strings.xml"))
    }

    @Test
    fun aChecksumLineGivesTheRealFileName() {
        val hash = "a".repeat(64)
        assertEquals("a/b.smali" to hash, CompareWorkbench.checksumLine("$hash  ./a/b.smali"))
        assertEquals("a\\b\nc" to hash, CompareWorkbench.checksumLine("\\$hash  ./a\\\\b\\nc"))
        assertNull(CompareWorkbench.checksumLine("no hash here"))
    }
}
