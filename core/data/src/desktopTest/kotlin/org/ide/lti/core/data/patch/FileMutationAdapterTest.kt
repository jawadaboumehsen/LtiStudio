/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.patch

import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.plugin.ConcreteMutationPlan
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.EffectKind
import org.ide.lti.core.domain.plugin.FileMutationCodes
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.domain.plugin.MutationOutcome
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.SmaliEdit
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Comparator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@Suppress("TooManyFunctions")
class FileMutationAdapterTest {

    private lateinit var tempDir: Path
    private lateinit var workTree: Path
    private lateinit var assetRoot: Path
    private lateinit var adapter: FileMutationAdapter
    private lateinit var ctx: MutationContext

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("file-mutation-test-")
        workTree = tempDir.resolve("work")
        assetRoot = tempDir.resolve("assets")
        Files.createDirectories(workTree)
        Files.createDirectories(assetRoot)

        adapter = FileMutationAdapter()
        ctx = MutationContext(
            workTreeRelPath = workTree.toString(),
            partitionRoots = mapOf(
                "system" to "system",
                "product" to "product",
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        deleteRecursively(tempDir)
    }

    @Test
    fun happyPathExecutesAllMutationsAndProducesCorrectEffects() = runBlocking {
        setupWorkTreeFixture()

        val sampleTxt = workTree.resolve("system/etc/sample.txt")
        val sampleSha = sha256Of(sampleTxt)

        val replaceMe = workTree.resolve("system/bin/replace_me")
        val replaceSha = sha256Of(replaceMe)

        val plan = ConcreteMutationPlan(
            publisher = "test-publisher",
            id = "test-plan",
            version = "1.0.0",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op_copy",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "bin/created.bin"),
                ),
                ConcreteOperation.Replace(
                    id = "op_replace",
                    source = AssetRef("replacement.bin"),
                    destination = PartitionPath("system", "bin/replace_me"),
                    expectedSha256 = replaceSha,
                ),
                ConcreteOperation.Delete(
                    id = "op_delete",
                    target = PartitionPath("system", "bin/delete_me"),
                ),
                ConcreteOperation.PropertyPatch(
                    id = "op_prop_update",
                    target = PartitionPath("system", "build.prop"),
                    key = "ro.build.version",
                    value = "14",
                ),
                ConcreteOperation.PropertyPatch(
                    id = "op_prop_new",
                    target = PartitionPath("product", "build.prop"),
                    key = "ro.product.custom_feature",
                    value = "true",
                ),
                ConcreteOperation.TextPatch(
                    id = "op_text",
                    target = PartitionPath("system", "etc/sample.txt"),
                    context = "old world",
                    replacement = "brave new world",
                    expectedSha256 = sampleSha,
                ),
            ),
            notApplicable = emptyList(),
            digest = "test-digest",
        )

        val outcome = adapter.apply(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Applied>(outcome)
        assertEquals(6, outcome.effects.size)

        val copyEffect = outcome.effects.first { it.operationId == "op_copy" }
        assertEquals("system", copyEffect.partition)
        assertEquals("bin/created.bin", copyEffect.relativePath)
        assertEquals(EffectKind.CREATED, copyEffect.kind)
        assertTrue(copyEffect.sidecarUpdates.any { it.endsWith("configs/fs_config-system") })
        assertTrue(copyEffect.sidecarUpdates.any { it.endsWith("configs/file_context-system") })

        val replaceEffect = outcome.effects.first { it.operationId == "op_replace" }
        assertEquals(EffectKind.REPLACED, replaceEffect.kind)
        assertTrue(replaceEffect.sidecarUpdates.any { it.endsWith("configs/fs_config-system") })

        val deleteEffect = outcome.effects.first { it.operationId == "op_delete" }
        assertEquals(EffectKind.DELETED, deleteEffect.kind)
        assertTrue(deleteEffect.sidecarUpdates.any { it.endsWith("configs/fs_config-system") })

        val propUpdateEffect = outcome.effects.first { it.operationId == "op_prop_update" }
        assertEquals(EffectKind.PROPERTY_SET, propUpdateEffect.kind)
        assertTrue(propUpdateEffect.sidecarUpdates.isEmpty())

        val propNewEffect = outcome.effects.first { it.operationId == "op_prop_new" }
        assertEquals(EffectKind.PROPERTY_SET, propNewEffect.kind)
        assertTrue(propNewEffect.sidecarUpdates.isEmpty())

        val textEffect = outcome.effects.first { it.operationId == "op_text" }
        assertEquals(EffectKind.TEXT_PATCHED, textEffect.kind)
        assertTrue(textEffect.sidecarUpdates.isEmpty())

        // Verify filesystem states
        val createdFile = workTree.resolve("system/bin/created.bin")
        assertTrue(Files.exists(createdFile))
        assertEquals(sha256Of(assetRoot.resolve("new_asset.bin")), sha256Of(createdFile))

        val replacedFile = workTree.resolve("system/bin/replace_me")
        assertEquals(sha256Of(assetRoot.resolve("replacement.bin")), sha256Of(replacedFile))

        val deletedFile = workTree.resolve("system/bin/delete_me")
        assertFalse(Files.exists(deletedFile))

        val systemBuildProp = Files.readString(workTree.resolve("system/build.prop"), Charsets.UTF_8)
        assertTrue(systemBuildProp.contains("# System build properties"))
        assertTrue(systemBuildProp.contains("ro.build.type=user"))
        assertTrue(systemBuildProp.contains("ro.build.version=14"))
        assertFalse(systemBuildProp.contains("ro.build.version=13"))
        assertTrue(systemBuildProp.contains("ro.product.model=Pixel"))

        val productBuildProp = Files.readString(workTree.resolve("product/build.prop"), Charsets.UTF_8)
        assertTrue(productBuildProp.contains("# Product properties"))
        assertTrue(productBuildProp.contains("ro.product.brand=Google"))
        assertTrue(productBuildProp.contains("ro.product.custom_feature=true"))

        val patchedText = Files.readString(sampleTxt, Charsets.UTF_8)
        assertEquals("Hello brave new world text to patch here.\n", patchedText)
    }

    @Test
    fun previewProducesSameEffectsAndLeavesEveryFileByteIdentical() = runBlocking {
        setupWorkTreeFixture()

        val sampleTxt = workTree.resolve("system/etc/sample.txt")
        val sampleSha = sha256Of(sampleTxt)

        val replaceMe = workTree.resolve("system/bin/replace_me")
        val replaceSha = sha256Of(replaceMe)

        val plan = ConcreteMutationPlan(
            publisher = "test-publisher",
            id = "test-plan",
            version = "1.0.0",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op_copy",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "bin/created.bin"),
                ),
                ConcreteOperation.Replace(
                    id = "op_replace",
                    source = AssetRef("replacement.bin"),
                    destination = PartitionPath("system", "bin/replace_me"),
                    expectedSha256 = replaceSha,
                ),
                ConcreteOperation.Delete(
                    id = "op_delete",
                    target = PartitionPath("system", "bin/delete_me"),
                ),
                ConcreteOperation.PropertyPatch(
                    id = "op_prop_update",
                    target = PartitionPath("system", "build.prop"),
                    key = "ro.build.version",
                    value = "14",
                ),
                ConcreteOperation.PropertyPatch(
                    id = "op_prop_new",
                    target = PartitionPath("product", "build.prop"),
                    key = "ro.product.custom_feature",
                    value = "true",
                ),
                ConcreteOperation.TextPatch(
                    id = "op_text",
                    target = PartitionPath("system", "etc/sample.txt"),
                    context = "old world",
                    replacement = "brave new world",
                    expectedSha256 = sampleSha,
                ),
            ),
            notApplicable = emptyList(),
            digest = "test-digest",
        )

        val treeHashBefore = hashTree(workTree)

        val previewOutcome = adapter.preview(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Applied>(previewOutcome)
        assertEquals(6, previewOutcome.effects.size)

        val treeHashAfter = hashTree(workTree)
        assertEquals(treeHashBefore, treeHashAfter)

        // Ensure effects match what apply produces on the clean tree
        val applyOutcome = adapter.apply(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Applied>(applyOutcome)
        assertEquals(previewOutcome.effects, applyOutcome.effects)
    }

    @Test
    fun failureUnknownPartitionRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op1",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("vendor", "bin/tool"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PARTITION_NOT_IN_TREE)
    }

    @Test
    fun failurePathEscapesPartitionRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op1",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "../escape.txt"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PATH_ESCAPES_PARTITION)
    }

    @Test
    fun failurePathTraversesSymlinkRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val symlinkDir = workTree.resolve("system/symlink_dir")
        try {
            Files.createSymbolicLink(symlinkDir, tempDir)
        } catch (_: Exception) {
            return@runBlocking // Skip symlink test if OS / user privileges do not allow creating symlinks
        }

        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op1",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "symlink_dir/escape.txt"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PATH_ESCAPES_PARTITION)
    }

    @Test
    fun failureMissingAssetRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op1",
                    source = AssetRef("missing_asset.bin"),
                    destination = PartitionPath("system", "bin/created.bin"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.SOURCE_ASSET_MISSING)
    }

    @Test
    fun failureMissingTargetRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Delete(
                    id = "op1",
                    target = PartitionPath("system", "bin/non_existent.bin"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.TARGET_MISSING)
    }

    @Test
    fun failureWrongPreimageReplaceRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Replace(
                    id = "op1",
                    source = AssetRef("replacement.bin"),
                    destination = PartitionPath("system", "bin/replace_me"),
                    expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PREIMAGE_MISMATCH)
    }

    @Test
    fun failureWrongPreimageTextPatchRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.TextPatch(
                    id = "op1",
                    target = PartitionPath("system", "etc/sample.txt"),
                    context = "old world",
                    replacement = "new world",
                    expectedSha256 = "0000000000000000000000000000000000000000000000000000000000000000",
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PREIMAGE_MISMATCH)
    }

    @Test
    fun failureInvalidPropertyKeyRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.PropertyPatch(
                    id = "op1",
                    target = PartitionPath("system", "build.prop"),
                    key = "invalid=key",
                    value = "val",
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.PROPERTY_KEY_INVALID)
    }

    @Test
    fun failureTextContextNotFoundRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val sampleSha = sha256Of(workTree.resolve("system/etc/sample.txt"))
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.TextPatch(
                    id = "op1",
                    target = PartitionPath("system", "etc/sample.txt"),
                    context = "string not present in file",
                    replacement = "replacement",
                    expectedSha256 = sampleSha,
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.TEXT_CONTEXT_NOT_FOUND)
    }

    @Test
    fun failureTextContextAmbiguousRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val sampleTxt = workTree.resolve("system/etc/sample.txt")
        Files.writeString(sampleTxt, "context context", Charsets.UTF_8)
        val sampleSha = sha256Of(sampleTxt)

        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.TextPatch(
                    id = "op1",
                    target = PartitionPath("system", "etc/sample.txt"),
                    context = "context",
                    replacement = "replacement",
                    expectedSha256 = sampleSha,
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.TEXT_CONTEXT_AMBIGUOUS)
    }

    @Test
    fun failureOverlappingMutationRefusesAndWritesNothing() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op1",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "bin/overlap.bin"),
                ),
                ConcreteOperation.Copy(
                    id = "op2",
                    source = AssetRef("new_asset.bin"),
                    destination = PartitionPath("system", "bin/overlap.bin"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        assertFailureLeavesTreeUntouched(plan, FileMutationCodes.OVERLAPPING_MUTATION)
    }

    @Test
    fun failureUnsupportedOperationRefusesAndNamesTask() = runBlocking {
        setupWorkTreeFixture()
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "op_apk",
            version = "1",
            operations = listOf(
                ConcreteOperation.ApkJarPatch(
                    id = "op_apk",
                    target = PartitionPath("system", "framework/framework.jar"),
                    edits = listOf(SmaliEdit("Lcom/pkg/Cls;->m()V", "sha", "diff")),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        val treeBefore = hashTree(workTree)

        val outcome = adapter.apply(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Refused>(outcome)
        val err = outcome.report.errors.first { it.code == FileMutationCodes.UNSUPPORTED_OPERATION }
        assertEquals("op_apk", err.objectId)
        assertTrue(err.message.contains("T059"))

        val treeAfter = hashTree(workTree)
        assertEquals(treeBefore, treeAfter)

        // Also test HookInjection names T060
        val hookPlan = ConcreteMutationPlan(
            publisher = "p",
            id = "op_hook",
            version = "1",
            operations = listOf(
                ConcreteOperation.HookInjection(
                    id = "op_hook",
                    target = PartitionPath("system", "framework/framework.jar"),
                    match = BytecodeMatchSpec(
                        classDescriptor = "Lcom/pkg/Cls;",
                        methodDescriptor = "m()V",
                        anchor = AnchorPattern(listOf("nop")),
                        expectedPreimageSha256 = "sha",
                    ),
                    placement = HookPlacement.BEFORE,
                    hookDescriptor = "Lcom/pkg/Hook;->h()V",
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )
        val hookOutcome = adapter.apply(hookPlan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Refused>(hookOutcome)
        val hookErr = hookOutcome.report.errors.first { it.code == FileMutationCodes.UNSUPPORTED_OPERATION }
        assertEquals("op_hook", hookErr.objectId)
        assertTrue(hookErr.message.contains("T060"))
    }

    @Test
    fun streamCopy12MiBOver8MiBChunkBoundary() = runBlocking {
        setupWorkTreeFixture()
        val largeAsset = assetRoot.resolve("large_12mb.bin")
        val size = 12L * 1024 * 1024
        val chunk = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt()) { (it % 251).toByte() }

        val extraBytes = 4 * 1024 * 1024
        Files.newOutputStream(largeAsset).use { out ->
            out.write(chunk) // 8 MiB
            out.write(chunk, 0, extraBytes) // 4 MiB
        }
        assertEquals(size, Files.size(largeAsset))

        val expectedSha = sha256Of(largeAsset)
        val plan = ConcreteMutationPlan(
            publisher = "p",
            id = "p",
            version = "1",
            operations = listOf(
                ConcreteOperation.Copy(
                    id = "op_large_copy",
                    source = AssetRef("large_12mb.bin"),
                    destination = PartitionPath("system", "large.bin"),
                ),
            ),
            notApplicable = emptyList(),
            digest = "d",
        )

        val outcome = adapter.apply(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Applied>(outcome)

        val destination = workTree.resolve("system/large.bin")
        assertTrue(Files.exists(destination))
        assertEquals(size, Files.size(destination))
        assertEquals(expectedSha, sha256Of(destination))
    }

    private suspend fun assertFailureLeavesTreeUntouched(plan: ConcreteMutationPlan, expectedCode: String) {
        val treeBefore = hashTree(workTree)

        val previewOutcome = adapter.preview(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Refused>(previewOutcome)
        assertTrue(
            previewOutcome.report.errors.any { it.code == expectedCode },
            "Expected code $expectedCode in preview errors: ${previewOutcome.report.errors}",
        )
        assertEquals(treeBefore, hashTree(workTree))

        val applyOutcome = adapter.apply(plan, ctx, assetRoot.toString())
        assertIs<MutationOutcome.Refused>(applyOutcome)
        assertTrue(
            applyOutcome.report.errors.any { it.code == expectedCode },
            "Expected code $expectedCode in apply errors: ${applyOutcome.report.errors}",
        )
        assertEquals(treeBefore, hashTree(workTree))
    }

    private fun setupWorkTreeFixture() {
        val systemDir = workTree.resolve("system")
        val productDir = workTree.resolve("product")
        val configsDir = workTree.resolve("configs")
        Files.createDirectories(systemDir.resolve("bin"))
        Files.createDirectories(systemDir.resolve("etc"))
        Files.createDirectories(productDir)
        Files.createDirectories(configsDir)

        val systemPropLines = listOf(
            "# System build properties",
            "ro.build.type=user",
            "ro.build.version=13",
            "",
            "# Middle comment",
            "ro.product.model=Pixel",
            "",
        ).joinToString("\n")
        Files.writeString(systemDir.resolve("build.prop"), systemPropLines, Charsets.UTF_8)
        Files.writeString(
            productDir.resolve("build.prop"),
            "# Product properties\nro.product.brand=Google\n",
            Charsets.UTF_8,
        )
        Files.writeString(
            systemDir.resolve("etc/sample.txt"),
            "Hello old world text to patch here.\n",
            Charsets.UTF_8,
        )
        Files.write(systemDir.resolve("bin/replace_me"), byteArrayOf(1, 2, 3, 4, 5))
        Files.write(systemDir.resolve("bin/delete_me"), byteArrayOf(9, 8, 7))

        Files.writeString(
            configsDir.resolve("fs_config-system"),
            "system/bin/replace_me 0 0 755\n",
        )
        Files.writeString(
            configsDir.resolve("file_context-system"),
            "/system/bin/replace_me u:object_r:system_file:s0\n",
        )

        Files.write(assetRoot.resolve("new_asset.bin"), byteArrayOf(10, 20, 30, 40))
        Files.write(assetRoot.resolve("replacement.bin"), byteArrayOf(100, 101, 102))
    }

    private fun sha256Of(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        Files.newInputStream(file).use { input ->
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                md.update(buffer, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hashTree(dir: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        if (!Files.exists(dir)) return "EMPTY"
        Files.walk(dir).use { stream ->
            val sorted = stream.sorted().toList()
            for (path in sorted) {
                val rel = dir.relativize(path).toString().replace('\\', '/')
                md.update(rel.toByteArray(Charsets.UTF_8))
                hashFile(path, md, buffer)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun hashFile(path: Path, md: MessageDigest, buffer: ByteArray) {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) return
        Files.newInputStream(path).use { input ->
            var n: Int
            while (input.read(buffer).also { n = it } != -1) {
                md.update(buffer, 0, n)
            }
        }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }
}
