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

import kotlinx.serialization.json.Json
import org.ide.lti.core.data.admission.atomicWrite
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.sdk.patchmod.runtime.PackageReadResult
import org.ide.lti.sdk.patchmod.runtime.PluginPackageReader
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

class InstalledPluginRepository(private val storeDir: Path) : PluginInstallPort {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
        prettyPrint = false
    }

    override suspend fun quarantine(sourceLabel: String): Path? = try {
        val sourcePath = Paths.get(sourceLabel)
        if (!Files.exists(sourcePath) || !Files.isRegularFile(sourcePath)) {
            null
        } else {
            val quarantineDir = storeDir.resolve("quarantine")
            Files.createDirectories(quarantineDir)
            val quarantined = quarantineDir.resolve("${UUID.randomUUID()}.lti-mod.zip")
            copyStream(sourcePath, quarantined)
            quarantined
        }
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    }

    override suspend fun inspect(quarantined: Path): PackageInspection =
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

    override suspend fun promote(quarantined: Path, identity: PackageIdentity): Boolean {
        if (!Files.exists(quarantined)) return false

        val versionDir = storeDir.resolve("packages")
            .resolve(identity.publisher)
            .resolve(identity.id)
            .resolve(identity.version)
        val recordFile = versionDir.resolve("record.json")

        return checkExistingPromotion(recordFile, quarantined, identity)
            ?: executePromotion(versionDir, recordFile, quarantined, identity)
    }

    private fun checkExistingPromotion(recordFile: Path, quarantined: Path, identity: PackageIdentity): Boolean? {
        if (!Files.exists(recordFile)) return null
        val existing = runCatching {
            json.decodeFromString(InstalledRecord.serializer(), Files.readString(recordFile))
        }.getOrNull()

        return when {
            existing == null -> null
            existing.identity.contentDigest != identity.contentDigest -> false
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

        val trust = if (inspection.hasSignature) {
            TrustDecision.TRUSTED_SIGNATURE
        } else {
            TrustDecision.TRUSTED_UNVERIFIED
        }

        val record = InstalledRecord(
            identity = identity,
            trust = trust,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = inspection.dependencies,
            conflicts = inspection.conflicts,
        )
        val recordJson = json.encodeToString(InstalledRecord.serializer(), record)
        atomicWrite(recordFile, recordJson)
        return true
    }

    override suspend fun installed(): List<InstalledRecord> {
        val packagesDir = storeDir.resolve("packages")
        if (!Files.exists(packagesDir)) return emptyList()

        val records = mutableListOf<InstalledRecord>()
        Files.walk(packagesDir).use { stream ->
            stream.filter { it.fileName?.toString() == "record.json" && Files.isRegularFile(it) }
                .forEach { path ->
                    runCatching {
                        val text = Files.readString(path)
                        json.decodeFromString(InstalledRecord.serializer(), text)
                    }.getOrNull()?.let { records.add(it) }
                }
        }
        return records.sortedWith(
            compareBy({ it.identity.publisher }, { it.identity.id }, { it.identity.version }),
        )
    }

    override suspend fun remove(identity: PackageIdentity): Boolean {
        val versionDir = storeDir.resolve("packages")
            .resolve(identity.publisher)
            .resolve(identity.id)
            .resolve(identity.version)
        if (!Files.exists(versionDir)) return false

        return versionDir.toFile().deleteRecursively()
    }

    override suspend fun updateRecord(record: InstalledRecord): Boolean {
        val versionDir = storeDir.resolve("packages")
            .resolve(record.identity.publisher)
            .resolve(record.identity.id)
            .resolve(record.identity.version)
        if (!Files.exists(versionDir)) return false

        val recordFile = versionDir.resolve("record.json")
        val recordJson = json.encodeToString(InstalledRecord.serializer(), record)
        atomicWrite(recordFile, recordJson)
        return true
    }

    private fun copyStream(source: Path, target: Path) {
        val buffer = ByteArray(CHUNK_SIZE)
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(target).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                }
            }
        }
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
    }
}
