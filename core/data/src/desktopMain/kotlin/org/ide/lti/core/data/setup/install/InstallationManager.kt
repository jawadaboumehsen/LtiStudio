package org.ide.lti.core.data.setup.install

import io.ltirom.tooling.client.wsl.WslCliExecutor
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.ide.lti.core.domain.setup.RevertingGroup
import org.ide.lti.core.domain.setup.ToolGroupCatalog
import java.io.File
import java.security.MessageDigest
import java.util.Locale

@Serializable
public data class InstallOutputDescriptor(
    val toolId: String,
    val file: String,
    val kind: String = "NATIVE",
    val requiredForProduct: Boolean = true,
)

@Serializable
public data class InstallManifest(
    val schema: Int = 1,
    val installId: String,
    val distro: String,
    val arch: String,
    val groups: Map<String, String>,
    val outputs: List<InstallOutputDescriptor> = emptyList(),
    val catalogRevision: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
)

public data class AssembledCandidate(
    val installId: String,
    val installDirWsl: String,
    val manifest: InstallManifest,
    val installDir: File = File(installDirWsl),
)

public data class CleanupResult(val deletedInstalls: List<String>, val deletedArtifacts: List<String>)

@Suppress("ReturnCount")
public class InstallationManager(
    public val toolsDir: File? = null,
    private val cli: WslCliExecutor = WslCliExecutor(),
    private val distro: String = "Ubuntu",
) {
    public constructor(cli: WslCliExecutor, distro: String = "Ubuntu") : this(null, cli, distro)
    public constructor(toolsDir: File) : this(toolsDir, WslCliExecutor(), "Ubuntu")

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    public companion object {
        public val REQUIRED_CATALOG_GROUPS: Set<String> = setOf(
            "android-tools",
            "erofs-utils",
            "apktool",
            "signapk",
            "img2sdat",
            "gh",
            "payload-dumper-go",
        )

        public fun sha256(text: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(text.toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(Locale.ROOT, it) }
        }

        public fun computeInstallId(
            schema: Int,
            distro: String,
            arch: String,
            groups: Map<String, String>,
            outputs: List<InstallOutputDescriptor>,
            catalogRevision: Int,
        ): String {
            val sortedGroups = groups.toSortedMap()
            val sortedOutputs = outputs.sortedBy { it.toolId }
            val canonicalJson =
                buildCanonicalJson(schema, distro, arch, sortedGroups, sortedOutputs, catalogRevision)
            val hash = sha256(canonicalJson)
            return "i-" + hash.take(16)
        }

        private fun buildCanonicalJson(
            schema: Int,
            distro: String,
            arch: String,
            groups: Map<String, String>,
            outputs: List<InstallOutputDescriptor>,
            catalogRevision: Int,
        ): String {
            val groupsJson = groups.entries.joinToString(",") { "\"${it.key}\":\"${it.value}\"" }
            val outputsJson = outputs.joinToString(",") {
                "{\"file\":\"${it.file}\",\"kind\":\"${it.kind}\"," +
                    "\"requiredForProduct\":${it.requiredForProduct},\"toolId\":\"${it.toolId}\"}"
            }
            return "{\"arch\":\"$arch\",\"catalogRevision\":$catalogRevision,\"distro\":\"$distro\"," +
                "\"groups\":{$groupsJson},\"outputs\":[$outputsJson],\"schema\":$schema}"
        }
    }

    private fun getHome(): String? {
        val homeRes = cli.execute(distro, listOf("printenv", "HOME"))
        return homeRes.output.trim().takeIf { homeRes.exitCode == 0 && it.startsWith("/") }
    }

    public fun assembleCandidate(
        distro: String,
        arch: String,
        groups: Map<String, String>,
        outputs: List<InstallOutputDescriptor>,
        catalogRevision: Int = 1,
    ): AssembledCandidate {
        val missing = REQUIRED_CATALOG_GROUPS - groups.keys
        require(missing.isEmpty()) { "Missing catalog group(s): ${missing.joinToString(", ")}" }

        val installId = computeInstallId(
            schema = 1,
            distro = distro,
            arch = arch,
            groups = groups,
            outputs = outputs,
            catalogRevision = catalogRevision,
        )

        val installDir = if (toolsDir != null) {
            "${toolsDir.absolutePath.replace('\\', '/')}/installs/$installId"
        } else {
            val home = getHome() ?: throw IllegalStateException("Could not get WSL HOME")
            "$home/LtiRomTools/installs/$installId"
        }
        val binDir = "$installDir/bin"

        if (toolsDir != null) {
            File(binDir).mkdirs()
        } else {
            cli.execute(distro, listOf("mkdir", "-p", binDir))
        }

        linkArtifactsToBin(groups, binDir)

        val manifest = InstallManifest(
            schema = 1,
            installId = installId,
            distro = distro,
            arch = arch,
            groups = groups,
            outputs = outputs,
            catalogRevision = catalogRevision,
            createdAt = System.currentTimeMillis(),
        )
        val manifestJson = json.encodeToString(manifest)
        if (toolsDir != null) {
            runCatching { File(installDir, "install.json").writeText(manifestJson) }
        } else {
            cli.execute(distro, listOf("tee", "$installDir/install.json"), stdin = manifestJson)
        }

        return AssembledCandidate(installId, installDir, manifest, File(installDir))
    }

    private fun linkArtifactsToBin(groups: Map<String, String>, binDir: String) {
        if (toolsDir != null) {
            val localArtBase = File(toolsDir, "artifacts")
            val localBinDir = File(binDir)
            for ((group, artifactId) in groups) {
                val artBin = File(localArtBase, "$group/$artifactId/bin")
                if (artBin.exists()) {
                    artBin.listFiles()?.forEach { file ->
                        val target = File(localBinDir, file.name)
                        file.copyTo(target, overwrite = true)
                    }
                }
            }
            return
        }
        val home = getHome() ?: return
        val artifactsDir = "$home/LtiRomTools/artifacts"
        for ((group, artifactId) in groups) {
            val artBin = "$artifactsDir/$group/$artifactId/bin"
            val testRes = cli.execute(distro, listOf("test", "-d", artBin))
            if (testRes.exitCode == 0) {
                val lsRes = cli.execute(distro, listOf("ls", "-1", artBin))
                if (lsRes.exitCode == 0 && lsRes.output.isNotBlank()) {
                    val files = lsRes.output.lines().map { it.trim() }.filter { it.isNotBlank() }
                    for (file in files) {
                        linkSingleFile(file, group, artifactId, binDir)
                    }
                }
            }
        }
    }

    private fun linkSingleFile(file: String, group: String, artifactId: String, binDir: String) {
        val linkFile = "$binDir/$file"
        cli.execute(distro, listOf("rm", "-rf", linkFile))
        val relPath = "../../../artifacts/$group/$artifactId/bin/$file"
        val lnRes = cli.execute(distro, listOf("ln", "-s", relPath, linkFile))
        if (lnRes.exitCode != 0) {
            val artBinFile = "$binDir/../../../artifacts/$group/$artifactId/bin/$file"
            cli.execute(distro, listOf("cp", "-R", artBinFile, linkFile))
        }
    }

    public suspend fun verifyCandidate(
        distro: String,
        installId: String,
        verifier: org.ide.lti.core.data.setup.ToolVerifier,
        outputs: List<InstallOutputDescriptor>? = null,
    ): List<org.ide.lti.core.data.setup.ToolVerifier.Result> {
        val resolvedOutputs = outputs ?: getManifest(installId)?.outputs.orEmpty()
        val binDir = if (toolsDir != null) {
            "${toolsDir.absolutePath.replace('\\', '/')}/installs/$installId/bin"
        } else {
            "~/LtiRomTools/installs/$installId/bin"
        }
        return resolvedOutputs.filter { it.requiredForProduct }.map { out ->
            verifier.verify(
                distro = distro,
                toolId = out.toolId,
                binDir = binDir,
                binaryName = out.file,
            )
        }
    }

    public fun getManifest(installId: String): InstallManifest? {
        if (toolsDir != null) {
            val localPath = File(toolsDir, "installs/$installId/install.json")
            if (localPath.exists()) {
                return runCatching { json.decodeFromString<InstallManifest>(localPath.readText()) }.getOrNull()
            }
            return null
        }
        val home = getHome() ?: return null
        val wslPath = "$home/LtiRomTools/installs/$installId/install.json"
        val res = cli.execute(distro, listOf("cat", wslPath))
        return if (res.exitCode == 0 && res.output.isNotBlank()) {
            runCatching { json.decodeFromString<InstallManifest>(res.output.trim()) }.getOrNull()
        } else {
            null
        }
    }

    public fun computeRevertSet(
        activeInstallId: String,
        previousInstallId: String,
        artifactStore: ArtifactStore,
    ): List<RevertingGroup> {
        val activeManifest = getManifest(activeInstallId)
        val prevManifest = getManifest(previousInstallId)
        if (activeManifest == null || prevManifest == null) {
            return emptyList()
        }
        return ToolGroupCatalog.DEFAULT_GROUPS.mapNotNull { group ->
            val activeArtId = activeManifest.groups[group.id.value]
            val prevArtId = prevManifest.groups[group.id.value]
            if (activeArtId != null && prevArtId != null && activeArtId != prevArtId) {
                val activeArt = artifactStore.getManifest(group.id.value, activeArtId)
                val prevArt = artifactStore.getManifest(group.id.value, prevArtId)
                val curVer = activeArt?.source?.version ?: activeArt?.source?.commit?.take(7) ?: "unknown"
                val prevVer = prevArt?.source?.version ?: prevArt?.source?.commit?.take(7) ?: "unknown"
                RevertingGroup(
                    groupId = group.id,
                    currentArtifactId = activeArtId,
                    previousArtifactId = prevArtId,
                    currentVersion = curVer,
                    previousVersion = prevVer,
                    affectedTools = group.outputs.map { it.toolId },
                )
            } else {
                null
            }
        }
    }

    public fun cleanup(
        activeInstallId: String?,
        previousInstallId: String?,
        protectedInstallIds: Set<String> = emptySet(),
        pinnedArtifactIds: Set<String> = emptySet(),
    ): CleanupResult {
        if (toolsDir != null) {
            val installsBase = File(toolsDir, "installs")
            val keptInstallIds = setOfNotNull(activeInstallId, previousInstallId) + protectedInstallIds
            val allInstalls = installsBase.listFiles { f -> f.isDirectory } ?: emptyArray()
            val deletedInstalls = mutableListOf<String>()
            for (dir in allInstalls) {
                if (dir.name !in keptInstallIds) {
                    dir.deleteRecursively()
                    deletedInstalls.add(dir.name)
                }
            }
            return CleanupResult(deletedInstalls, emptyList())
        }
        val home = getHome() ?: return CleanupResult(emptyList(), emptyList())
        val installsDir = "$home/LtiRomTools/installs"
        val artifactsDir = "$home/LtiRomTools/artifacts"
        val keptInstallIds = setOfNotNull(activeInstallId, previousInstallId) + protectedInstallIds
        val lsInstalls = cli.execute(distro, listOf("ls", "-1", installsDir))
        val allInstalls = if (lsInstalls.exitCode == 0) {
            lsInstalls.output.lines().map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        val referencedArtifacts = collectReferencedArtifacts(allInstalls, keptInstallIds, installsDir)

        val deletedInstalls = deleteUnreferencedInstalls(allInstalls, keptInstallIds, installsDir)
        val deletedArtifacts = deleteUnreferencedArtifacts(artifactsDir, referencedArtifacts, pinnedArtifactIds)

        return CleanupResult(deletedInstalls, deletedArtifacts)
    }

    private fun collectReferencedArtifacts(
        allInstalls: List<String>,
        keptInstallIds: Set<String>,
        installsDir: String,
    ): Set<String> {
        val referenced = mutableSetOf<String>()
        for (installId in allInstalls) {
            if (installId in keptInstallIds) {
                val res = cli.execute(distro, listOf("cat", "$installsDir/$installId/install.json"))
                check(res.exitCode == 0) {
                    "Cannot clean up: failed to read install manifest for kept install '$installId': ${res.error}"
                }
                val manifest = try {
                    json.decodeFromString<InstallManifest>(res.output.trim())
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Cannot clean up: corrupted install manifest for kept install '$installId': ${e.message}",
                        e,
                    )
                }
                referenced.addAll(manifest.groups.values)
            }
        }
        return referenced
    }

    private fun deleteUnreferencedInstalls(
        allInstalls: List<String>,
        keptInstallIds: Set<String>,
        installsDir: String,
    ): List<String> {
        val deleted = mutableListOf<String>()
        for (id in allInstalls) {
            if (id !in keptInstallIds) {
                cli.execute(distro, listOf("rm", "-rf", "$installsDir/$id"))
                deleted.add(id)
            }
        }
        return deleted
    }

    private fun deleteUnreferencedArtifacts(
        artifactsDir: String,
        referencedArtifacts: Set<String>,
        pinnedArtifactIds: Set<String>,
    ): List<String> {
        val deleted = mutableListOf<String>()
        val lsGroups = cli.execute(distro, listOf("ls", "-1", artifactsDir))
        if (lsGroups.exitCode == 0) {
            val groups = lsGroups.output.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (group in groups) {
                deleteUnrefInGroup(artifactsDir, group, referencedArtifacts, pinnedArtifactIds, deleted)
            }
        }
        return deleted
    }

    private fun deleteUnrefInGroup(
        artifactsDir: String,
        group: String,
        referencedArtifacts: Set<String>,
        pinnedArtifactIds: Set<String>,
        deleted: MutableList<String>,
    ) {
        val groupDir = "$artifactsDir/$group"
        val lsArts = cli.execute(distro, listOf("ls", "-1", groupDir))
        if (lsArts.exitCode == 0) {
            val arts = lsArts.output.lines().map { it.trim() }.filter { it.isNotBlank() }
            for (artId in arts) {
                if (artId !in referencedArtifacts && artId !in pinnedArtifactIds) {
                    cli.execute(distro, listOf("rm", "-rf", "$groupDir/$artId"))
                    deleted.add("$group/$artId")
                }
            }
        }
    }
}
