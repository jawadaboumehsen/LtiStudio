/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.PartitionPath
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AnchorHealthPreflightTest {

    private fun spec(classDescriptor: String, methodDescriptor: String) = BytecodeMatchSpec(
        classDescriptor = classDescriptor,
        methodDescriptor = methodDescriptor,
        anchor = AnchorPattern(opcodeSequence = listOf("invoke-virtual", "return-void")),
        expectedPreimageSha256 = "sha256:deadbeef",
    )

    private fun hookOp(id: String, classDescriptor: String, methodDescriptor: String) = ConcreteOperation.HookInjection(
        id = id,
        target = PartitionPath(partition = "system", relativePath = "framework/framework.jar"),
        match = spec(classDescriptor, methodDescriptor),
        placement = HookPlacement.AFTER,
        hookDescriptor = "Lorg/ide/lti/Hook;->onEvent()V",
    )

    @Test
    fun checksForExtractsOnlyHookInjectionOperations() {
        val plan = ConcreteMutationPlan(
            publisher = "org.ide.lti",
            id = "sample-mod",
            version = "1.0.0",
            operations = listOf(
                hookOp("hook-1", "Lcom/example/Target;", "onCreate()V"),
                ConcreteOperation.Delete(id = "del-1", target = PartitionPath("system", "app/Bloat.apk")),
                ConcreteOperation.PropertyPatch(
                    id = "prop-1",
                    target = PartitionPath("system", "build.prop"),
                    key = "ro.debuggable",
                    value = "0",
                ),
            ),
            notApplicable = emptyList(),
            digest = "sha256:plan",
        )

        val checks = AnchorHealthPreflight.checksFor(plan)

        assertEquals(1, checks.size)
        assertEquals("hook-1", checks.first().operationId)
        assertEquals("Lcom/example/Target;", checks.first().classDescriptor)
    }

    @Test
    fun preflightReturnsPerPackageFindingsKeyedByPublisherAndId() = runTest {
        val findings = mapOf(
            "hook-1" to AnchorFinding.Matched(anchorStartLine = 4, anchorEndLine = 6, score = 1.0),
            "hook-2" to AnchorFinding.NearMiss(
                bestStartLine = 10,
                score = 0.82,
                divergingOpcodeIndex = 3,
                expected = "invoke-virtual",
                actual = "invoke-static",
            ),
            "hook-3" to AnchorFinding.Ambiguous(candidateStartLines = listOf(12, 40)),
            "hook-4" to AnchorFinding.ComponentUnavailable(reason = "component not present in pinned target"),
        )
        val port = object : AnchorHealthPort {
            override suspend fun checkAnchor(
                target: PartitionPath,
                classDescriptor: String,
                match: BytecodeMatchSpec,
            ): AnchorFinding {
                val id = match.methodDescriptor
                return findings.getValue(id)
            }
        }
        val preflight = AnchorHealthPreflight(port)

        val packages = listOf(
            PackageAnchors(
                publisher = "org.ide.lti",
                id = "sample-mod",
                version = "1.0.0",
                checks = listOf(
                    AnchorCheck("hook-1", PartitionPath("system", "a.jar"), "La;", spec("La;", "hook-1")),
                    AnchorCheck("hook-2", PartitionPath("system", "a.jar"), "La;", spec("La;", "hook-2")),
                ),
            ),
            PackageAnchors(
                publisher = "org.ide.lti",
                id = "other-mod",
                version = "2.0.0",
                checks = listOf(
                    AnchorCheck("hook-3", PartitionPath("system", "b.jar"), "Lb;", spec("Lb;", "hook-3")),
                    AnchorCheck("hook-4", PartitionPath("system", "b.jar"), "Lb;", spec("Lb;", "hook-4")),
                ),
            ),
        )

        val result = preflight.preflight(packages)

        assertEquals(setOf("org.ide.lti:sample-mod", "org.ide.lti:other-mod"), result.keys)

        val sampleModFindings = result.getValue("org.ide.lti:sample-mod")
        assertIs<AnchorFinding.Matched>(sampleModFindings.first { it.operationId == "hook-1" }.finding)
        val nearMiss = sampleModFindings.first { it.operationId == "hook-2" }.finding
        assertIs<AnchorFinding.NearMiss>(nearMiss)
        assertEquals(3, nearMiss.divergingOpcodeIndex)

        val otherModFindings = result.getValue("org.ide.lti:other-mod")
        val ambiguous = otherModFindings.first { it.operationId == "hook-3" }.finding
        assertIs<AnchorFinding.Ambiguous>(ambiguous)
        assertTrue(ambiguous.candidateStartLines.containsAll(listOf(12, 40)))
        assertIs<AnchorFinding.ComponentUnavailable>(otherModFindings.first { it.operationId == "hook-4" }.finding)
    }
}
