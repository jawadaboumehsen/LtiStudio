package org.ide.lti.core.data.setup.execute.handlers

import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.setup.install.ArtifactFileEntry
import org.ide.lti.core.data.setup.install.ArtifactManifest
import org.ide.lti.core.data.setup.install.ArtifactRecipeInfo
import org.ide.lti.core.data.setup.install.ArtifactSourceInfo
import org.ide.lti.core.data.setup.install.ArtifactStore
import org.ide.lti.core.data.setup.install.detectHostArch
import org.ide.lti.core.domain.setup.ResolvedInput
import org.ide.lti.core.domain.setup.ToolGroup
import java.security.MessageDigest
import java.util.UUID

class WslArtifactPublisher(private val cli: WslCliExecutor, private val distro: String, private val json: Json) {
    fun publishCandidate(
        group: ToolGroup,
        wslTmpBuild: String,
        wslTmpBin: String,
        input: ResolvedInput,
        fingerprint: String,
    ): Result<ArtifactManifest> = runCatching {
        val files = collectArtifactFiles(wslTmpBin, group)
        val manifest = buildManifest(group, files, input, fingerprint)
        executePublish(group, wslTmpBuild, wslTmpBin, manifest)
        manifest
    }

    private fun collectArtifactFiles(wslTmpBin: String, group: ToolGroup): List<ArtifactFileEntry> {
        val findRes = cli.execute(
            distro,
            listOf("find", wslTmpBin, "-mindepth", "1", "(", "-type", "f", "-o", "-type", "l", ")"),
        )
        val failed = findRes.exitCode != 0 || findRes.output.isBlank()
        check(!failed) { "find failed in $wslTmpBin for ${group.id.value}: ${findRes.error}" }
        val rawPaths = findRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }

        val declaredOutputs = group.outputs.map { it.file }.toSet()

        return rawPaths.map { fullPath ->
            val relPath = fullPath.removePrefix(wslTmpBin).removePrefix("/")
            val isLink = cli.execute(distro, listOf("test", "-L", fullPath)).exitCode == 0
            val linkTarget = if (isLink) {
                val rl = cli.execute(distro, listOf("readlink", fullPath))
                if (rl.exitCode == 0 && rl.output.isNotBlank()) rl.output.trim() else null
            } else {
                null
            }

            val sumRes = cli.execute(distro, listOf("sha256sum", fullPath))
            val sha256 = if (sumRes.exitCode == 0) {
                val stdout = sumRes.output.trim()
                val hash = stdout.substringBefore(" ")
                check(hash.length == 64) { "Invalid sha256sum output for $relPath: $stdout" }
                hash
            } else if (linkTarget != null) {
                MessageDigest.getInstance("SHA-256")
                    .digest(linkTarget.toByteArray())
                    .joinToString("") { "%02x".format(it) }
            } else {
                error("sha256sum failed for $relPath: ${sumRes.error}")
            }

            val isExec = cli.execute(distro, listOf("test", "-x", fullPath)).exitCode == 0
            val mode = if (relPath in declaredOutputs || isExec) "755" else "644"
            ArtifactFileEntry(path = "bin/$relPath", sha256 = sha256, mode = mode, linkTarget = linkTarget)
        }
    }

    private fun buildManifest(
        group: ToolGroup,
        files: List<ArtifactFileEntry>,
        input: ResolvedInput,
        fingerprint: String,
    ): ArtifactManifest {
        val contentId = ArtifactStore.computeContentId(fingerprint, files)
        val artifactId = ArtifactStore.computeArtifactId(contentId, UUID.randomUUID().toString())

        val sourceInfo = when (input) {
            is ResolvedInput.Git -> ArtifactSourceInfo(
                kind = "git",
                repoUrl = input.repoUrl,
                commit = input.commit,
                submodules = input.submoduleCommits,
            )
            is ResolvedInput.Release -> ArtifactSourceInfo(
                kind = "release",
                version = input.version,
                sha256 = input.sha256,
            )
        }

        return ArtifactManifest(
            schema = 1,
            group = group.id.value,
            artifactId = artifactId,
            fingerprint = fingerprint,
            source = sourceInfo,
            recipe = ArtifactRecipeInfo(type = group.recipe::class.simpleName ?: "Recipe", revision = 1),
            arch = detectHostArch(),
            files = files,
            outputs = group.outputs.map { it.file },
            verifiedAt = System.currentTimeMillis(),
        )
    }

    private fun executePublish(group: ToolGroup, wslTmpBuild: String, wslTmpBin: String, manifest: ArtifactManifest) {
        val homeRes = cli.execute(distro, listOf("printenv", "HOME"))
        check(homeRes.exitCode == 0) { "Failed to get HOME" }
        val home = homeRes.output.trim()
        val wslArtifactsRoot = "$home/LtiRomTools/artifacts"
        val wslGroupDir = "$wslArtifactsRoot/${group.id.value}"
        val wslTargetDir = "$wslGroupDir/${manifest.artifactId}"

        val manifestJson = json.encodeToString(manifest)
        val teeRes = cli.execute(distro, listOf("tee", "$wslTmpBuild/manifest.json"), stdin = manifestJson)
        check(teeRes.exitCode == 0) { "Failed to write manifest: ${teeRes.error}" }

        for (out in group.outputs.map { it.file }) {
            val chmodRes = cli.execute(distro, listOf("chmod", "+x", "$wslTmpBin/$out"))
            check(chmodRes.exitCode == 0) { "chmod failed on $out" }
        }

        val mkGroupRes = cli.execute(distro, listOf("mkdir", "-p", wslGroupDir))
        check(mkGroupRes.exitCode == 0) { "Failed to create group dir: ${mkGroupRes.error}" }

        val lockFile = "$wslArtifactsRoot/.lock"
        val mvRes = cli.execute(distro, listOf("flock", lockFile, "mv", wslTmpBuild, wslTargetDir))
        if (mvRes.exitCode != 0) {
            val existsRes = cli.execute(distro, listOf("test", "-d", wslTargetDir))
            if (existsRes.exitCode == 0) {
                cli.execute(distro, listOf("rm", "-rf", wslTmpBuild))
            } else {
                error("Failed atomic move to $wslTargetDir: ${mvRes.error}")
            }
        }
    }
}
