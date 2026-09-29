/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.setup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ToolCapabilitiesTest {

    @Test
    fun testSupportedAndUnsupportedRecipes() {
        assertTrue(ToolCapabilities.isRecipeSupported("adb"))
        assertTrue(ToolCapabilities.isRecipeSupported("lpmake"))
        assertTrue(ToolCapabilities.isRecipeSupported("lpunpack"))
        assertTrue(ToolCapabilities.isRecipeSupported("mkfs.erofs"))
        assertTrue(ToolCapabilities.isRecipeSupported("dump.erofs"))
        assertTrue(ToolCapabilities.isRecipeSupported("img2sdat"))
        assertTrue(ToolCapabilities.isRecipeSupported("signapk"))
        assertTrue(ToolCapabilities.isRecipeSupported("payload-dumper-go"))

        assertTrue(ToolCapabilities.isRecipeSupported("gh"))
        assertFalse(ToolCapabilities.isRecipeSupported("unknown-tool"))

        assertNull(ToolCapabilities.unavailableReasonFor("lpunpack"))
        assertEquals(
            ToolCapabilities.REPAIR_UNAVAILABLE_REASON,
            ToolCapabilities.unavailableReasonFor("unknown-tool"),
        )
    }

    @Test
    fun testRecipeGroupMapping() {
        assertEquals(ToolCapabilities.GROUP_ANDROID_TOOLS, ToolCapabilities.recipeGroupFor("adb"))
        assertEquals(ToolCapabilities.GROUP_ANDROID_TOOLS, ToolCapabilities.recipeGroupFor("lpunpack"))
        assertEquals(ToolCapabilities.GROUP_ANDROID_TOOLS, ToolCapabilities.recipeGroupFor("lpmake"))
        assertEquals(ToolCapabilities.GROUP_EROFS_UTILS, ToolCapabilities.recipeGroupFor("mkfs.erofs"))
        assertEquals(ToolCapabilities.GROUP_EROFS_UTILS, ToolCapabilities.recipeGroupFor("dump.erofs"))
        assertEquals(ToolCapabilities.GROUP_IMG2SDAT, ToolCapabilities.recipeGroupFor("img2sdat"))
        assertEquals(ToolCapabilities.GROUP_APKTOOL, ToolCapabilities.recipeGroupFor("apktool"))
        assertEquals(ToolCapabilities.GROUP_SIGNAPK, ToolCapabilities.recipeGroupFor("signapk"))
        assertEquals(ToolCapabilities.GROUP_PAYLOAD_DUMPER_GO, ToolCapabilities.recipeGroupFor("payload-dumper-go"))
        assertEquals(ToolCapabilities.GROUP_GH, ToolCapabilities.recipeGroupFor("gh"))
        assertNull(ToolCapabilities.recipeGroupFor("unknown-tool"))
    }

    @Test
    fun testProbeArgumentsAndAcceptedExitCodes() {
        // Default contract: `--help` must exit 0.
        val args = ToolCapabilities.probeArgumentsFor("lpdump", "/bin/lpdump")
        assertEquals(listOf("/bin/lpdump", "--help"), args)
        assertEquals(setOf(0), ToolCapabilities.acceptedExitCodesFor("lpdump"))
        assertTrue(ToolCapabilities.isAcceptedExitCode("lpdump", 0))
        assertFalse(ToolCapabilities.isAcceptedExitCode("lpdump", 1))

        // Per-tool overrides: the inventory, not the caller, decides how a binary proves it runs.
        assertEquals(listOf("/bin/mke2fs", "-V"), ToolCapabilities.probeArgumentsFor("mke2fs", "/bin/mke2fs"))
        assertEquals(listOf("version"), ToolCapabilities.probeArgumentsFor("adb"))
        assertEquals(listOf("/bin/simg2img"), ToolCapabilities.probeArgumentsFor("simg2img", "/bin/simg2img"))
        assertEquals(setOf(1, 255), ToolCapabilities.acceptedExitCodesFor("simg2img"))
        assertTrue(ToolCapabilities.isAcceptedExitCode("simg2img", 255))
        assertFalse(ToolCapabilities.isAcceptedExitCode("simg2img", 0))
        // Measured: liblp tools exit 64 (EX_USAGE) on --help; erofsfuse (FUSE help) exits 1.
        assertEquals(setOf(0, 64), ToolCapabilities.acceptedExitCodesFor("lpmake"))
        assertEquals(setOf(0, 1), ToolCapabilities.acceptedExitCodesFor("erofsfuse"))

        // Unknown tools fall back to the conservative default rather than being optimistically accepted.
        assertEquals(listOf("--help"), ToolCapabilities.probeArgumentsFor("unknown-tool"))
        assertEquals(setOf(0), ToolCapabilities.acceptedExitCodesFor("unknown-tool"))
        assertNull(ToolCapabilities.capabilityFor("unknown-tool"))

        val capability = ToolCapabilities.capabilityFor("mkfs.erofs")
        assertNotNull(capability)
        assertEquals(ToolCapabilities.GROUP_EROFS_UTILS, capability.recipeGroup)
        assertEquals(listOf("/x/mkfs.erofs", "--help"), capability.probeArgv("/x/mkfs.erofs"))
    }

    @Test
    fun testEveryInventoryToolBelongsToExactlyOneGroup() {
        val groups = ToolCapabilities.allRecipeGroups()
        val seen = mutableMapOf<String, String>()
        for (group in groups) {
            for (toolId in ToolCapabilities.toolsForRecipeGroup(group)) {
                val previous = seen.put(toolId, group)
                assertNull(previous, "$toolId is owned by both $previous and $group")
                assertEquals(group, ToolCapabilities.recipeGroupFor(toolId))
            }
        }
        assertEquals(ToolCapabilities.SUPPORTED_TOOL_IDS, seen.keys)
    }

    @Test
    fun testInvalidateOwningGroupForRepair() {
        val evidenceMap = mapOf(
            "adb" to ToolEvidence(toolId = "adb", isVerified = true),
            "lpunpack" to ToolEvidence(toolId = "lpunpack", isVerified = true),
            "mkfs.erofs" to ToolEvidence(toolId = "mkfs.erofs", isVerified = true),
        )

        // Repairing lpunpack invalidates android-tools only (adb + lpunpack)
        val invalidated = ToolCapabilities.invalidateOwningGroupForRepair("lpunpack", evidenceMap)
        assertFalse(invalidated["adb"]?.isVerified == true)
        assertFalse(invalidated["lpunpack"]?.isVerified == true)
        assertTrue(invalidated["mkfs.erofs"]?.isVerified == true, "mkfs.erofs must remain verified")
    }

    @Test
    fun everyToolTheToolsPageListsHasARecipe() {
        // The Tools page shows the matrix; a tool missing from the inventory reads "can't be rebuilt".
        val withoutRecipe = ToolchainSetupState.defaultToolsMatrix().map { it.id }
            .filterNot { ToolCapabilities.isRecipeSupported(it) }
        assertEquals(emptyList(), withoutRecipe, "Every listed tool must be buildable or downloadable by setup")
        assertEquals(listOf("version"), ToolCapabilities.probeArgumentsFor("gh"))
    }

    @Test
    fun f2fsAndSdkBuildToolsBelongToAndroidToolsWithTheirRealProbes() {
        for (id in listOf("make_f2fs", "sload_f2fs", "mkf2fsuserimg", "aapt2", "zipalign")) {
            assertEquals(ToolCapabilities.GROUP_ANDROID_TOOLS, ToolCapabilities.recipeGroupFor(id), id)
        }
        // Exit codes measured on the built binaries: `--help` is not a valid option for these.
        assertEquals(listOf("-V"), ToolCapabilities.probeArgumentsFor("make_f2fs"))
        assertEquals(listOf("version"), ToolCapabilities.probeArgumentsFor("aapt2"))
        assertEquals(setOf(2), ToolCapabilities.acceptedExitCodesFor("zipalign"))
    }
}
