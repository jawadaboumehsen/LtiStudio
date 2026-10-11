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

import org.ide.lti.core.model.setup.ToolRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ToolGroupCatalogTest {
    @Test
    fun catalogYieldsExactlyTheSevenExpectedGroups() {
        val expectedGroupIds =
            setOf(
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
        val expectedPins =
            mapOf(
                "android-tools" to
                    Triple(
                        "https://github.com/UN1CA/external_android-tools.git",
                        ToolRef.Branch("sixteen"),
                        "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
                    ),
                "apktool" to
                    Triple(
                        "https://github.com/iBotPeaches/Apktool.git",
                        ToolRef.Tag("v3.0.2"),
                        "1c1d15b070c2138d958fa8b685d430c49c4bb117",
                    ),
                "erofs-utils" to
                    Triple(
                        "https://github.com/sekaiacg/erofs-tools.git",
                        ToolRef.Tag("v1.8.10-251217"),
                        "7274417816e3adfe0cd6d4a8ff194ec5f41268f4",
                    ),
                "img2sdat" to
                    Triple(
                        "https://github.com/UN1CA/external_img2sdat.git",
                        ToolRef.Branch("sixteen"),
                        "601317ed0bf502eb8facdd048f2d75dc75ffcd53",
                    ),
                "signapk" to
                    Triple(
                        "https://github.com/UN1CA/external_signapk.git",
                        ToolRef.Branch("sixteen"),
                        "cb88f4b27d8a07ca101eade5d40a6893920cb55e",
                    ),
            )

        for ((groupId, pin) in expectedPins) {
            val group = ToolGroupCatalog.group(ToolGroupId(groupId))
            assertNotNull(group, "Group $groupId must exist in catalog")
            val source = group.source
            assertTrue(source is ToolSource.Git, "Group $groupId must have Git source")
            assertEquals(pin.first, source.recommendedUrl, "Group $groupId URL mismatch")
            assertEquals(pin.second, source.pinnedRef, "Group $groupId ref mismatch")
            assertEquals(pin.third, source.pinnedCommit, "Group $groupId commit mismatch")
        }
    }

    @Test
    fun catalogPreservesExactReleaseVersionsAndSha256() {
        val payloadDumperGroup = ToolGroupCatalog.group(ToolGroupId("payload-dumper-go"))
        assertNotNull(payloadDumperGroup)
        val payloadRecipe = payloadDumperGroup.recipe
        assertTrue(payloadRecipe is RecipeConfig.Release)
        val payloadVersion = payloadRecipe.versions.first { it.version == "1.3.0" }
        assertEquals("1b973d521cda71268ddbaa0388fe28d6a94b370c58e21352137c5f9077b0bfe7", payloadVersion.sha256)
        assertEquals("payload-dumper-go", payloadVersion.archiveMember)

        val ghGroup = ToolGroupCatalog.group(ToolGroupId("gh"))
        assertNotNull(ghGroup)
        val ghRecipe = ghGroup.recipe
        assertTrue(ghRecipe is RecipeConfig.Release)
        val ghVersion = ghRecipe.versions.first { it.version == "2.97.0" }
        assertEquals("a2c9b8497e1f85b1ad0dfcb78b5a622e098801b8e461e459e88e1ee12f018112", ghVersion.sha256)
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
            val expectedFileName =
                when (output.kind) {
                    ToolOutputKind.JAR -> "${output.toolId}.jar"
                    ToolOutputKind.NATIVE, ToolOutputKind.SCRIPT -> output.toolId
                }
            assertEquals(expectedFileName, output.file, "Tool ${output.toolId} installed file name must match")
        }
    }

    @Test
    fun toolIdsAreGloballyUniqueAndEachToolHasExactlyOneOwnerGroup() {
        val allTools = ToolGroupCatalog.allGroups.flatMap { g -> g.outputs.map { it.toolId to g.id } }
        val duplicates = allTools.groupBy { it.first }.filter { it.value.size > 1 }
        assertTrue(duplicates.isEmpty(), "Found duplicate tool outputs across groups: $duplicates")
    }

    @Test
    fun toolPathsAreRelativeAndContainedWithoutPathTraversal() {
        for (group in ToolGroupCatalog.allGroups) {
            for (out in group.outputs) {
                kotlin.test.assertFalse(out.file.startsWith("/"), "Path must be relative: ${out.file}")
                kotlin.test.assertFalse(out.file.contains(".."), "Path must not contain traversal: ${out.file}")
            }
        }
    }

    @Test
    fun demoGroupOfExistingRecipeTypeAddedWithEntryOnly() {
        val demoGroup =
            ToolGroup(
                id = ToolGroupId("demo-tool"),
                source =
                ToolSource.Git(
                    recommendedUrl = "https://github.com/example/demo.git",
                    pinnedRef = ToolRef.Tag("v1.0"),
                    pinnedCommit = "1111111111111111111111111111111111111111",
                ),
                recipe =
                RecipeConfig.CMake(
                    sourceSubdir = ".",
                    buildSubdir = "build",
                    cmakeArgs = listOf("-DBUILD_DEMO=ON"),
                    revision = 1,
                ),
                layout = listOf("CMakeLists.txt"),
                outputs =
                listOf(
                    ToolOutput(
                        toolId = "demo-bin",
                        file = "demo-bin",
                        kind = ToolOutputKind.NATIVE,
                        probe = ToolProbe(listOf("--help"), setOf(0)),
                    ),
                ),
                buildPackages = listOf("cmake", "build-essential"),
            )
        assertEquals("demo-tool", demoGroup.id.value)
        assertEquals(listOf("demo-bin"), demoGroup.outputs.map { it.toolId })
        assertEquals(listOf("cmake", "build-essential"), demoGroup.packages)
    }
}
