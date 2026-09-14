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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class InstalledPluginRepositoryTest {

    private lateinit var tempStoreDir: Path
    private lateinit var tempWorkDir: Path

    @BeforeTest
    fun setUp() {
        tempStoreDir = Files.createTempDirectory("installed_plugin_store")
        tempWorkDir = Files.createTempDirectory("installed_plugin_work")
    }

    @AfterTest
    fun tearDown() {
        tempStoreDir.toFile().deleteRecursively()
        tempWorkDir.toFile().deleteRecursively()
    }

    private fun createZip(file: Path, entries: Map<String, ByteArray>) {
        Files.newOutputStream(file).use { fos ->
            ZipOutputStream(fos).use { zos ->
                for ((name, data) in entries) {
                    val entry = ZipEntry(name)
                    zos.putNextEntry(entry)
                    zos.write(data)
                    zos.closeEntry()
                }
            }
        }
    }

    private fun buildPackageZip(
        dest: Path,
        publisher: String = "org.mifos",
        id: String = "repo-test",
        version: String = "1.0.0",
        payloadBytes: ByteArray = "payload-data".toByteArray(Charsets.UTF_8),
    ): PackageIdentity {
        val payloadSha = MessageDigest.getInstance("SHA-256")
            .digest(payloadBytes)
            .joinToString("") { "%02x".format(it) }

        val payloadEntry = PayloadEntry(
            path = "assets/bin/tool.so",
            sizeBytes = payloadBytes.size.toLong(),
            sha256 = payloadSha,
        )

        val manifest = PluginManifest(
            schemaVersion = 1,
            sdkApiRange = ">=1.0.0 <2.0.0",
            publisher = publisher,
            id = id,
            version = version,
            displayName = "Repo Test Package",
            license = "MPL-2.0",
            compatibleTargets = listOf("target-a"),
            payloads = listOf(payloadEntry),
        )

        val template = PluginPlanTemplate(
            schemaVersion = 1,
            publisher = publisher,
            id = id,
            version = version,
            settings = listOf(
                SettingDefinition.BooleanSetting(
                    id = "opt_flag",
                    label = "Flag",
                    default = true,
                ),
            ),
            operations = listOf(
                Operation.Copy(
                    id = "op_copy",
                    source = AssetRef("assets/bin/tool.so"),
                    destination = PartitionPath("system", "bin/tool.so"),
                ),
            ),
        )

        val entries = mapOf(
            PluginPackageCodec.MANIFEST to PluginPackageCodec.encodeManifest(manifest).toByteArray(Charsets.UTF_8),
            PluginPackageCodec.PLAN to PluginPackageCodec.encodePlan(template).toByteArray(Charsets.UTF_8),
            PluginPackageCodec.SETTINGS to
                PluginPackageCodec.settingsSchema(template.settings).toByteArray(Charsets.UTF_8),
            PluginPackageCodec.README to "Readme\n".toByteArray(Charsets.UTF_8),
            PluginPackageCodec.LICENSE to "License\n".toByteArray(Charsets.UTF_8),
            "assets/bin/tool.so" to payloadBytes,
        )

        createZip(dest, entries)
        val contentDigest = PluginPackageCodec.contentIdentity(listOf(payloadEntry))
        return PackageIdentity(publisher, id, version, contentDigest)
    }

    @Test
    fun testQuarantineInspectPromoteListAndRemove() = runTest {
        val repo = InstalledPluginRepository(tempStoreDir)
        val srcZip = tempWorkDir.resolve("original.lti-mod.zip")
        val identity = buildPackageZip(srcZip)

        // 1. Quarantine
        val quarantined = repo.quarantine(srcZip.toString())
        assertNotNull(quarantined)
        assertTrue(Files.exists(quarantined))

        // 2. Inspect
        val inspection = repo.inspect(quarantined)
        assertEquals(identity, inspection.identity)
        assertFalse(inspection.report.hasBlockingErrors())

        // 3. Promote
        val promoted = repo.promote(quarantined, identity)
        assertTrue(promoted)
        // Original quarantine file moved
        assertFalse(Files.exists(quarantined))

        // 4. Installed() lists it
        val records = repo.installed()
        assertEquals(1, records.size)
        assertEquals(identity, records[0].identity)

        // 5. Second promote of same version with different bytes returns false and leaves original
        val tamperedZip = tempWorkDir.resolve("tampered.lti-mod.zip")
        val diffIdentity = buildPackageZip(tamperedZip, payloadBytes = "different-data".toByteArray(Charsets.UTF_8))
        val tamperedQuarantined = repo.quarantine(tamperedZip.toString())
        assertNotNull(tamperedQuarantined)

        val secondPromote = repo.promote(tamperedQuarantined, diffIdentity)
        assertFalse(secondPromote)

        val recordsAfterTamper = repo.installed()
        assertEquals(1, recordsAfterTamper.size)
        assertEquals(identity.contentDigest, recordsAfterTamper[0].identity.contentDigest)

        // 6. Remove deletes it
        val removed = repo.remove(identity)
        assertTrue(removed)
        assertTrue(repo.installed().isEmpty())
    }
}
