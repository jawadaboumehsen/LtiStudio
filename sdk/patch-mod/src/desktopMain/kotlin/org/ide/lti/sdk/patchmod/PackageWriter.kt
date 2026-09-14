/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod

import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Deterministic writer for .lti-mod.zip packages.
 */
object PackageWriter {

    val DOS_EPOCH_MILLIS: Long = LocalDateTime.of(1980, 1, 1, 0, 0, 0)
        .toInstant(ZoneOffset.UTC)
        .toEpochMilli()

    private const val BUFFER_SIZE = 8 * 1024 * 1024

    fun writePackage(
        manifest: PluginManifest,
        template: PluginPlanTemplate,
        readme: String,
        license: String,
        assets: Map<String, Path>,
        out: Path,
    ): PluginManifest {
        validateAssets(manifest, assets)
        val payloadEntries = measureAndHashAssets(assets)
        val updatedManifest = manifest.copy(payloads = payloadEntries)
        val textMembers = prepareTextMembers(updatedManifest, template, readme, license)
        writeZipFile(textMembers, assets, out)
        return updatedManifest
    }

    private fun validateAssets(manifest: PluginManifest, assets: Map<String, Path>) {
        validateAssetKeysAndFiles(assets)
        validateWithCodec(manifest, assets)
    }

    private fun validateAssetKeysAndFiles(assets: Map<String, Path>) {
        for ((key, path) in assets) {
            if (!key.startsWith(PluginPackageCodec.ASSETS_DIR) || key.length <= PluginPackageCodec.ASSETS_DIR.length) {
                throw IllegalArgumentException("Asset key must be under '${PluginPackageCodec.ASSETS_DIR}': '$key'")
            }
            if (!Files.isRegularFile(path)) {
                throw IllegalArgumentException("Asset file not found or not a regular file: '$path'")
            }
        }
    }

    private fun validateWithCodec(manifest: PluginManifest, assets: Map<String, Path>) {
        val simulatedMembers = listOf(
            PluginPackageCodec.MANIFEST,
            PluginPackageCodec.PLAN,
            PluginPackageCodec.SETTINGS,
            PluginPackageCodec.README,
            PluginPackageCodec.LICENSE,
        ) + assets.keys

        val dummyPayloads = assets.keys.map { PayloadEntry(path = it, sizeBytes = 0L, sha256 = "0".repeat(64)) }
        val testManifest = manifest.copy(payloads = dummyPayloads)
        val report = PluginPackageCodec.validateMembers(simulatedMembers, testManifest)
        if (report.hasBlockingErrors()) {
            val first = report.errors.first()
            throw IllegalArgumentException("Asset validation failed (${first.code}): ${first.message}")
        }
    }

    private fun measureAndHashAssets(assets: Map<String, Path>): List<PayloadEntry> {
        val sortedKeys = assets.keys.sorted()
        val entries = mutableListOf<PayloadEntry>()
        val buffer = ByteArray(BUFFER_SIZE)

        for (key in sortedKeys) {
            val path = assets[key]!!
            val md = MessageDigest.getInstance("SHA-256")
            var sizeBytes = 0L

            Files.newInputStream(path).use { stream ->
                while (true) {
                    val n = stream.read(buffer)
                    if (n == -1) break
                    md.update(buffer, 0, n)
                    sizeBytes += n
                }
            }
            val sha256 = md.digest().joinToString("") { "%02x".format(it) }
            entries.add(PayloadEntry(path = key, sizeBytes = sizeBytes, sha256 = sha256))
        }
        return entries
    }

    private fun prepareTextMembers(
        manifest: PluginManifest,
        template: PluginPlanTemplate,
        readme: String,
        license: String,
    ): Map<String, ByteArray> = mapOf(
        PluginPackageCodec.MANIFEST to normalizeText(PluginPackageCodec.encodeManifest(manifest)),
        PluginPackageCodec.PLAN to normalizeText(PluginPackageCodec.encodePlan(template)),
        PluginPackageCodec.SETTINGS to normalizeText(PluginPackageCodec.settingsSchema(template.settings)),
        PluginPackageCodec.README to normalizeText(readme),
        PluginPackageCodec.LICENSE to normalizeText(license),
    )

    private fun normalizeText(text: String): ByteArray {
        val lf = text.replace("\r\n", "\n").replace("\r", "\n")
        val withTrailing = if (lf.endsWith("\n")) lf else "$lf\n"
        return withTrailing.toByteArray(Charsets.UTF_8)
    }

    private fun writeZipFile(textMembers: Map<String, ByteArray>, assets: Map<String, Path>, out: Path) {
        if (out.parent != null) {
            Files.createDirectories(out.parent)
        }
        val allMemberNames = (textMembers.keys + assets.keys).sorted()
        val buffer = ByteArray(BUFFER_SIZE)

        Files.newOutputStream(out).use { fos ->
            ZipOutputStream(fos).use { zos ->
                zos.setComment(null)
                for (name in allMemberNames) {
                    writeMemberEntry(zos, name, textMembers[name], assets[name], buffer)
                }
            }
        }
    }

    private fun writeMemberEntry(
        zos: ZipOutputStream,
        name: String,
        textBytes: ByteArray?,
        assetPath: Path?,
        buffer: ByteArray,
    ) {
        val entry = ZipEntry(name).apply {
            time = DOS_EPOCH_MILLIS
            method = ZipEntry.DEFLATED
            extra = null
            comment = null
        }
        zos.putNextEntry(entry)
        if (textBytes != null) {
            zos.write(textBytes)
        } else if (assetPath != null) {
            copyAssetToZip(zos, assetPath, buffer)
        }
        zos.closeEntry()
    }

    private fun copyAssetToZip(zos: ZipOutputStream, assetPath: Path, buffer: ByteArray) {
        Files.newInputStream(assetPath).use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n == -1) break
                zos.write(buffer, 0, n)
            }
        }
    }
}
