/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.text

import org.ide.lti.core.domain.pipeline.toolkit.CoverageFix
import org.ide.lti.core.domain.pipeline.toolkit.CoverageGate
import org.ide.lti.core.domain.pipeline.toolkit.CoverageRule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlatformMetadataTest {
    // Lines of PQ84P01's system/etc/selinux/plat_file_contexts and vendor_file_contexts, as written there.
    private val plat = """
        /system(/.*)?		u:object_r:system_file:s0
        /system/lib(64)?(/.*)?		u:object_r:system_lib_file:s0
        /system/bin/toybox	--	u:object_r:toolbox_exec:s0
        /system/bin/logcatd	--	u:object_r:logcat_exec:s0
        /system/bin/sh		--	u:object_r:shell_exec:s0
        /system/etc/selinux/mapping/[0-9]+\.[0-9]+(\.compat)?\.cil       u:object_r:sepolicy_file:s0
        /system/etc/selinux/plat_file_contexts  u:object_r:file_contexts_file:s0
    """.trimIndent()
    private val vendor = """
        /(vendor|system/vendor)(/.*)?  u:object_r:vendor_file:s0
        /vendor/bin/cnd                 u:object_r:vendor_cnd_exec:s0
    """.trimIndent()
    private val rules = FileContextRules.parse(listOf(plat, vendor))

    @Test
    fun anExactPathWinsOverTheBroadRuleBeforeIt() {
        assertEquals("u:object_r:logcat_exec:s0", rules.label("/system/bin/logcatd", EntryKind.File))
        assertEquals("u:object_r:vendor_cnd_exec:s0", rules.label("/vendor/bin/cnd", EntryKind.File))
    }

    @Test
    fun theLastMatchingRegexWinsAndATypeLimitsARule() {
        assertEquals("u:object_r:system_lib_file:s0", rules.label("/system/lib64/libfoo.so", EntryKind.File))
        assertEquals("u:object_r:system_file:s0", rules.label("/system/bin/newtool", EntryKind.File))
        // `--` is for files: a folder of that name falls back to /system(/.*)?.
        assertEquals("u:object_r:system_file:s0", rules.label("/system/bin/sh", EntryKind.Directory))
        assertEquals("u:object_r:sepolicy_file:s0", rules.label("/system/etc/selinux/mapping/34.0.cil", EntryKind.File))
        assertEquals("u:object_r:vendor_file:s0", rules.label("/vendor/etc/init/foo.rc", EntryKind.File))
    }

    @Test
    fun noneAndUnmatchedPathsGiveNoLabel() {
        val none = FileContextRules.parse(listOf("/data/foo(/.*)? <<none>>"))
        assertNull(none.label("/data/foo/x", EntryKind.File))
        assertNull(rules.label("/odm/etc/x", EntryKind.File))
    }

    @Test
    fun metaCharactersFollowLibselinux() {
        assertTrue(FileContextRules.hasMetaChars("/system(/.*)?"))
        assertTrue(FileContextRules.hasMetaChars("/vendor/lib/libfoo.so"))
        assertTrue(!FileContextRules.hasMetaChars("/system/bin/sh"))
        assertTrue(!FileContextRules.hasMetaChars("/vendor/lib/libfoo\\.so"))
    }

    @Test
    fun compiledRulesDecodeBackToText() {
        // PCRE2 build, format 4 (Android 8): the compiled regex blobs are skipped.
        val pcre2 = compiled(version = 4, pcre = "10.42 2022-12-11", pcre1 = false)
        assertEquals(
            "/system(/.*)?\tu:object_r:system_file:s0\n/system/bin/sh\t--\tu:object_r:shell_exec:s0\n",
            CompiledFileContexts.decode(pcre2),
        )
        // PCRE build, format 2 (Android 7): no mode, no prefix length, regex plus study data.
        val pcre1 = compiled(version = 2, pcre = "8.38 2015-11-23", pcre1 = true)
        assertEquals(
            "/system(/.*)?\tu:object_r:system_file:s0\n/system/bin/sh\tu:object_r:shell_exec:s0\n",
            CompiledFileContexts.decode(pcre1),
        )
        assertTrue(CompiledFileContexts.isCompiled(pcre2))
        assertFailsWith<IllegalArgumentException> { CompiledFileContexts.decode(plat.encodeToByteArray()) }
        assertFailsWith<IllegalArgumentException> { CompiledFileContexts.decode(pcre2.copyOf(pcre2.size - 3)) }
    }

    @Test
    fun theDeviceTableIsReadAsAndroidWritesIt() {
        // The first entry of PQ84P01's vendor/etc/fs_config_files, byte for byte.
        val entry = table("system/vendor/bin/cnd", mode = 0x1ED, uid = 1000, gid = 1000, caps = 0x1000001400L)
        val decoded = OwnerRules.decodeTable(entry + table("vendor/bin/cnd", 0x1ED, 1000, 1000, 0x1000001400L))
        assertEquals(2, decoded.size)
        assertEquals(OwnerRules.Entry("system/vendor/bin/cnd", Owner(1000, 1000, 0x1ED, 0x1000001400L)), decoded[0])
        // A damaged tail ends the list.
        assertEquals(1, OwnerRules.decodeTable(entry + byteArrayOf(0x40, 0, 1)).size)
    }

    @Test
    fun ownersFollowTheDeviceThenAndroidThenRoot() {
        val device = OwnerRules(
            dirs = listOf(OwnerRules.Entry("firmware/", Owner(1000, 1000, 0x1F9))),
            files = listOf(OwnerRules.Entry("vendor/bin/cnd", Owner(1000, 1000, 0x1ED, 0x1000001400L))),
        )
        fun fields(path: String, dir: Boolean) = device.owner(path, dir).first.fsConfigFields
        // PQ84P01's own lines for these paths.
        assertEquals("1000 1000 755 capabilities=0x1000001400", fields("vendor/bin/cnd", false))
        assertEquals("0 2000 751 capabilities=0x0", fields("system/bin", true))
        assertEquals("0 2000 751 capabilities=0x0", fields("system/bin/hw", true))
        assertEquals("0 2000 755 capabilities=0x0", fields("system/bin/newtool", false))
        assertEquals("0 2000 755 capabilities=0x0", fields("vendor/etc", true))
        assertEquals("0 0 755 capabilities=0x0", fields("system/etc", true))
        assertEquals("0 0 644 capabilities=0x0", fields("product/app/Foo/Foo.apk", false))
        assertEquals("1000 1000 771 capabilities=0x0", fields("firmware/image", true))
        assertEquals(OwnerRules.Source.Default, device.owner("odm/etc/x.xml", false).second)
    }

    @Test
    fun aNewFileIsLabelledAndOwnedLikeAndroidsBuild() {
        val platform = PlatformMetadata(rules, OwnerRules.ANDROID_ONLY)
        val fs = SidecarEditor.withFsConfig(
            "system/bin 0 2000 751 capabilities=0x0\n",
            "system",
            "system/bin/newd",
            false,
            platform = platform,
        )
        assertEquals(
            "system/bin 0 2000 751 capabilities=0x0\nsystem 0 0 755 capabilities=0x0\n" +
                "system/bin/newd 0 2000 755 capabilities=0x0\n",
            fs,
        )
        val ctx = SidecarEditor.withFileContext(
            "/system/bin u:object_r:system_file:s0\n",
            "system",
            "system/bin/logcatd",
            platform = platform,
        )
        assertTrue("/system/bin/logcatd u:object_r:logcat_exec:s0" in ctx, ctx)
        // An explicit label still wins over the rules.
        val own = SidecarEditor.withFileContext(
            "",
            "system",
            "system/bin/logcatd",
            "u:object_r:mine:s0",
            platform = platform,
        )
        assertTrue("/system/bin/logcatd u:object_r:mine:s0" in own, own)
    }

    @Test
    fun theFillSaysWhereEachPartComesFrom() {
        val platform = PlatformMetadata(rules, OwnerRules.ANDROID_ONLY)
        val rom = CoverageFix.preview("", "system", "system/bin/logcatd", EntryKind.File, platform = platform)
        assertEquals("0 2000 755", rom.owner)
        assertEquals(CoverageFix.Source.Android, rom.ownerFrom)
        assertEquals(CoverageFix.Source.RomRules, rom.labelFrom)
        val mine = CoverageRule.parse("/system/bin/logcatd 1036 1036 0550 u:object_r:mine:s0").first
        val ruled = CoverageFix.preview("", "system", "system/bin/logcatd", EntryKind.File, mine, platform)
        assertEquals(CoverageFix.Source.YourRule, ruled.ownerFrom)
        assertEquals("u:object_r:mine:s0", ruled.label)
        val folder = CoverageFix.preview(
            "/odm u:object_r:vendor_file:s0\n",
            "odm",
            "etc/x",
            EntryKind.File,
            platform = platform,
        )
        assertEquals(CoverageFix.Source.Folder, folder.labelFrom)
        assertEquals("u:object_r:vendor_file:s0", folder.label)
    }

    @Test
    fun aNameWithASpaceCannotBeBuilt() {
        val result = CoverageGate.check("product", listOf("media/My Song.ogg"), "", "")
        assertEquals(listOf("media/My Song.ogg"), result.unbuildableNames)
        assertTrue(!result.isClean)
        // The fill leaves it alone: no line could describe it.
        assertEquals("", CoverageFix.fixFsConfig("", "product", listOf("media/My Song.ogg")))
    }

    @Test
    fun loadReadsTheWorkTreesFiles() {
        val files = mapOf(
            "system/system/etc/selinux/plat_file_contexts" to plat.encodeToByteArray(),
            "vendor/etc/fs_config_files" to table("vendor/bin/cnd", 0x1ED, 1000, 1000, 0x1000001400L),
        )
        val loaded = PlatformMetadata.load { files[it] }
        assertEquals("u:object_r:shell_exec:s0", loaded.label("system", "system/bin/sh", EntryKind.File))
        assertEquals(Owner(1000, 1000, 0x1ED, 0x1000001400L), loaded.owner("vendor", "bin/cnd", false).first)
    }

    /** One fs_config table entry: header, path and its NUL, padded to 8 bytes as Android writes it. */
    @Test
    fun deviceRulesMatchAsAndroidsFnmatchWithPartitionAliases() {
        val o = Owner(1000, 2000, 0x1ED)
        val rules = OwnerRules(
            dirs = emptyList(),
            files = listOf(
                OwnerRules.Entry("vendor/bin/hw/android.hardware.?ensors*", o),
                OwnerRules.Entry("vendor/lib[0-9]*/libfoo.so", o),
                OwnerRules.Entry("system/vendor/bin/helper", o),
            ),
        )
        fun source(path: String) = rules.owner(path, isDirectory = false).second
        assertEquals(OwnerRules.Source.Device, source("vendor/bin/hw/android.hardware.sensors@2.0"))
        assertEquals(OwnerRules.Source.Device, source("vendor/lib64/libfoo.so"))
        assertEquals(OwnerRules.Source.Default, source("vendor/libx/libfoo.so"))
        // system/vendor/ and vendor/ name the same place, both ways.
        assertEquals(OwnerRules.Source.Device, source("vendor/bin/helper"))
        assertEquals(OwnerRules.Source.Device, source("system/vendor/lib64/libfoo.so"))
    }

    @Test
    fun aRecordWithoutItsNulEndsTheTable() {
        val entry = table("vendor/bin/cnd", 0x1ED, 1000, 1000, 0)
        val unterminated = entry.copyOf().also { b ->
            for (i in HEADER + "vendor/bin/cnd".length until b.size) b[i] = 'x'.code.toByte()
        }
        assertEquals(1, OwnerRules.decodeTable(entry).size)
        assertEquals(0, OwnerRules.decodeTable(unterminated).size)
    }

    @Test
    fun aTabSeparatedSidecarLineIsRenamed() {
        val text = "vendor/bin/a\t0 2000 0755\nvendor/bin/z 0 0 0644\n"
        assertEquals(
            "vendor/bin/b\t0 2000 0755\nvendor/bin/z 0 0 0644\n",
            SidecarEditor.withRenamedFsConfig(text, "vendor", "bin/a", "bin/b"),
        )
    }

    private fun table(path: String, mode: Int, uid: Int, gid: Int, caps: Long): ByteArray {
        val name = path.encodeToByteArray()
        val length = (HEADER + name.size + 1 + 7) / 8 * 8
        val b = ByteArray(length)
        fun u16(at: Int, v: Int) {
            b[at] = (v and 0xFF).toByte()
            b[at + 1] = (v shr 8 and 0xFF).toByte()
        }
        u16(0, length)
        u16(2, mode)
        u16(4, uid)
        u16(6, gid)
        for (i in 0 until 8) b[8 + i] = (caps shr (8 * i) and 0xFF).toByte()
        name.copyInto(b, HEADER)
        return b
    }

    /** Two rules (a regex and an exact `--` path) in libselinux's compiled form. */
    private fun compiled(version: Int, pcre: String, pcre1: Boolean): ByteArray {
        val out = mutableListOf<Byte>()
        fun u32(v: Long) = (0 until 4).forEach { out += (v shr (8 * it) and 0xFF).toByte() }
        fun bytes(s: String, nul: Boolean) {
            val b = s.encodeToByteArray() + if (nul) byteArrayOf(0) else byteArrayOf()
            u32(b.size.toLong())
            out += b.toList()
        }
        u32(CompiledFileContexts.MAGIC)
        u32(version.toLong())
        if (version >= 2) bytes(pcre, nul = false)
        u32(1) // one stem
        bytes("/system", nul = false)
        out += 0
        u32(2)
        listOf(
            Triple("/system(/.*)?", "u:object_r:system_file:s0", 0),
            Triple("/system/bin/sh", "u:object_r:shell_exec:s0", 0x8000),
        )
            .forEach { (regex, context, mode) ->
                bytes(context, nul = true)
                bytes(regex, nul = true)
                if (version >= 3) u32(mode.toLong())
                u32(0)
                u32(if (regex.contains('(')) 1 else 0)
                if (version >= 4) u32(regex.length.toLong())
                bytes("compiled", nul = false)
                if (pcre1) bytes("study", nul = false)
            }
        return out.toByteArray()
    }

    private companion object {
        const val HEADER = 16
    }
}
