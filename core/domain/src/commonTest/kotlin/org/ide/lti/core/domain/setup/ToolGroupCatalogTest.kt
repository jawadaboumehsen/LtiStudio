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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolGroupCatalogTest {

    @Test
    fun catalogYieldsExactlyTheSevenExpectedGroups() {
        val expectedGroupIds = setOf(
            "android-tools",
            "erofs-utils",
            "img2sdat",
            "apktool",
            "signapk",
            "payload-dumper-go",
            "gh",
        )
        val actualGroupIds = ToolGroupCatalog.allGroups.map { it.id.value }.toSet()
        assertEquals(expectedGroupIds, actualGroupIds)
        assertEquals(7, ToolGroupCatalog.allGroups.size)
    }

    @Test
    fun catalogPreservesExactGitPins() {
        val expectedPins = mapOf(
            "android-tools" to Pair(
                "https://github.com/UN1CA/external_android-tools.git",
                "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
            ),
            "apktool" to Pair(
                "https://github.com/iBotPeaches/Apktool.git",
                "1c1d15b070c2138d958fa8b685d430c49c4bb117",
            ),
            "erofs-utils" to Pair(
                "https://github.com/sekaiacg/erofs-tools.git",
                "7274417816e3adfe0cd6d4a8ff194ec5f41268f4",
            ),
            "img2sdat" to Pair(
                "https://github.com/UN1CA/external_img2sdat.git",
                "601317ed0bf502eb8facdd048f2d75dc75ffcd53",
            ),
            "signapk" to Pair(
                "https://github.com/UN1CA/external_signapk.git",
                "cb88f4b27d8a07ca101eade5d40a6893920cb55e",
            ),
        )

        for ((groupId, pin) in expectedPins) {
            val group = ToolGroupCatalog.group(ToolGroupId(groupId))
            assertNotNull(group, "Group $groupId must exist in catalog")
            val source = group.source
            assertTrue(source is ToolSource.Git, "Group $groupId must have Git source")
            assertEquals(pin.first, source.recommendedUrl, "Group $groupId URL mismatch")
            assertEquals(pin.second, source.recommendedCommit, "Group $groupId commit mismatch")
        }
    }

    @Test
    fun catalogPreservesExactReleaseVersionsAndSha256() {
        val payloadDumperGroup = ToolGroupCatalog.group(ToolGroupId("payload-dumper-go"))
        assertNotNull(payloadDumperGroup)
        val payloadRecipe = payloadDumperGroup.recipe
        assertTrue(payloadRecipe is RecipeConfig.Release)
        val payloadVersion = payloadRecipe.versions.first { it.version == "1.3.0" }
        assertEquals("4abca6f57158f510d15b940e0ac3ead59c9897c7f304bb22c310ad0283d00efd", payloadVersion.sha256)
        assertEquals("payload-dumper-go", payloadVersion.archiveMember)

        val ghGroup = ToolGroupCatalog.group(ToolGroupId("gh"))
        assertNotNull(ghGroup)
        val ghRecipe = ghGroup.recipe
        assertTrue(ghRecipe is RecipeConfig.Release)
        val ghVersion = ghRecipe.versions.first { it.version == "2.97.0" }
        assertEquals("141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409", ghVersion.sha256)
        assertEquals("gh_2.97.0_linux_amd64/bin/gh", ghVersion.archiveMember)
    }

    @Test
    fun catalogYieldsAllToolsWithCorrectProbesAndExitCodes() {
        val allOutputs = ToolGroupCatalog.allOutputs
        val toolIds = allOutputs.map { it.toolId }.toSet()

        assertTrue("adb" in toolIds)
        assertTrue("fastboot" in toolIds)
        assertTrue("mkfs.erofs" in toolIds)
        assertTrue("apktool" in toolIds)
        assertTrue("signapk" in toolIds)
        assertTrue("img2sdat" in toolIds)
        assertTrue("payload-dumper-go" in toolIds)
        assertTrue("gh" in toolIds)

        val adb = allOutputs.first { it.toolId == "adb" }
        assertEquals(listOf("version"), adb.probe.args)
        assertEquals(setOf(0), adb.probe.acceptedExitCodes)

        val fastboot = allOutputs.first { it.toolId == "fastboot" }
        assertEquals(listOf("--version"), fastboot.probe.args)

        val mke2fs = allOutputs.first { it.toolId == "mke2fs" }
        assertEquals(listOf("-V"), mke2fs.probe.args)

        val simg2img = allOutputs.first { it.toolId == "simg2img" }
        assertEquals(emptyList(), simg2img.probe.args)
        assertEquals(setOf(1, 255), simg2img.probe.acceptedExitCodes)

        val signapk = allOutputs.first { it.toolId == "signapk" }
        assertEquals(ToolOutputKind.JAR, signapk.kind)
        assertEquals(setOf(0, 2), signapk.probe.acceptedExitCodes)

        val apktool = allOutputs.first { it.toolId == "apktool" }
        assertEquals(ToolOutputKind.JAR, apktool.kind)
        assertEquals(listOf("--version"), apktool.probe.args)

        val erofsfuse = allOutputs.first { it.toolId == "erofsfuse" }
        assertEquals(setOf(0, 1), erofsfuse.probe.acceptedExitCodes)
    }

    @Test
    fun toolIdInstalledFileNameIsImmutable() {
        val allOutputs = ToolGroupCatalog.allOutputs
        for (output in allOutputs) {
            val expectedFileName = when (output.kind) {
                ToolOutputKind.JAR -> "${output.toolId}.jar"
                ToolOutputKind.NATIVE, ToolOutputKind.SCRIPT -> output.toolId
            }
            assertEquals(expectedFileName, output.file, "Tool ${output.toolId} installed file name must match")
        }
    }
}
