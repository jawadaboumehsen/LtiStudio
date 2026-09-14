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

import org.ide.lti.core.data.patch.BytecodeMatcher.MatchOutcome
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BytecodeMatcherTest {

    private val classDescriptor = "Lcom/android/server/pm/InstallPackageHelper;"
    private val methodDescriptor = "scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V"

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

.method public unrelatedMethod()V
    .locals 1

    return-void
.end method
    """.trimIndent()

    private fun expectedPreimage(): String {
        val preimage = BytecodeMatcher.methodPreimage(fixtureSmali, methodDescriptor)
        assertNotNull(preimage)
        return preimage
    }

    private fun createSpec(
        classDesc: String = classDescriptor,
        methodDesc: String = methodDescriptor,
        anchor: AnchorPattern,
        expectedPreimage: String = expectedPreimage(),
    ): BytecodeMatchSpec = BytecodeMatchSpec(
        classDescriptor = classDesc,
        methodDescriptor = methodDesc,
        anchor = anchor,
        expectedPreimageSha256 = expectedPreimage,
    )

    @Test
    fun case1_uniqueFullMatchByOpcodesAndInvokeSignature() {
        val spec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("invoke-static/range", "const-string", "move-result-object"),
                stringConstants = emptyList(),
                invokeSignature = "Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI(...)V",
            ),
        )

        val outcome = BytecodeMatcher.match(fixtureSmali, spec)
        val matched = assertIs<MatchOutcome.Matched>(outcome)

        assertEquals(12, matched.anchorStartLine)
        assertEquals(17, matched.anchorEndLine)
        assertEquals(1.0, matched.score)

        val lines = fixtureSmali.lines()
        assertTrue(lines[matched.anchorStartLine].trim().startsWith("invoke-static/range"))
        assertTrue(lines[matched.anchorEndLine].trim().startsWith("move-result-object"))
    }

    @Test
    fun case2_sameOpcodesDisambiguatedByStringConstantsOrAmbiguous() {
        val disambiguatedSpec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("const-string", "move-result-object"),
                stringConstants = listOf("LTI_CPS"),
                invokeSignature = null,
            ),
        )

        val matchedOutcome = BytecodeMatcher.match(fixtureSmali, disambiguatedSpec)
        val matched = assertIs<MatchOutcome.Matched>(matchedOutcome)
        assertEquals(15, matched.anchorStartLine)
        assertEquals(17, matched.anchorEndLine)
        assertEquals(1.0, matched.score)

        val ambiguousSpec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("const-string", "move-result-object"),
                stringConstants = emptyList(),
                invokeSignature = null,
            ),
        )

        val ambiguousOutcome = BytecodeMatcher.match(fixtureSmali, ambiguousSpec)
        val ambiguous = assertIs<MatchOutcome.Ambiguous>(ambiguousOutcome)
        assertEquals(listOf(15, 27), ambiguous.candidateStartLines)
    }

    @Test
    fun case3_oneOpcodeWrongReturnsNearMiss() {
        val spec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("invoke-static/range", "const-string", "nop"),
                stringConstants = emptyList(),
                invokeSignature = null,
            ),
        )

        val outcome = BytecodeMatcher.match(fixtureSmali, spec)
        val nearMiss = assertIs<MatchOutcome.NearMiss>(outcome)

        assertEquals(12, nearMiss.bestStartLine)
        assertTrue(nearMiss.score < 1.0)
        assertEquals(2.0 / 3.0, nearMiss.score)
        assertEquals(2, nearMiss.divergingOpcodeIndex)
        assertEquals("nop", nearMiss.expected)
        assertEquals("move-result-object", nearMiss.actual)
    }

    @Test
    fun case4_opcodesRightButInvokeSignatureWrongReturnsNearMiss() {
        val wrongSignature = "Lcom/android/server/pm/ScanPackageUtils;->wrongMethod()V"
        val spec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("invoke-static/range", "const-string", "move-result-object"),
                stringConstants = emptyList(),
                invokeSignature = wrongSignature,
            ),
        )

        val outcome = BytecodeMatcher.match(fixtureSmali, spec)
        val nearMiss = assertIs<MatchOutcome.NearMiss>(outcome)

        assertEquals(12, nearMiss.bestStartLine)
        assertEquals(0.5, nearMiss.score)
        assertEquals(-1, nearMiss.divergingOpcodeIndex)
        assertEquals(wrongSignature, nearMiss.expected)
        assertEquals(
            "Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI(...)V",
            nearMiss.actual,
        )
    }

    @Test
    fun case5_patternLongerThanMethodReturnsNoCandidate() {
        val spec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = List(13) { "nop" },
                stringConstants = emptyList(),
                invokeSignature = null,
            ),
        )

        val outcome = BytecodeMatcher.match(fixtureSmali, spec)
        assertIs<MatchOutcome.NoCandidate>(outcome)
    }

    @Test
    fun case6_wrongMethodOrClassDescriptor() {
        val wrongMethodSpec = createSpec(
            methodDesc = "nonExistentMethod(Lcom/android/server/pm/ParsedPackage;)V",
            anchor = AnchorPattern(
                opcodeSequence = listOf("return-void"),
            ),
        )
        val methodNotFoundOutcome = BytecodeMatcher.match(fixtureSmali, wrongMethodSpec)
        val methodNotFound = assertIs<MatchOutcome.MethodNotFound>(methodNotFoundOutcome)
        assertEquals("nonExistentMethod(Lcom/android/server/pm/ParsedPackage;)V", methodNotFound.methodDescriptor)

        val wrongClassSpec = createSpec(
            classDesc = "Lcom/android/server/pm/WrongHelper;",
            anchor = AnchorPattern(
                opcodeSequence = listOf("return-void"),
            ),
        )
        val classMismatchOutcome = BytecodeMatcher.match(fixtureSmali, wrongClassSpec)
        val classMismatch = assertIs<MatchOutcome.ClassMismatch>(classMismatchOutcome)
        assertEquals("Lcom/android/server/pm/WrongHelper;", classMismatch.expected)
        assertEquals(classDescriptor, classMismatch.actual)
    }

    @Test
    fun case7_preimageMismatchAndNormalizationIgnoresNonInstructions() {
        val wrongHash = "0".repeat(64)
        val spec = createSpec(
            anchor = AnchorPattern(
                opcodeSequence = listOf("return-void"),
            ),
            expectedPreimage = wrongHash,
        )

        val outcome = BytecodeMatcher.match(fixtureSmali, spec)
        val mismatch = assertIs<MatchOutcome.PreimageMismatch>(outcome)
        assertEquals(wrongHash, mismatch.expected)
        assertEquals(expectedPreimage(), mismatch.actual)

        val variantSmali = """
.class public Lcom/android/server/pm/InstallPackageHelper;
.super Ljava/lang/Object;

.method public scanPackageTracedLI(Lcom/android/server/pm/ParsedPackage;)V
    # Differently indented, comments and line directives modified
        .locals 16
        .line 999
        # Random comment here
        :different_label_1
    sget-object v0, Ljava/lang/System;->out:Ljava/io/PrintStream;
        .line 1000
    invoke-static/range {v1 .. v6}, Lcom/android/server/pm/ScanPackageUtils;->collectCertificatesLI(...)V
        :different_label_2
        # Another comment
    const-string v5, "LTI_CPS"
    move-result-object v0
        .line 1001
    check-cast v0, Ljava/lang/String;
    invoke-virtual {v0, v1, v15}, Lcom/android/server/pm/InstallPackageHelper;->maybeClearProfilesForUpgradesLI(...)V
    const-string v5, "OTHER_CONST"
    move-result-object v1
    if-eqz v1, :cond_skip
    new-instance v2, Ljava/lang/StringBuilder;
    invoke-direct {v2}, Ljava/lang/StringBuilder;-><init>()V
    return-void
.end method
        """.trimIndent()

        val preimageOriginal = BytecodeMatcher.methodPreimage(fixtureSmali, methodDescriptor)
        val preimageVariant = BytecodeMatcher.methodPreimage(variantSmali, methodDescriptor)
        assertNotNull(preimageOriginal)
        assertEquals(64, preimageOriginal.length)
        assertTrue(preimageOriginal.all { it in "0123456789abcdef" })
        assertEquals(preimageOriginal, preimageVariant)

        assertNull(BytecodeMatcher.methodPreimage(fixtureSmali, "absentMethod()V"))
    }

    @Test
    fun exhaustiveWhenOverMatchOutcome() {
        val outcomes: List<MatchOutcome> = listOf(
            MatchOutcome.Matched(0, 1, 1.0),
            MatchOutcome.Ambiguous(listOf(0, 2)),
            MatchOutcome.NearMiss(0, 0.5, -1, "exp", "act"),
            MatchOutcome.NoCandidate,
            MatchOutcome.MethodNotFound("m()V"),
            MatchOutcome.ClassMismatch("exp", "act"),
            MatchOutcome.PreimageMismatch("exp", "act"),
        )

        for (outcome in outcomes) {
            val description = when (outcome) {
                is MatchOutcome.Matched -> "Matched"
                is MatchOutcome.Ambiguous -> "Ambiguous"
                is MatchOutcome.NearMiss -> "NearMiss"
                MatchOutcome.NoCandidate -> "NoCandidate"
                is MatchOutcome.MethodNotFound -> "MethodNotFound"
                is MatchOutcome.ClassMismatch -> "ClassMismatch"
                is MatchOutcome.PreimageMismatch -> "PreimageMismatch"
            }
            assertTrue(description.isNotEmpty())
        }
    }
}
