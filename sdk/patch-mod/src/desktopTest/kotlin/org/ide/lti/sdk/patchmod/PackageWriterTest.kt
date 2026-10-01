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

import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackageWriterTest {

    private lateinit var tempDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("pkg_writer_test")
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private val sampleManifest = PluginManifest(
        schemaVersion = 1,
        sdkApiRange = ">=1.0.0 <2.0.0",
        publisher = "org.mifos",
        id = "writer-test",
        version = "1.0.0",
        displayName = "Writer Test",
        license = "MPL-2.0",
        compatibleTargets = listOf("target-a"),
        payloads = emptyList(),
    )

    private val sampleTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "writer-test",
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

    private fun createAssetFile(name: String, content: String): Path {
        val file = tempDir.resolve(name)
        if (file.parent != null) {
            Files.createDirectories(file.parent)
        }
        Files.write(file, content.toByteArray(Charsets.UTF_8))
        return file
    }

    private fun sha256OfFile(path: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buf = ByteArray(8192)
            while (true) {
                val n = stream.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testDeterministicWritesProduceIdenticalSha256() {
        val asset1 = createAssetFile("tool1.so", "payload-content-one")
        val asset2 = createAssetFile("tool2.so", "payload-content-two")
        val assets = mapOf(
            "assets/z.bin" to asset2,
            "assets/a.bin" to asset1,
        )

        val out1 = tempDir.resolve("run1.lti-mod.zip")
        val out2 = tempDir.resolve("run2.lti-mod.zip")

        PackageWriter.writePackage(
            manifest = sampleManifest,
            template = sampleTemplate,
            readme = "Readme for determinism\r\n",
            license = "License for determinism\r\n",
            assets = assets,
            out = out1,
        )

        PackageWriter.writePackage(
            manifest = sampleManifest,
            template = sampleTemplate,
            readme = "Readme for determinism\n",
            license = "License for determinism\n",
            assets = assets,
            out = out2,
        )

        val hash1 = sha256OfFile(out1)
        val hash2 = sha256OfFile(out2)
        assertEquals(hash1, hash2)
    }

    @Test
    fun testMemberOrderIsSorted() {
        val asset1 = createAssetFile("first.bin", "1")
        val asset2 = createAssetFile("second.bin", "2")
        val assets = mapOf(
            "assets/z_item.bin" to asset2,
            "assets/a_item.bin" to asset1,
        )
        val out = tempDir.resolve("sorted.lti-mod.zip")

        PackageWriter.writePackage(
            manifest = sampleManifest,
            template = sampleTemplate,
            readme = "Readme\n",
            license = "License\n",
            assets = assets,
            out = out,
        )

        ZipFile(out.toFile()).use { zf ->
            val memberNames = zf.entries().asSequence().map { it.name }.toList()
            assertEquals(memberNames.sorted(), memberNames)
        }
    }

    @Test
    fun testEveryEntryTimeEqualsConstant() {
        val asset = createAssetFile("tool.so", "bytes")
        val assets = mapOf("assets/bin/tool.so" to asset)
        val out = tempDir.resolve("time_check.lti-mod.zip")

        PackageWriter.writePackage(
            manifest = sampleManifest,
            template = sampleTemplate,
            readme = "Readme\n",
            license = "License\n",
            assets = assets,
            out = out,
        )

        ZipFile(out.toFile()).use { zf ->
            val entries = zf.entries().asSequence().toList()
            assertTrue(entries.isNotEmpty())
            for (entry in entries) {
                assertEquals(PackageWriter.DOS_EPOCH_MILLIS, entry.time)
            }
        }
    }

    @Test
    fun testReturnedManifestPayloadsMatchAssets() {
        val contentA = "binary-asset-alpha"
        val contentB = "binary-asset-beta"
        val assetA = createAssetFile("alpha.bin", contentA)
        val assetB = createAssetFile("beta.bin", contentB)

        val assets = mapOf(
            "assets/alpha.bin" to assetA,
            "assets/beta.bin" to assetB,
        )
        val out = tempDir.resolve("payload_match.lti-mod.zip")

        val returnedManifest = PackageWriter.writePackage(
            manifest = sampleManifest,
            template = sampleTemplate,
            readme = "Readme\n",
            license = "License\n",
            assets = assets,
            out = out,
        )

        assertEquals(2, returnedManifest.payloads.size)
        val byPath = returnedManifest.payloads.associateBy { it.path }

        val payloadA = byPath["assets/alpha.bin"]
        assertTrue(payloadA != null)
        assertEquals(contentA.toByteArray(Charsets.UTF_8).size.toLong(), payloadA.sizeBytes)
        assertEquals(sha256OfFile(assetA), payloadA.sha256)

        val payloadB = byPath["assets/beta.bin"]
        assertTrue(payloadB != null)
        assertEquals(contentB.toByteArray(Charsets.UTF_8).size.toLong(), payloadB.sizeBytes)
        assertEquals(sha256OfFile(assetB), payloadB.sha256)
    }

    @Test
    fun testAssetKeyOutsideAssetsThrows() {
        val asset = createAssetFile("outside.txt", "data")
        val assets = mapOf("outside/bad.txt" to asset)
        val out = tempDir.resolve("bad_key.lti-mod.zip")

        assertFailsWith<IllegalArgumentException> {
            PackageWriter.writePackage(
                manifest = sampleManifest,
                template = sampleTemplate,
                readme = "Readme\n",
                license = "License\n",
                assets = assets,
                out = out,
            )
        }
    }
}
