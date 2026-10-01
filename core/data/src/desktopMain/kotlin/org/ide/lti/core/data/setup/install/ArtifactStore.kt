package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Serializable
public data class ArtifactFileEntry(
    val path: String,
    val sha256: String,
    val mode: String = "755",
    val linkTarget: String? = null,
)

@Serializable
public data class ArtifactSourceInfo(
    val kind: String = "git",
    val repoUrl: String? = null,
    val commit: String? = null,
    val submodules: Map<String, String> = emptyMap(),
    val version: String? = null,
    val sha256: String? = null,
)

@Serializable
public data class ArtifactRecipeInfo(val type: String, val revision: Int)

@Serializable
public data class ArtifactManifest(
    val schema: Int = 1,
    val group: String,
    val artifactId: String,
    val fingerprint: String,
    val source: ArtifactSourceInfo,
    val recipe: ArtifactRecipeInfo,
    val toolchain: Map<String, String> = emptyMap(),
    val arch: String = detectHostArch(),
    val files: List<ArtifactFileEntry> = emptyList(),
    val outputs: List<String> = emptyList(),
    val verifiedAt: Long = System.currentTimeMillis(),
)

@Serializable
public data class UnusableMarker(val reason: String, val detectedAt: Long = System.currentTimeMillis())

@Suppress("ReturnCount", "FunctionExpressionBody")
public class ArtifactStore(
    public val rootDir: File? = null,
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val distro: String = "Ubuntu",
) {
    public constructor(cli: WslCliExecutor, distro: String = "Ubuntu") : this(null, cli, distro)
    public constructor(rootDir: File) : this(rootDir, WslCliExecutor(), "Ubuntu")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    public companion object {
        public fun computeContentHash(files: List<ArtifactFileEntry>): String {
            val lines = files
                .sortedBy { it.path }
                .joinToString("\n") { "${it.path}\t${it.sha256}\t${it.mode}" }
            return sha256(lines)
        }

        public fun computeContentId(fingerprint: String, files: List<ArtifactFileEntry>): String {
            val fp12 = fingerprint.take(12)
            val ch12 = computeContentHash(files).take(12)
            return "$fp12-$ch12"
        }

        public fun computeArtifactId(contentId: String, attemptId: String): String {
            val inst8 = attemptId.replace("-", "").take(8)
            return "$contentId-$inst8"
        }

        public fun sha256(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(file.readBytes())
            return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }

        public fun sha256(text: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }
    }

    public fun getArtifactDir(group: String, artifactId: String): File {
        return File(rootDir ?: File(""), "$group/$artifactId")
    }

    public fun publish(
        group: String,
        candidateDir: File,
        manifestTemplate: ArtifactManifest,
        attemptId: String,
    ): ArtifactManifest {
        val base = rootDir ?: File(System.getProperty("java.io.tmpdir"), "lti-artifacts")
        val contentId = computeContentId(manifestTemplate.fingerprint, manifestTemplate.files)
        val artifactId = computeArtifactId(contentId, attemptId)
        val groupDir = File(base, group).apply { mkdirs() }
        val targetDir = File(groupDir, artifactId)
        candidateDir.copyRecursively(targetDir, overwrite = true)
        val finalManifest = manifestTemplate.copy(artifactId = artifactId, verifiedAt = System.currentTimeMillis())
        File(targetDir, "manifest.json").writeText(json.encodeToString(finalManifest))
        return finalManifest
    }

    private fun getWslArtifactDir(home: String, group: String, artifactId: String): String {
        return "$home/LtiRomTools/artifacts/$group/$artifactId"
    }

    private fun getHome(): String? {
        val homeRes = cli.execute(distro, listOf("printenv", "HOME"))
        return homeRes.output.trim().takeIf { homeRes.exitCode == 0 && it.startsWith("/") }
    }

    public fun isUnusable(group: String, artifactId: String): Boolean {
        val home = getHome() ?: return false
        val dir = getWslArtifactDir(home, group, artifactId)
        val res = cli.execute(distro, listOf("test", "-f", "$dir/unusable.json"))
        return res.exitCode == 0
    }

    public fun isIntact(group: String, artifactId: String): Boolean {
        val home = getHome() ?: return false
        val manifest = getManifest(group, artifactId) ?: return false
        val dir = getWslArtifactDir(home, group, artifactId)
        return areArtifactFilesIntact(dir, manifest.files)
    }

    public fun getManifest(group: String, artifactId: String): ArtifactManifest? {
        if (rootDir != null) {
            val localFile = File(rootDir, "$group/$artifactId/manifest.json")
            if (localFile.exists()) {
                return runCatching { json.decodeFromString<ArtifactManifest>(localFile.readText()) }.getOrNull()
            }
        }
        val home = getHome() ?: return null
        val wslPath = "$home/LtiRomTools/artifacts/$group/$artifactId/manifest.json"
        val res = cli.execute(distro, listOf("cat", wslPath))
        if (res.exitCode != 0 || res.output.isBlank()) return null
        return runCatching { json.decodeFromString<ArtifactManifest>(res.output.trim()) }.getOrNull()
    }

    private fun areArtifactFilesIntact(artifactDir: String, files: List<ArtifactFileEntry>): Boolean {
        return files.all { entry ->
            val wslPath = "$artifactDir/${entry.path}"
            val res = cli.execute(distro, listOf("sha256sum", wslPath))
            if (res.exitCode != 0) return@all false
            val stdout = res.output.trim()
            val sha256 = stdout.substringBefore(" ")
            sha256 == entry.sha256
        }
    }

    public fun markUnusable(group: String, artifactId: String, reason: String) {
        val home = getHome() ?: return
        val artifactDir = getWslArtifactDir(home, group, artifactId)
        cli.execute(distro, listOf("mkdir", "-p", artifactDir))
        val marker = UnusableMarker(reason = reason)
        val markerJson = json.encodeToString(marker)

        val tmpFile = "$artifactDir/unusable.json.tmp"
        val targetFile = "$artifactDir/unusable.json"

        val teeRes = cli.execute(distro, listOf("tee", tmpFile), stdin = markerJson)
        if (teeRes.exitCode == 0) {
            cli.execute(distro, listOf("mv", tmpFile, targetFile))
        }
    }
}
