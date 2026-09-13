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

        assertFalse(ToolCapabilities.isRecipeSupported("gh"))
        assertFalse(ToolCapabilities.isRecipeSupported("unknown-tool"))

        assertNull(ToolCapabilities.unavailableReasonFor("lpunpack"))
        assertEquals(
            ToolCapabilities.REPAIR_UNAVAILABLE_REASON,
            ToolCapabilities.unavailableReasonFor("gh"),
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
        assertNull(ToolCapabilities.recipeGroupFor("gh"))
    }

    @Test
    fun testProbeArgumentsAndAcceptedExitCodes() {
        // Default contract: `--help` must exit 0.
        val args = ToolCapabilities.probeArgumentsFor("lpunpack", "/bin/lpunpack")
        assertEquals(listOf("/bin/lpunpack", "--help"), args)
        assertEquals(setOf(0), ToolCapabilities.acceptedExitCodesFor("lpunpack"))
        assertTrue(ToolCapabilities.isAcceptedExitCode("lpunpack", 0))
        assertFalse(ToolCapabilities.isAcceptedExitCode("lpunpack", 1))

        // Per-tool overrides: the inventory, not the caller, decides how a binary proves it runs.
        assertEquals(listOf("/bin/mke2fs", "-V"), ToolCapabilities.probeArgumentsFor("mke2fs", "/bin/mke2fs"))
        assertEquals(listOf("version"), ToolCapabilities.probeArgumentsFor("adb"))
        assertEquals(listOf("/bin/simg2img"), ToolCapabilities.probeArgumentsFor("simg2img", "/bin/simg2img"))
        assertEquals(setOf(255), ToolCapabilities.acceptedExitCodesFor("simg2img"))
        assertTrue(ToolCapabilities.isAcceptedExitCode("simg2img", 255))
        assertFalse(ToolCapabilities.isAcceptedExitCode("simg2img", 0))

        // Unknown tools fall back to the conservative default rather than being optimistically accepted.
        assertEquals(listOf("--help"), ToolCapabilities.probeArgumentsFor("gh"))
        assertEquals(setOf(0), ToolCapabilities.acceptedExitCodesFor("gh"))
        assertNull(ToolCapabilities.capabilityFor("gh"))

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
}
