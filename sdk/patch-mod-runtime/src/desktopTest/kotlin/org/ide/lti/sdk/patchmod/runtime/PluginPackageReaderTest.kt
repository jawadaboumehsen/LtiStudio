/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.runtime

import org.ide.lti.core.domain.plugin.PluginPackageCodec
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
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
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginPackageReaderTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("pkg_reader_test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private val payloadBytes = "test-payload-bytes".toByteArray(Charsets.UTF_8)
    private val payloadSha = MessageDigest.getInstance("SHA-256")
        .digest(payloadBytes)
        .joinToString("") { "%02x".format(it) }

    private val samplePayload = PayloadEntry(
        path = "assets/bin/tool.so",
        sizeBytes = payloadBytes.size.toLong(),
        sha256 = payloadSha,
    )

    private val sampleManifest = PluginManifest(
        schemaVersion = 1,
        sdkApiRange = ">=1.0.0 <2.0.0",
        publisher = "org.mifos",
        id = "reader-test",
        version = "1.0.0",
        displayName = "Reader Test",
        license = "MPL-2.0",
        compatibleTargets = listOf("target-a"),
        payloads = listOf(samplePayload),
    )

    private val sampleTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "reader-test",
        version = "1.0.0",
        settings = listOf(
            SettingDefinition.BooleanSetting(
                id = "opt_bool",
                label = "Option",
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

    private fun buildStandardEntries(
        manifest: PluginManifest = sampleManifest,
        planText: String = PluginPackageCodec.encodePlan(sampleTemplate),
        readmeText: String = "Readme content\n",
        licenseText: String = "License content\n",
        assets: Map<String, ByteArray> = mapOf("assets/bin/tool.so" to payloadBytes),
        extra: Map<String, ByteArray> = emptyMap(),
    ): Map<String, ByteArray> {
        val result = mutableMapOf<String, ByteArray>()
        result[PluginPackageCodec.MANIFEST] = PluginPackageCodec.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
        result[PluginPackageCodec.PLAN] = planText.toByteArray(Charsets.UTF_8)
        result[PluginPackageCodec.SETTINGS] =
            PluginPackageCodec.settingsSchema(sampleTemplate.settings).toByteArray(Charsets.UTF_8)
        result[PluginPackageCodec.README] = readmeText.toByteArray(Charsets.UTF_8)
        result[PluginPackageCodec.LICENSE] = licenseText.toByteArray(Charsets.UTF_8)
        result.putAll(assets)
        result.putAll(extra)
        return result
    }

    @Test
    fun testValidPackageReadsOkWithRightDigest() {
        val zipFile = tempDir.resolve("valid.lti-mod.zip")
        createZip(zipFile, buildStandardEntries())

        val result = PluginPackageReader.read(zipFile)
        val ok = assertIs<PackageReadResult.Ok>(result)

        val expectedDigest = PluginPackageCodec.contentIdentity(sampleManifest.payloads)
        assertEquals(expectedDigest, ok.pkg.contentDigest)
        assertEquals("reader-test", ok.pkg.manifest.id)
        assertEquals("reader-test", ok.pkg.template.id)
        assertEquals("Readme content\n", ok.pkg.readme)
        assertEquals("License content\n", ok.pkg.license)
        assertEquals(false, ok.pkg.hasSignature)
    }

    @Test
    fun testOversizeManifestRejectedWithoutReadingFurther() {
        val zipFile = tempDir.resolve("oversize_manifest.lti-mod.zip")
        val oversizeCount = PluginPackagePolicy.MANIFEST_MAX_BYTES.toInt() + 1
        val padding = " ".repeat(oversizeCount - 2)
        val oversizeManifestBytes = "{$padding}".toByteArray(Charsets.UTF_8)

        val entries = buildStandardEntries().toMutableMap()
        entries[PluginPackageCodec.MANIFEST] = oversizeManifestBytes
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageReader.MEMBER_TOO_LARGE })
    }

    @Test
    fun testEvilMemberPathRejectedWithInvalidMemberPath() {
        val zipFile = tempDir.resolve("evil_path.lti-mod.zip")
        val entries = buildStandardEntries(extra = mapOf("../evil" to "hacked".toByteArray(Charsets.UTF_8)))
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageCodec.INVALID_MEMBER_PATH })
    }

    @Test
    fun testUndeclaredAssetRejectedWithUndeclaredPayload() {
        val zipFile = tempDir.resolve("undeclared.lti-mod.zip")
        val entries = buildStandardEntries(extra = mapOf("assets/x" to "data".toByteArray(Charsets.UTF_8)))
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageCodec.UNDECLARED_PAYLOAD })
    }

    @Test
    fun testPayloadMismatchRejectedWithPayloadMismatch() {
        val zipFile = tempDir.resolve("tampered_payload.lti-mod.zip")
        val tamperedBytes = "tampered-bytes".toByteArray(Charsets.UTF_8)
        val entries = buildStandardEntries(assets = mapOf("assets/bin/tool.so" to tamperedBytes))
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageReader.PAYLOAD_MISMATCH })
    }

    @Test
    fun testSchemaVersion2RejectedWithUnsupportedSchema() {
        val zipFile = tempDir.resolve("schema_v2.lti-mod.zip")
        val v2Manifest = sampleManifest.copy(schemaVersion = 2)
        val entries = buildStandardEntries(manifest = v2Manifest)
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageReader.UNSUPPORTED_SCHEMA })
    }

    @Test
    fun testMalformedPlanRejectedWithInvalidJson() {
        val zipFile = tempDir.resolve("malformed_plan.lti-mod.zip")
        val entries = buildStandardEntries(planText = "{ not valid json")
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageReader.INVALID_JSON })
    }

    @Test
    fun testTwoMebibyteReadmeRejectedWithMemberTooLarge() {
        val zipFile = tempDir.resolve("oversize_readme.lti-mod.zip")
        val twoMiB = 2 * 1024 * 1024
        val bigReadme = "a".repeat(twoMiB)
        val entries = buildStandardEntries(readmeText = bigReadme)
        createZip(zipFile, entries)

        val result = PluginPackageReader.read(zipFile)
        val rejected = assertIs<PackageReadResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginPackageReader.MEMBER_TOO_LARGE })
    }
}
