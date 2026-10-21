/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.usecase.publish

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.ide.lti.core.domain.pipeline.digestToken
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.runToEnd
import org.ide.lti.core.model.workspace.CollisionPolicy
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.Workspace
import java.util.Base64

/**
 * GitHub release publishing with `gh` in WSL (signed in with `gh auth login` there; no token is stored by the
 * app), after LtiRom's scripts/release/publish.sh: upload, verify the uploaded digests, then the OTA manifest.
 */
internal class GithubReleaseSteps(
    private val port: PipelineExecutionPort,
    private val ws: Workspace,
    private val settings: PublishSettings,
    private val release: ReleaseFiles,
    /** Bytes uploaded so far of the bytes this upload sends. */
    private val report: (Long, Long) -> Unit = { _, _ -> },
) {
    private val root = checkNotNull(ws.linuxPath) { "workspace ${ws.id} has no WSL folder" }
    private val repo = checkNotNull(settings.repository?.takeIf { it.isNotBlank() }) { "No release repository set." }
    val tag: String = settings.sourceTag?.takeIf { it.isNotBlank() } ?: release.zip.removeSuffix(".zip")
    private val split = release.size > settings.partSizeBytes
    private val zipPath = "$root/out/package/${release.zip}"
    private val partsDir = "$root/out/publish/parts"

    suspend fun run(stepId: String): String = when (stepId) {
        "check-auth" -> gh("auth", "status").let { "gh is signed in" }
        "prepare-assets" -> prepareAssets()
        "create-release" -> createRelease()
        "upload-assets" -> uploadAssets()
        "verify-remote" -> verifyRemote()
        "publish-manifest" -> publishManifest()
        else -> error("unknown step $stepId")
    }

    /** A package larger than the part size goes up as `<zip>.00`, `.01`, … (GitHub's asset limit is 2 GiB). */
    private suspend fun prepareAssets(): String {
        if (!split) return "one asset, ${release.size} bytes"
        tool("rm", "-rf", "--", partsDir)
        tool("mkdir", "-p", partsDir)
        tool("split", "-b", settings.partSizeBytes.toString(), "-d", "-a", "2", zipPath, "$partsDir/${release.zip}.")
        return "${assets().size} parts"
    }

    private suspend fun assets(): List<String> = if (!split) {
        listOf(zipPath)
    } else {
        tool("find", partsDir, "-maxdepth", "1", "-name", "${release.zip}.*", "-type", "f")
            .lines().filter { it.isNotBlank() }.sorted()
    }

    private suspend fun createRelease(): String {
        if (exec("gh", "release", "view", tag, "--repo", repo).exitCode == 0) return "release $tag exists"
        gh("release", "create", tag, "--repo", repo, "--title", tag, "--notes", "LtiRom ${release.zip}")
        return "created release $tag in $repo"
    }

    /**
     * Uploads each asset, reporting the bytes `gh` has read of the ones this run sends (assets kept as they are do
     * not count, so the bar and speed measure real transfer). An asset already up with the local file's digest is
     * kept. The collision policy decides whether any differing or unfinished asset can be replaced, on retries too.
     */
    private suspend fun uploadAssets(): String {
        val remote = remoteAssets()
        val files = assets()
        // path -> clobber (replace the copy there); paths to keep are left out.
        val uploads = files.mapNotNull { path -> uploadMode(path, remote)?.let { path to it } }
        val sizes = uploads.associate { (path, _) -> path to tool("stat", "-c", "%s", "--", path).trim().toLong() }
        val total = sizes.values.sum()
        var done = 0L
        report(done, total)
        for ((path, clobber) in uploads) {
            val size = sizes.getValue(path)
            val args = listOf("release", "upload", tag, path, "--repo", repo) +
                if (clobber) listOf("--clobber") else emptyList()
            val base = done
            sampling(sample = { readBy(path) }, report = { report(base + it.coerceAtMost(size), total) }) {
                gh(*args.toTypedArray())
            }
            done += size
            report(done, total)
        }
        return "uploaded ${uploads.size} of ${files.size} assets" +
            if (uploads.size < files.size) " (${files.size - uploads.size} already there)" else ""
    }

    /** Whether [path] is uploaded fresh (false), over the copy there (true), or kept as it is (null). */
    private suspend fun uploadMode(path: String, remote: Map<String, String>): Boolean? {
        val name = path.substringAfterLast('/')
        return when {
            name !in remote -> false
            // The same bytes are already there (an earlier upload of this file): nothing to upload or refuse.
            remote[name] == "sha256:" + tool("sha256sum", "--", path).digestToken() -> null
            settings.collisionPolicy == CollisionPolicy.FAIL ->
                error("$name is already in release $tag (collision policy: fail).")
            settings.collisionPolicy == CollisionPolicy.SKIP -> null
            else -> true
        }
    }

    /** Bytes the running `gh release upload` of [path] has read (`rchar` in /proc/<pid>/io), or null. */
    private suspend fun readBy(path: String): Long? {
        val root = checkNotNull(ws.linuxPath)
        val pid = port.execute(ws, stepCommand("pgrep", listOf("-n", "-f", "release upload $tag $path"), root))
            .takeIf { it.exitCode == 0 }?.stdout?.trim()?.lines()?.firstOrNull() ?: return null
        val io = port.execute(ws, stepCommand("cat", listOf("/proc/$pid/io"), root))
        return io.stdout.lineSequence().firstOrNull { it.startsWith("rchar:") }
            ?.substringAfter(':')?.trim()?.toLongOrNull()
    }

    /** Every asset's digest on GitHub must be the local file's sha256. */
    private suspend fun verifyRemote(): String {
        val remote = remoteAssets()
        assets().forEach { path ->
            val name = path.substringAfterLast('/')
            val local = tool("sha256sum", "--", path).digestToken()
            val digest = checkNotNull(remote[name]) { "$name is not in release $tag." }
            check(digest == "sha256:$local") { "$name on GitHub has digest $digest, not sha256:$local." }
        }
        return "${remote.size} assets verified"
    }

    /** name -> digest (`sha256:…`) of the release's assets; [UNFINISHED] for one whose upload was cut off. */
    private suspend fun remoteAssets(): Map<String, String> {
        val out = gh("release", "view", tag, "--repo", repo, "--json", "assets")
        return Json.parseToJsonElement(out).jsonObject.getValue("assets").jsonArray.associate {
            val a = it.jsonObject
            val uploaded = a["state"]?.jsonPrimitive?.content.let { it == null || it == "uploaded" }
            a.getValue("name").jsonPrimitive.content to
                (a["digest"]?.jsonPrimitive?.content?.takeIf { uploaded } ?: UNFINISHED)
        }
    }

    /**
     * Adds this release to the OTA manifest in the manifest repository, keeping the newest [retention] entries
     * for this device and every other device's entries. The write names the version it read, so a concurrent
     * change makes it fail rather than be lost.
     */
    private suspend fun publishManifest(): String {
        val manifestRepo = settings.manifestRepository?.takeIf { it.isNotBlank() }
            ?: return "skipped: no manifest repository set"
        check(!split) { "A package published in parts cannot be an OTA download; raise the part size." }
        val path = checkNotNull(settings.manifestPath?.takeIf { it.isNotBlank() }) { "No manifest path set." }
        val branch = settings.manifestBranch?.takeIf { it.isNotBlank() } ?: "main"
        val current = exec("gh", "api", "repos/$manifestRepo/contents/$path?ref=$branch")
        val (existing, sha) = if (current.exitCode == 0) {
            val o = Json.parseToJsonElement(current.stdout).jsonObject
            val text = String(Base64.getMimeDecoder().decode(o.getValue("content").jsonPrimitive.content))
            text to o.getValue("sha").jsonPrimitive.content
        } else {
            null to null
        }
        val built = checkNotNull(port.readFile(ws, "out/manifest.json")) { "out/manifest.json is missing." }
        val merged = merge(existing, built)
        val args = mutableListOf(
            "api", "-X", "PUT", "repos/$manifestRepo/contents/$path",
            "-f", "message=OTA: ${release.zip}",
            "-f", "content=" + Base64.getEncoder().encodeToString(merged.encodeToByteArray()),
            "-f", "branch=$branch",
        )
        if (sha != null) args += listOf("-f", "sha=$sha")
        gh(*args.toTypedArray())
        return "manifest $manifestRepo/$path updated"
    }

    private fun merge(existing: String?, built: String): String {
        val entry = Json.parseToJsonElement(built).jsonObject.getValue("response").jsonArray.first().jsonObject
        val url = "https://github.com/$repo/releases/download/$tag/${release.zip}"
        val newEntry = JsonObject(entry + ("urls" to JsonArray(listOf(JsonPrimitive(url)))))
        val device = entry["device"]?.jsonPrimitive?.content
        val old = existing?.let { Json.parseToJsonElement(it).jsonObject["response"]?.jsonArray }.orEmpty()
            .map { it.jsonObject }
            .filter { it["id"] != newEntry["id"] }
        val (mine, others) = old.partition { it["device"]?.jsonPrimitive?.content == device }
        val kept = (listOf(newEntry) + mine)
            .sortedByDescending { it["datetime"]?.jsonPrimitive?.longOrNull ?: 0L }
            .take(settings.retentionPerDevice.coerceAtLeast(1))
        return pretty.encodeToString(JsonObject.serializer(), JsonObject(mapOf("response" to JsonArray(others + kept))))
    }

    private suspend fun gh(vararg args: String): String = tool("gh", *args)

    private suspend fun tool(id: String, vararg args: String): String {
        val result = exec(id, *args)
        val why = result.stderr.trim().ifEmpty { "exit ${result.exitCode}" }
        check(result.exitCode == 0) { "$id ${args.take(2).joinToString(" ")} failed: $why" }
        return result.stdout
    }

    private suspend fun exec(id: String, vararg args: String) = port.runToEnd(ws, id, args.toList())

    companion object {
        val STEPS = listOf(
            "verify-package" to "Check the package against its manifest",
            "check-auth" to "Check the GitHub sign-in (gh auth status)",
            "prepare-assets" to "Prepare the upload (split when larger than the part size)",
            "create-release" to "Create the GitHub release",
            "upload-assets" to "Upload the package",
            "verify-remote" to "Verify the uploaded digests",
            "publish-manifest" to "Update the OTA manifest",
        )
        private val pretty = Json { prettyPrint = true }
    }
}

private const val UNFINISHED = ""
