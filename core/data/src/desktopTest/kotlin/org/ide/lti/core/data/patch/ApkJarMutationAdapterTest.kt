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
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.plugin.ApkJarOutcome
import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.domain.plugin.ComponentPatchPort
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.DecodedComponent
import org.ide.lti.core.domain.plugin.EffectKind
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.SmaliEdit
import java.nio.file.Files
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

@Suppress("LargeClass", "TooManyFunctions")
class ApkJarMutationAdapterTest {

    private lateinit var tempDir: Path
    private lateinit var workTree: Path
    private lateinit var assetRoot: Path
    private lateinit var fakePort: FakeComponentPatchPort
    private lateinit var adapter: ApkJarMutationAdapter
    private lateinit var ctx: MutationContext
    private lateinit var frameworkJar: Path

    private val fixtureSmali = """
.class public Lcom/android/server/pm/InstallPackageHelper;
.super Ljava/lang/Object;

.method public scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V
    .locals 16

    .line 100
    # Setup tracing
    :prologue
    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;

    .line 101
    invoke-static/range {v1 .. v6}, Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI(...)V

    .line 102
    const-string v5, "LTI_CPS"

    move-result-object v0

    .line 103
    check-cast v0, Ljava/lang/String;

    .line 104
    invoke-virtual {v0, v1, v15}, Lcom/android/server/pm/InstallPackageHelper;->maybeClearProfilesForUpgradesLI(...)V

    .line 105
    :label_disambiguate
    const-string v5, "OTHER_CONST"

    move-result-object v1

    .line 106
    if-eqz v1, :cond_skip

    .line 107
    new-instance v2, Ljava/lang/StringBuilder;

    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V

    :cond_skip
    return-void
.end method
    """.trimIndent()

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("apk-jar-mutation-test-")
        workTree = tempDir.resolve("work")
        assetRoot = tempDir.resolve("assets")
        Files.createDirectories(workTree)
        Files.createDirectories(assetRoot)

        val systemFramework = workTree.resolve("system/framework")
        Files.createDirectories(systemFramework)
        frameworkJar = systemFramework.resolve("framework.jar")
        Files.writeString(frameworkJar, "DEX_CONTAINER_PLACEHOLDER")

        fakePort = FakeComponentPatchPort()
        adapter = ApkJarMutationAdapter(fakePort, defaultAssetRoot = assetRoot.toString())
        ctx = MutationContext(
            workTreeRelPath = workTree.toString(),
            partitionRoots = mapOf(
                "system" to "system",
            ),
        )
    }

    @AfterTest
    fun tearDown() {
        deleteRecursively(tempDir)
    }

    @Test
    fun oneComponentWithTwoApkJarPatchOperationsDecodesOnceRebuildsOnceAndBothEditsLand() = runBlocking {
        val memberPath = "smali/com/example/Test.smali"
        val initialContent = """
            line 1: original A
            line 2: original B
            line 3: original C
        """.trimIndent()
        fakePort.members[memberPath] = initialContent

        val sha1 = sha256Of(initialContent)
        val diff1 = """
            @@ -1,3 +1,3 @@
            -line 1: original A
            +line 1: patched A
             line 2: original B
             line 3: original C
        """.trimIndent()

        val contentAfterOp1 = """
            line 1: patched A
            line 2: original B
            line 3: original C
        """.trimIndent()
        val sha2 = sha256Of(contentAfterOp1)
        val diff2 = """
            @@ -1,3 +1,3 @@
             line 1: patched A
            -line 2: original B
            +line 2: patched B
             line 3: original C
        """.trimIndent()

        val op1 = ConcreteOperation.ApkJarPatch(
            id = "op_patch_1",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(SmaliEdit(memberPath, sha1, diff1)),
        )
        val op2 = ConcreteOperation.ApkJarPatch(
            id = "op_patch_2",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(SmaliEdit(memberPath, sha2, diff2)),
        )

        val outcome = adapter.apply(listOf(op1, op2), ctx, assetRoot.toString(), preview = false)
        val applied = assertIs<ApkJarOutcome.Applied>(outcome)

        assertEquals(2, applied.effects.size)
        assertEquals(1, applied.receipts.size)
        assertEquals(listOf("op_patch_1", "op_patch_2"), applied.receipts.first().operationIds)
        assertEquals(listOf(memberPath), applied.receipts.first().changedMethods)

        assertEquals(1, fakePort.decodeCount)
        assertEquals(1, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
        assertEquals(2, fakePort.writeCount)

        val finalMember = fakePort.members[memberPath]
        assertTrue(finalMember != null && finalMember.contains("line 1: patched A"))
        assertTrue(finalMember != null && finalMember.contains("line 2: patched B"))
    }

    @Test
    fun previewWritesNothingAndNeverRebuildsButReturnsSameEffectsAndReceipts() = runBlocking {
        val memberPath = "smali/com/example/Test.smali"
        val initialContent = "line 1\nline 2\nline 3"
        fakePort.members[memberPath] = initialContent

        val sha = sha256Of(initialContent)
        val diff = """
            @@ -1,3 +1,3 @@
             line 1
            -line 2
            +line 2 previewed
             line 3
        """.trimIndent()

        val op = ConcreteOperation.ApkJarPatch(
            id = "op_preview",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(SmaliEdit(memberPath, sha, diff)),
        )

        val outcome = adapter.apply(listOf(op), ctx, assetRoot.toString(), preview = true)
        val applied = assertIs<ApkJarOutcome.Applied>(outcome)

        assertEquals(1, applied.effects.size)
        assertEquals(EffectKind.REPLACED, applied.effects.first().kind)
        assertEquals(1, applied.receipts.size)
        assertEquals(listOf("op_preview"), applied.receipts.first().operationIds)
        assertEquals(listOf(memberPath), applied.receipts.first().changedMethods)

        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(0, fakePort.writeCount)
        assertEquals(1, fakePort.discardCount)
        assertEquals(initialContent, fakePort.members[memberPath])
    }

    @Test
    fun failureMissingComponentReturnsComponentMissing() = runBlocking {
        val missingJarOp = ConcreteOperation.ApkJarPatch(
            id = "op_missing_comp",
            target = PartitionPath("system", "framework/non_existent.jar"),
            edits = emptyList(),
        )

        val outcome = adapter.apply(listOf(missingJarOp), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.COMPONENT_MISSING, refused.report.errors.first().code)
        assertEquals(Severity.ERROR, refused.report.errors.first().severity)
        assertEquals(0, fakePort.decodeCount)
    }

    @Test
    fun failureDecodeFailedWhenPortReturnsNull() = runBlocking {
        fakePort.decodeFails = true
        val op = ConcreteOperation.ApkJarPatch(
            id = "op_decode_fail",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = emptyList(),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.DECODE_FAILED, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(0, fakePort.discardCount)
    }

    @Test
    fun failureRebuildFailedReturnsRebuildFailed() = runBlocking {
        fakePort.rebuildFails = true
        val op = ConcreteOperation.ApkJarPatch(
            id = "op_rebuild_fail",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = emptyList(),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.REBUILD_FAILED, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(1, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun failureMissingSmaliMemberReturnsSmaliMemberMissing() = runBlocking {
        val op = ConcreteOperation.ApkJarPatch(
            id = "op_missing_member",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(
                SmaliEdit(
                    memberDescriptor = "smali/non/existent/Foo.smali",
                    expectedSha256 = "dummy",
                    unifiedDiff = "dummy",
                ),
            ),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.SMALI_MEMBER_MISSING, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun failureWrongPreimageReturnsSmaliPreimageMismatch() = runBlocking {
        val member = "smali/com/example/Test.smali"
        fakePort.members[member] = "actual content"

        val op = ConcreteOperation.ApkJarPatch(
            id = "op_preimage_fail",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(
                SmaliEdit(
                    memberDescriptor = member,
                    expectedSha256 = "0".repeat(64),
                    unifiedDiff = "dummy",
                ),
            ),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.SMALI_PREIMAGE_MISMATCH, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun failureContextNotFoundReturnsPatchContextNotFound() = runBlocking {
        val member = "smali/com/example/Test.smali"
        val content = "line 1\nline 2\nline 3"
        fakePort.members[member] = content

        val diff = """
            @@ -1,3 +1,3 @@
            -line 999
            +line 999 replaced
        """.trimIndent()

        val op = ConcreteOperation.ApkJarPatch(
            id = "op_context_not_found",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(SmaliEdit(member, sha256Of(content), diff)),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun failureContextAmbiguousReturnsPatchContextAmbiguous() = runBlocking {
        val member = "smali/com/example/Test.smali"
        val content = "dup\nline\ndup\nline"
        fakePort.members[member] = content

        val diff = """
            @@ -1,2 +1,2 @@
            -dup
            +replaced
             line
        """.trimIndent()

        val op = ConcreteOperation.ApkJarPatch(
            id = "op_context_ambiguous",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(SmaliEdit(member, sha256Of(content), diff)),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun failureDuplicateClassReturnsDuplicateClass() = runBlocking {
        val payload = assetRoot.resolve("NewClass.smali")
        Files.writeString(payload, ".class public Lcom/example/ExistingClass;\n")

        fakePort.members["smali/com/example/ExistingClass.smali"] = ".class public Lcom/example/ExistingClass;\n"

        val op = ConcreteOperation.CompiledClassMerge(
            id = "op_dup_class",
            target = PartitionPath("system", "framework/framework.jar"),
            payload = AssetRef("NewClass.smali"),
            classDescriptors = listOf("Lcom/example/ExistingClass;"),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.DUPLICATE_CLASS, refused.report.errors.first().code)
    }

    @Test
    fun failurePlatformClassCollisionReturnsPlatformClassCollision() = runBlocking {
        val payload = assetRoot.resolve("PlatformClass.smali")
        Files.writeString(payload, ".class public Landroid/widget/MyWidget;\n")

        val op = ConcreteOperation.CompiledClassMerge(
            id = "op_plat_col",
            target = PartitionPath("system", "framework/framework.jar"),
            payload = AssetRef("PlatformClass.smali"),
            classDescriptors = listOf("Landroid/widget/MyWidget;"),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.PLATFORM_CLASS_COLLISION, refused.report.errors.first().code)
    }

    @Test
    fun failureUnresolvedDependencyReturnsUnresolvedDependency() = runBlocking {
        val payload = assetRoot.resolve("ClassWithDep.smali")
        Files.writeString(payload, ".class public Lcom/example/ClassWithDep;\n")

        val op = ConcreteOperation.CompiledClassMerge(
            id = "op_unresolved_dep",
            target = PartitionPath("system", "framework/framework.jar"),
            payload = AssetRef("ClassWithDep.smali"),
            classDescriptors = listOf("Lcom/example/ClassWithDep;"),
            dependencies = listOf("Lcom/example/MissingDep;"),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.UNRESOLVED_DEPENDENCY, refused.report.errors.first().code)
    }

    @Test
    fun failureMissingPayloadReturnsPayloadMissing() = runBlocking {
        val op = ConcreteOperation.CompiledClassMerge(
            id = "op_missing_payload",
            target = PartitionPath("system", "framework/framework.jar"),
            payload = AssetRef("non_existent_payload.smali"),
            classDescriptors = listOf("Lcom/example/Foo;"),
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.PAYLOAD_MISSING, refused.report.errors.first().code)
    }

    @Test
    fun failureBadHookDescriptorReturnsHookSignatureMismatch() = runBlocking {
        val spec = BytecodeMatchSpec(
            classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;",
            methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
            anchor = AnchorPattern(opcodeSequence = listOf("return-void")),
            expectedPreimageSha256 = "dummy",
        )
        val op = ConcreteOperation.HookInjection(
            id = "op_bad_hook",
            target = PartitionPath("system", "framework/framework.jar"),
            match = spec,
            placement = HookPlacement.BEFORE,
            hookDescriptor = "not_a_valid_hook_descriptor",
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.HOOK_SIGNATURE_MISMATCH, refused.report.errors.first().code)
    }

    @Test
    fun hookInjectionInsertsInvokeStaticBeforeAndAfter() = runBlocking {
        val classRel = "smali/com/android/server/pm/InstallPackageHelper.smali"
        fakePort.members[classRel] = fixtureSmali

        val preimage = BytecodeMatcher.methodPreimage(
            fixtureSmali,
            "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
        )
        assertFalse(preimage.isNullOrEmpty())

        val beforeSpec = BytecodeMatchSpec(
            classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;",
            methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
            anchor = AnchorPattern(
                opcodeSequence = listOf("const-string", "move-result-object"),
                stringConstants = listOf("LTI_CPS"),
            ),
            expectedPreimageSha256 = preimage,
        )

        val beforeOp = ConcreteOperation.HookInjection(
            id = "op_hook_before",
            target = PartitionPath("system", "framework/framework.jar"),
            match = beforeSpec,
            placement = HookPlacement.BEFORE,
            hookDescriptor = "Lcom/example/Hook;->beforeHook()V",
        )

        val beforeOutcome = adapter.apply(listOf(beforeOp), ctx, preview = false)
        val beforeApplied = assertIs<ApkJarOutcome.Applied>(beforeOutcome)
        assertEquals(1, beforeApplied.effects.size)

        val smaliAfterBefore = fakePort.members[classRel]!!
        val beforeLines = smaliAfterBefore.lines()
        val beforeHookIdx = beforeLines.indexOfFirst { it.contains("Lcom/example/Hook;->beforeHook()V") }
        assertTrue(beforeHookIdx != -1)
        assertTrue(beforeLines[beforeHookIdx + 1].contains("const-string"))

        // Now test AFTER placement
        fakePort.members[classRel] = fixtureSmali
        val afterOp = ConcreteOperation.HookInjection(
            id = "op_hook_after",
            target = PartitionPath("system", "framework/framework.jar"),
            match = beforeSpec,
            placement = HookPlacement.AFTER,
            hookDescriptor = "Lcom/example/Hook;->afterHook()V",
        )

        val afterOutcome = adapter.apply(listOf(afterOp), ctx, preview = false)
        val afterApplied = assertIs<ApkJarOutcome.Applied>(afterOutcome)
        assertEquals(1, afterApplied.effects.size)

        val smaliAfterAfter = fakePort.members[classRel]!!
        val afterLines = smaliAfterAfter.lines()
        val afterHookIdx = afterLines.indexOfFirst { it.contains("Lcom/example/Hook;->afterHook()V") }
        assertTrue(afterHookIdx != -1)
        assertTrue(afterLines[afterHookIdx - 1].contains("move-result-object"))
    }

    @Test
    fun failureAmbiguousAnchorRefused() = runBlocking {
        val classRel = "smali/com/android/server/pm/InstallPackageHelper.smali"
        fakePort.members[classRel] = fixtureSmali

        val preimage = BytecodeMatcher.methodPreimage(
            fixtureSmali,
            "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
        )
        assertFalse(preimage.isNullOrEmpty())

        val ambiguousSpec = BytecodeMatchSpec(
            classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;",
            methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
            anchor = AnchorPattern(
                opcodeSequence = listOf("const-string", "move-result-object"),
                stringConstants = emptyList(),
            ),
            expectedPreimageSha256 = preimage,
        )

        val op = ConcreteOperation.HookInjection(
            id = "op_ambiguous_anchor",
            target = PartitionPath("system", "framework/framework.jar"),
            match = ambiguousSpec,
            placement = HookPlacement.BEFORE,
            hookDescriptor = "Lcom/example/Hook;->onHook()V",
        )

        val outcome = adapter.apply(listOf(op), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.ANCHOR_AMBIGUOUS, refused.report.errors.first().code)
        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun refusalInSecondOfThreeOperationsRebuildsNothingAndDiscards() = runBlocking {
        val member = "smali/com/example/Test.smali"
        val content = "line 1\nline 2\nline 3"
        fakePort.members[member] = content
        val sha = sha256Of(content)

        val op1 = ConcreteOperation.ApkJarPatch(
            id = "op_1",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(
                SmaliEdit(
                    memberDescriptor = member,
                    expectedSha256 = sha,
                    unifiedDiff = "@@ -1,3 +1,3 @@\n-line 1\n+line 1 mod\n line 2\n line 3",
                ),
            ),
        )

        // op2 fails with SMALI_MEMBER_MISSING
        val op2 = ConcreteOperation.ApkJarPatch(
            id = "op_2",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(
                SmaliEdit(
                    memberDescriptor = "smali/non_existent.smali",
                    expectedSha256 = "dummy",
                    unifiedDiff = "dummy",
                ),
            ),
        )

        val op3 = ConcreteOperation.ApkJarPatch(
            id = "op_3",
            target = PartitionPath("system", "framework/framework.jar"),
            edits = listOf(
                SmaliEdit(
                    memberDescriptor = member,
                    expectedSha256 = sha,
                    unifiedDiff = "@@ -1,3 +1,3 @@\n line 1\n-line 2\n+line 2 mod\n line 3",
                ),
            ),
        )

        val outcome = adapter.apply(listOf(op1, op2, op3), ctx, preview = false)
        val refused = assertIs<ApkJarOutcome.Refused>(outcome)

        assertEquals(1, refused.report.errors.size)
        assertEquals(ComponentPatchCodes.SMALI_MEMBER_MISSING, refused.report.errors.first().code)

        assertEquals(1, fakePort.decodeCount)
        assertEquals(0, fakePort.rebuildCount)
        assertEquals(1, fakePort.discardCount)
    }

    @Test
    fun receiptsListAddedClassesChangedMethodsAndAotArtifactPaths() = runBlocking {
        val payload = assetRoot.resolve("NewClass.smali")
        Files.writeString(payload, ".class public Lcom/example/NewClass;\n")

        val classRel = "smali/com/android/server/pm/InstallPackageHelper.smali"
        fakePort.members[classRel] = fixtureSmali

        val preimage = BytecodeMatcher.methodPreimage(
            fixtureSmali,
            "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
        )
        assertFalse(preimage.isNullOrEmpty())

        val mergeOp = ConcreteOperation.CompiledClassMerge(
            id = "op_merge",
            target = PartitionPath("system", "framework/framework.jar"),
            payload = AssetRef("NewClass.smali"),
            classDescriptors = listOf("Lcom/example/NewClass;"),
            dependencies = emptyList(),
        )

        val hookSpec = BytecodeMatchSpec(
            classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;",
            methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V",
            anchor = AnchorPattern(
                opcodeSequence = listOf("const-string", "move-result-object"),
                stringConstants = listOf("LTI_CPS"),
            ),
            expectedPreimageSha256 = preimage,
        )

        val hookOp = ConcreteOperation.HookInjection(
            id = "op_hook",
            target = PartitionPath("system", "framework/framework.jar"),
            match = hookSpec,
            placement = HookPlacement.BEFORE,
            hookDescriptor = "Lcom/example/Hook;->beforeHook()V",
        )

        val outcome = adapter.apply(listOf(mergeOp, hookOp), ctx, preview = false)
        val applied = assertIs<ApkJarOutcome.Applied>(outcome)

        assertEquals(1, applied.receipts.size)
        val receipt = applied.receipts.first()

        assertTrue(receipt.decodedOnce)
        assertEquals(listOf("op_merge", "op_hook"), receipt.operationIds)
        assertEquals(listOf("Lcom/example/NewClass;"), receipt.addedClasses)
        val expectedMethod = "Lcom/android/server/pm/InstallPackageHelper;->" +
            "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V"
        assertEquals(
            listOf(expectedMethod),
            receipt.changedMethods,
        )

        val expectedAot = listOf(
            workTree.resolve("system/framework/oat/framework.odex").normalize().toString().replace('\\', '/'),
            workTree.resolve("system/framework/oat/framework.vdex").normalize().toString().replace('\\', '/'),
            workTree.resolve("system/framework/oat/framework.art").normalize().toString().replace('\\', '/'),
        )
        assertEquals(expectedAot, receipt.aotArtifactsInvalidated)
    }

    private fun sha256Of(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path)
            .sorted(Comparator.reverseOrder())
            .forEach(Files::delete)
    }
}

private class FakeComponentPatchPort(
    val members: MutableMap<String, String> = mutableMapOf(),
    var decodeFails: Boolean = false,
    var rebuildFails: Boolean = false,
) : ComponentPatchPort {
    var decodeCount: Int = 0
    var rebuildCount: Int = 0
    var discardCount: Int = 0
    var writeCount: Int = 0

    override suspend fun decode(componentAbsPath: String): DecodedComponent? {
        decodeCount++
        if (decodeFails) return null
        return DecodedComponent(componentPath = componentAbsPath, decodeRoot = "fake-root")
    }

    override suspend fun readMember(decoded: DecodedComponent, relPath: String): String? = members[relPath]

    override suspend fun writeMember(decoded: DecodedComponent, relPath: String, content: String): Boolean {
        writeCount++
        members[relPath] = content
        return true
    }

    override suspend fun listMembers(decoded: DecodedComponent, prefix: String): List<String> =
        members.keys.filter { it.startsWith(prefix) }.toList()

    override suspend fun rebuild(decoded: DecodedComponent, outputAbsPath: String): Boolean {
        rebuildCount++
        return !rebuildFails
    }

    override suspend fun discard(decoded: DecodedComponent) {
        discardCount++
    }
}
