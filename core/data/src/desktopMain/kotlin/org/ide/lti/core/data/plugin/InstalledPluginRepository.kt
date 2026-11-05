/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.ide.lti.core.data.admission.atomicWrite
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.sdk.patchmod.runtime.PackageReadResult
import org.ide.lti.sdk.patchmod.runtime.PluginPackageReader
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

class InstalledPluginRepository(
    private val storeDir: Path,
    private val connectionOpener: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection },
) : PluginInstallPort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    // A large package is copied off the caller's (UI) thread.
    override suspend fun quarantine(sourceLabel: String): Path? = handedOver {
        try {
            val sourcePath = Paths.get(sourceLabel)
            if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
                null
            } else {
                val quarantineDir = storeDir.resolve("quarantine")
                Files.createDirectories(quarantineDir)
                sweepAbandoned(quarantineDir)
                val quarantined = quarantineDir.resolve("${UUID.randomUUID()}.lti-mod.zip")
                copyStream(sourcePath, quarantined, PluginPackagePolicy.PACKAGE_MAX_COMPRESSED_BYTES + 1)
                quarantined
            }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Follows HTTPS-only redirects (a GitHub asset redirects to its storage host), caps the size, and keeps the file
     * only when its sha256 is [fileSha256] (`sha256:` prefix allowed), when one is given.
     */
    // Network and disk work never runs on the caller's (UI) thread.
    override suspend fun download(url: String, fileSha256: String?): Path? =
        handedOver { downloadBlocking(url, fileSha256) }

    /**
     * Runs [blocking] on the IO dispatcher and hands its quarantine file to the caller. Cancelled before the caller
     * has it (the result is then lost on the way back), the file is deleted here: nobody else knows its name.
     */
    private suspend fun handedOver(blocking: suspend () -> Path?): Path? {
        var owned: Path? = null
        try {
            return withContext(Dispatchers.IO) { blocking()?.also { owned = it } }.also { owned = null }
        } finally {
            owned?.let { withContext(NonCancellable + Dispatchers.IO) { Files.deleteIfExists(it) } }
        }
    }

    private suspend fun downloadBlocking(url: String, fileSha256: String?): Path? {
        val quarantineDir = storeDir.resolve("quarantine").also { Files.createDirectories(it) }
        val target = quarantineDir.resolve("${UUID.randomUUID()}.lti-mod.zip")
        val actual = try {
            openFollowingRedirects(URI(url))?.use { stream(it, target) }
        } catch (_: IOException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
        val expected = fileSha256?.removePrefix("sha256:")?.lowercase()
        if (actual == null || (expected != null && actual != expected)) {
            Files.deleteIfExists(target)
            return null
        }
        return target
    }

    @Suppress("ReturnCount")
    private fun openFollowingRedirects(start: URI): InputStream? {
        var uri = start
        repeat(MAX_REDIRECTS + 1) {
            if (uri.scheme?.lowercase() != "https" || !uri.userInfo.isNullOrBlank()) return null
            val conn = connectionOpener(uri).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
            when (conn.responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    uri = uri.resolve(location ?: return null)
                }

                in 200..299 -> return conn.inputStream

                else -> {
                    conn.disconnect()
                    return null
                }
            }
        }
        return null
    }

    /** Writes [input] to [target] and returns its sha256, or null past the package size limit. */
    private fun stream(input: InputStream, target: Path): String? {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(CHUNK_SIZE)
        var total = 0L
        Files.newOutputStream(target).use { output ->
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                total += read
                if (total > PluginPackagePolicy.PACKAGE_MAX_COMPRESSED_BYTES) return null
                digest.update(buffer, 0, read)
                output.write(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    // Network and disk work never runs on the caller's (UI) thread.
    override suspend fun inspect(quarantined: Path): PackageInspection =
        withContext(Dispatchers.IO) { inspectBlocking(quarantined) }

    private suspend fun inspectBlocking(quarantined: Path): PackageInspection =
        when (val result = PluginPackageReader.read(quarantined)) {
            is PackageReadResult.Ok -> {
                val pkg = result.pkg
                val identity = PackageIdentity(
                    publisher = pkg.manifest.publisher,
                    id = pkg.manifest.id,
                    version = pkg.manifest.version,
                    contentDigest = pkg.contentDigest,
                )
                PackageInspection(
                    identity = identity,
                    hasSignature = pkg.hasSignature,
                    report = ValidationReport(),
                    dependencies = pkg.manifest.dependencies,
                    conflicts = pkg.manifest.conflicts,
                    displayName = pkg.manifest.displayName,
                    summary = summaryOf(pkg.readme),
                    compatibility = pkg.template.compatibility,
                )
            }

            is PackageReadResult.Rejected -> {
                PackageInspection(
                    identity = null,
                    hasSignature = false,
                    report = result.report,
                    dependencies = emptyList(),
                    conflicts = emptyList(),
                )
            }
        }

    // Network and disk work never runs on the caller's (UI) thread.
    override suspend fun promote(quarantined: Path, identity: PackageIdentity): Boolean =
        withContext(Dispatchers.IO) { promoteBlocking(quarantined, identity) }

    private suspend fun promoteBlocking(quarantined: Path, identity: PackageIdentity): Boolean = promotion.withLock {
        val versionDir = versionDirOf(identity)
        if (!Files.exists(quarantined) || versionDir == null) return@withLock false
        val recordFile = versionDir.resolve("record.json")

        checkExistingPromotion(recordFile, quarantined, identity)
            ?: executePromotion(versionDir, recordFile, quarantined, identity)
    }

    /** Two installs of one package must not interleave their check and move (the later would replace the first). */
    private val promotion = Mutex()

    private fun checkExistingPromotion(recordFile: Path, quarantined: Path, identity: PackageIdentity): Boolean? {
        if (!Files.exists(recordFile)) return null
        val existing = runCatching {
            json.decodeFromString(InstalledRecord.serializer(), Files.readString(recordFile))
        }.getOrNull()

        return when {
            existing == null -> null

            existing.identity.contentDigest != identity.contentDigest -> false

            !Files.isRegularFile(recordFile.resolveSibling("package.lti-mod.zip")) -> null

            else -> {
                Files.deleteIfExists(quarantined)
                true
            }
        }
    }

    private suspend fun executePromotion(
        versionDir: Path,
        recordFile: Path,
        quarantined: Path,
        identity: PackageIdentity,
    ): Boolean {
        val inspection = inspect(quarantined)
        val inspectedIdentity = inspection.identity
        if (inspectedIdentity == null || inspectedIdentity.contentDigest != identity.contentDigest) {
            return false
        }

        Files.createDirectories(versionDir)
        val packageFile = versionDir.resolve("package.lti-mod.zip")
        atomicMove(quarantined, packageFile)

        // A signature file is not verified (no trusted publisher keys yet), so it grants no trust.
        val trust = TrustDecision.TRUSTED_UNVERIFIED

        val record = InstalledRecord(
            identity = identity,
            trust = trust,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = inspection.dependencies,
            conflicts = inspection.conflicts,
            displayName = inspection.displayName,
            summary = inspection.summary,
        )
        val recordJson = json.encodeToString(InstalledRecord.serializer(), record)
        atomicWrite(recordFile, recordJson)
        return true
    }

    override suspend fun installed(): List<InstalledRecord> {
        val packagesDir = storeDir.resolve("packages")
        if (!Files.exists(packagesDir)) return emptyList()

        val records = mutableListOf<Pair<InstalledRecord, Path>>()
        Files.walk(packagesDir).use { stream ->
            stream.filter { it.fileName?.toString() == "record.json" && Files.isRegularFile(it) }
                .forEach { path ->
                    runCatching {
                        val text = Files.readString(path)
                        json.decodeFromString(InstalledRecord.serializer(), text)
                    }.getOrNull()?.let { records.add(it to path.parent) }
                }
        }
        return records.map { (record, dir) -> withNames(record, dir) }.sortedWith(
            compareBy({ it.identity.publisher }, { it.identity.id }, { it.identity.version }),
        )
    }

    /** A record installed before names were kept gets them from its package. */
    private suspend fun withNames(record: InstalledRecord, versionDir: Path): InstalledRecord {
        if (record.displayName.isNotBlank()) return record
        val inspection = inspect(versionDir.resolve("package.lti-mod.zip"))
        return record.copy(displayName = inspection.displayName, summary = inspection.summary)
    }

    override suspend fun remove(identity: PackageIdentity): Boolean {
        val versionDir = versionDirOf(identity)?.takeIf { Files.exists(it) } ?: return false
        return versionDir.toFile().deleteRecursively()
    }

    override suspend fun updateRecord(record: InstalledRecord): Boolean {
        val versionDir = versionDirOf(record.identity)?.takeIf { Files.exists(it) } ?: return false

        val recordFile = versionDir.resolve("record.json")
        val recordJson = json.encodeToString(InstalledRecord.serializer(), record)
        atomicWrite(recordFile, recordJson)
        return true
    }

    /** Copies at most [limit] bytes: an oversized file never fills the store, and the reader refuses it by size. */
    private fun copyStream(source: Path, target: Path, limit: Long) {
        val buffer = ByteArray(CHUNK_SIZE)
        var left = limit
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(target).use { output ->
                while (left > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    left -= read
                }
            }
        }
    }

    /**
     * The folder of [identity] in the store, or null when a publisher, id or version is not a plain folder name or
     * the path would leave `packages/` (a crafted record must never point a write or delete elsewhere).
     */
    private fun versionDirOf(identity: PackageIdentity): Path? {
        val parts = listOf(identity.publisher, identity.id, identity.version)
        if (!parts.all(PluginPackageCodec::isSafeIdentitySegment)) return null
        val packages = storeDir.resolve("packages").toAbsolutePath().normalize()
        val dir = parts.fold(packages) { path, part -> path.resolve(part) }.normalize()
        return dir.takeIf { it.startsWith(packages) && it != packages }
    }

    override suspend fun discard(quarantined: Path) {
        runCatching { Files.deleteIfExists(quarantined) }
    }

    private fun atomicMove(source: Path, target: Path) {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    companion object {
        private const val CHUNK_SIZE = 8 * 1024 * 1024
        private const val MAX_REDIRECTS = 5
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}

/** The README's first paragraph that is not a heading, on one line. */
internal fun summaryOf(readme: String): String = readme.replace("\r\n", "\n").split(Regex("\n\\s*\n"))
    .map { it.trim() }
    .firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
    ?.replace('\n', ' ')
    .orEmpty()

/** A day-old copy is nobody's: an inspection left behind when the screen or the app closed. */
private fun sweepAbandoned(quarantineDir: Path) {
    val cutoff = java.time.Instant.now().minus(java.time.Duration.ofDays(1))
    runCatching {
        Files.list(quarantineDir).use { copies ->
            copies.filter { Files.getLastModifiedTime(it).toInstant().isBefore(cutoff) }
                .forEach { Files.deleteIfExists(it) }
        }
    }
}
