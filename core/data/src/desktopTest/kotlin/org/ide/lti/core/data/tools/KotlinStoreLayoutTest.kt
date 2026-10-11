/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import org.ide.lti.core.domain.tools.ResolvedSource
import org.ide.lti.core.model.setup.ToolRef
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KotlinStoreLayoutTest {
    @Test
    fun `selection script creates literal paths and valid manifest on a real shell`() {
        // Git Bash on Windows emulates symlinks and paths differently from WSL, so it cannot prove the
        // script; this runs only on a real Linux shell and is reported as skipped elsewhere.
        val canRun = File("/bin/sh").canExecute() && File.separatorChar == '/'
        org.junit.Assume.assumeTrue("Requires Linux /bin/sh", canRun)
        val shell = "/bin/sh"
        val root = Files.createTempDirectory("tool store '").toFile()
        try {
            val group = ToolGroupCatalog.allGroups.first { it.id.value == "erofs-utils" }
            val home = root.path
            val dir = "$home/LtiRomTools/bin/erofs-utils"
            val target = File(root, "LtiRomTools/bin/erofs-utils/v1").apply { mkdirs() }
            File(target, "version.json").writeText("{}")
            group.outputs.forEach { File(target, it.file).writeText("output") }
            File(root, ".ltirom").mkdirs()
            val script =
                KotlinStoreLayout.buildSwitchScript(
                    qTargetVerDir = KotlinStoreLayout.shellQuote("$dir/v1"),
                    groupDir = dir,
                    qVersionId = KotlinStoreLayout.shellQuote("v1"),
                    checkOuts = "true",
                    groupDef = group,
                ).replace("~/.ltirom", KotlinStoreLayout.shellQuote("$home/.ltirom"))
            val process = ProcessBuilder(shell, "-c", script).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().readText()
            assertEquals(0, process.waitFor(), output)
            val manifest = File(root, ".ltirom/tools.d/mkfs.erofs.json").readText()
            val execJson = Json.parseToJsonElement(manifest).jsonObject
            val executable = execJson.getValue("executable").jsonPrimitive.content
            assertEquals("$dir/current/mkfs.erofs", executable)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `an installed version is intact only if the catalog probes of its tools pass`() = runBlocking {
        val group = ToolGroupCatalog.allGroups.first { it.id.value == "erofs-utils" }
        val data =
            VersionData(
                schema = 1,
                group = group.id.value,
                id = "v1",
                label = "v1",
                source = with(KotlinStoreLayout) {
                    ResolvedSource.Git("https://x/y.git", ToolRef.Tag("v1"), "1".repeat(40)).toData()
                },
                recipe = RecipeData(type = "cmake", revision = 1),
                outputs = emptyList(),
                builtAt = "1",
                intact = true,
            )
        val json = Json { encodeDefaults = true }
        val lines = listOf("VER_START=v1") + json.encodeToString(VersionData.serializer(), data).lines() + "VER_END"
        val scripts = mutableListOf<String>()

        fun intact(passes: Boolean, probeVersion: String? = "v1") = runBlocking {
            KotlinStoreLayout.parseGroupInstalledVersions(
                groupDir = "/h/LtiRomTools/bin/erofs-utils",
                lines = lines,
                groupId = group.id,
                catalog = ToolGroupCatalog,
                json = json,
                probeVersion = probeVersion,
                exec = { command ->
                    scripts += command.last()
                    if (passes) Result.success("") else Result.failure(IllegalStateException("probe failed"))
                },
            ).single().intact
        }

        assertTrue(intact(passes = true))
        assertTrue(!intact(passes = false), "a failing check must make the version not intact")
        val script = scripts.first()
        group.outputs.forEach { out ->
            assertTrue("/v1/${out.file}'" in script && "timeout 3" in script, "no probe for ${out.file}")
        }
        assertTrue(script.contains("case \$rc in"), "exit codes are checked against the accepted set")
        assertTrue(script.contains("grep -Eiq"), "the output must identify the tool, not just the exit code")
        assertTrue(script.contains("wait \$pid"), "probes run in parallel and are all waited for")

        scripts.clear()
        intact(passes = true, probeVersion = "v2")
        assertTrue("timeout 3" !in scripts.single(), "only the version in use is probed during a status check")
    }

    @Test
    fun `a switch puts the previous link back if anything fails after the swap`() {
        val group = ToolGroupCatalog.allGroups.first { it.id.value == "erofs-utils" }
        val script =
            KotlinStoreLayout.buildSwitchScript(
                qTargetVerDir = "'/g/v2'",
                groupDir = "/g",
                qVersionId = "'v2'",
                checkOuts = "true",
                groupDef = group,
                toolsDDir = "'/h/tools.d'",
            )
        val lines = script.lines()
        val remembered = lines.indexOfFirst { it.startsWith("prev=") }
        val trapped = lines.indexOfFirst { it.startsWith("trap restore EXIT") }
        val swapped = lines.indexOfFirst { it.startsWith("swapped=1") }
        val firstManifestMove = lines.indexOfFirst { it.startsWith("mv '/h/tools.d'") }

        assertTrue(remembered in 0 until trapped, "the old target is read before anything changes")
        assertTrue(trapped < lines.indexOfFirst { it.startsWith("mv -T '/g'/current.tmp") }, "armed before the swap")
        assertTrue(swapped in 0 until firstManifestMove, "the rollback applies as soon as the link has moved")
        assertTrue(lines.any { it.startsWith("restore()") && "ln -sfn \"\$prev\"" in it }, "restores the old link")
        assertTrue(
            lines.any {
                it.startsWith("restore()") && "rm -f '/h/tools.d'/*.tmp" in it
            },
            "no temp manifests left",
        )
    }

    @Test
    fun `publication never overwrites an immutable destination`() {
        val script = KotlinStoreLayout.buildPublishScript("/home/a b/bin", "/home/a b/bin/v1", "/home/a b/work/out")
        assertTrue(script.contains("mv -T -n '/home/a b/work/out' '/home/a b/bin/v1'"))
        assertTrue(script.contains("if [ -e '/home/a b/work/out' ]; then exit 3; fi"))
    }

    @Test
    fun `publication with workDir swaps existing destination to old`() {
        val script =
            KotlinStoreLayout.buildPublishScript(
                groupDir = "/home/a b/bin",
                targetDir = "/home/a b/bin/v1",
                outDir = "/home/a b/work/out",
                workDir = "/home/a b/work",
            )
        assertTrue(script.contains("mv -T '/home/a b/work/out' '/home/a b/bin/v1.tmp'"))
        assertTrue(script.contains("mv -T '/home/a b/bin/v1' '/home/a b/work/old'"))
        assertTrue(script.contains("mv -T '/home/a b/bin/v1.tmp' '/home/a b/bin/v1'"))
        assertTrue(script.contains("trap"))
    }

    @Test
    fun `readable version ids match expected format and rules`() {
        val tagSource =
            org.ide.lti.core.domain.tools.ResolvedSource.Git(
                repoUrl = "https://example.com/repo.git",
                ref = org.ide.lti.core.model.setup.ToolRef.Tag("v1.8.10-251217"),
                commit = "7274417816e3adfe0cd6d4a8ff194ec5f41268f4",
            )
        assertEquals("v1.8.10-251217", KotlinStoreLayout.computeVersionId(tagSource))

        val branchSource =
            org.ide.lti.core.domain.tools.ResolvedSource.Git(
                repoUrl = "https://example.com/repo.git",
                ref = org.ide.lti.core.model.setup.ToolRef.Branch("sixteen"),
                commit = "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
            )
        assertEquals("sixteen-f9bef94", KotlinStoreLayout.computeVersionId(branchSource))

        val commitSource =
            org.ide.lti.core.domain.tools.ResolvedSource.Git(
                repoUrl = "https://example.com/repo.git",
                ref = org.ide.lti.core.model.setup.ToolRef.Commit("f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8"),
                commit = "f9bef94306f93be88b0cf98f9eeb0e24fc7b6aa8",
            )
        assertEquals("f9bef94", KotlinStoreLayout.computeVersionId(commitSource))

        val releaseSource =
            org.ide.lti.core.domain.tools.ResolvedSource.Release(
                version = "2.97.0",
                url = "https://example.com/release.tar.gz",
                sha256 = "141507c337e8b202ad398550c3b73d72f5af92e86f71665214538a81efd4c409",
                member = "tool",
            )
        assertEquals("2.97.0", KotlinStoreLayout.computeVersionId(releaseSource))

        val idRegex = Regex("^[A-Za-z0-9._-]+$")
        listOf(tagSource, branchSource, commitSource, releaseSource).forEach {
            val id = KotlinStoreLayout.computeVersionId(it)
            assertTrue(idRegex.matches(id), "id $id must match ^[A-Za-z0-9._-]+$")
        }

        kotlin.test.assertFailsWith<ToolErrorException> {
            KotlinStoreLayout.validateVersionId("current")
        }

        val clashBranchSource = branchSource.copy(commit = "1234567890abcdef1234567890abcdef12345678")
        val clashId = KotlinStoreLayout.computeVersionId(clashBranchSource)
        assertEquals("sixteen-1234567", clashId)
    }
}
