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

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.ValueRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PluginPlanResolverTest {

    private val validSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private fun sampleTemplate(operations: List<Operation>? = null): PluginPlanTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "test-plugin",
        version = "1.0.0",
        settings = listOf(
            SettingDefinition.BooleanSetting(id = "opt_bool", label = "Enable feature", default = true),
            SettingDefinition.IntSetting(id = "opt_int", label = "Count", default = 5, min = 1, max = 10),
            SettingDefinition.TextSetting(id = "opt_text", label = "Text", default = "default_text"),
            SettingDefinition.EnumSetting(
                id = "opt_enum",
                label = "Flavor",
                default = "vanilla",
                options = listOf("vanilla", "chocolate"),
            ),
        ),
        operations = operations ?: listOf(
            Operation.Copy(
                id = "op_copy",
                source = AssetRef("assets/bin/test"),
                destination = PartitionPath("system", "bin/test"),
            ),
            Operation.PropertyPatch(
                id = "op_prop_explicit",
                target = PartitionPath("system_ext", "build.prop"),
                key = "ro.custom.text",
                value = ValueRef.Setting("opt_text"),
            ),
            Operation.PropertyPatch(
                id = "op_prop_default",
                target = PartitionPath("system_ext", "build.prop"),
                key = "ro.custom.flavor",
                value = ValueRef.Setting("opt_enum"),
            ),
            Operation.PropertyPatch(
                id = "op_prop_fact",
                target = PartitionPath("system_ext", "build.prop"),
                key = "ro.device.model",
                value = ValueRef.TargetFact("ro.product.model"),
            ),
            Operation.TextPatch(
                id = "op_text",
                target = PartitionPath("odm", "etc/conf"),
                context = "ctx",
                replacement = ValueRef.Literal("literal_val"),
                expectedSha256 = validSha,
            ),
        ),
    )

    @Test
    fun happyPathBindsSettingsAndFacts() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg_digest_1",
            settingValues = mapOf("opt_text" to "custom_value"),
            targetFacts = mapOf("ro.product.model" to "Pixel 8"),
            toolCapabilities = setOf("cap1"),
            requiredCapabilities = listOf("cap1"),
            upstreamCheckpointDigest = "sha256:upstream_1",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Resolved>(result)
        val plan = result.plan

        assertEquals("org.mifos", plan.publisher)
        assertEquals("test-plugin", plan.id)
        assertEquals("1.0.0", plan.version)
        assertEquals(5, plan.operations.size)
        assertTrue(plan.notApplicable.isEmpty())

        val opCopy = plan.operations[0]
        assertIs<ConcreteOperation.Copy>(opCopy)
        assertEquals("op_copy", opCopy.id)

        val opExplicit = plan.operations[1]
        assertIs<ConcreteOperation.PropertyPatch>(opExplicit)
        assertEquals("op_prop_explicit", opExplicit.id)
        assertEquals("custom_value", opExplicit.value)

        val opDefault = plan.operations[2]
        assertIs<ConcreteOperation.PropertyPatch>(opDefault)
        assertEquals("op_prop_default", opDefault.id)
        assertEquals("vanilla", opDefault.value)

        val opFact = plan.operations[3]
        assertIs<ConcreteOperation.PropertyPatch>(opFact)
        assertEquals("op_prop_fact", opFact.id)
        assertEquals("Pixel 8", opFact.value)

        val opText = plan.operations[4]
        assertIs<ConcreteOperation.TextPatch>(opText)
        assertEquals("op_text", opText.id)
        assertEquals("literal_val", opText.replacement)
        assertTrue(plan.digest.startsWith("sha256:"))
    }

    @Test
    fun excludesFalseConditionOperationAsNotApplicablePreservingOrder() {
        val operations = listOf(
            Operation.Copy(
                id = "op1",
                source = AssetRef("assets/bin/test1"),
                destination = PartitionPath("system", "bin/test1"),
                condition = Condition.Always,
            ),
            Operation.Copy(
                id = "op2",
                source = AssetRef("assets/bin/test2"),
                destination = PartitionPath("system", "bin/test2"),
                condition = Condition.Equals(ValueRef.Literal("a"), ValueRef.Literal("b")),
            ),
            Operation.Copy(
                id = "op3",
                source = AssetRef("assets/bin/test3"),
                destination = PartitionPath("system", "bin/test3"),
                condition = Condition.Always,
            ),
        )
        val template = sampleTemplate(operations)
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Resolved>(result)
        val plan = result.plan

        assertEquals(listOf("op1", "op3"), plan.operations.map { it.id })
        assertEquals(1, plan.notApplicable.size)
        assertEquals("op2", plan.notApplicable[0].operationId)
        assertEquals("condition evaluated to false", plan.notApplicable[0].reason)
    }

    @Test
    fun digestDeterministicAndChangesWhenAnyInputChanges() {
        val template = sampleTemplate()
        val baseInput = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg1",
            settingValues = mapOf("opt_text" to "value_a"),
            targetFacts = mapOf("ro.product.model" to "ModelA"),
            toolCapabilities = setOf("cap1"),
            requiredCapabilities = listOf("cap1"),
            upstreamCheckpointDigest = "sha256:upstream1",
        )

        val baseRes = PluginPlanResolver.resolve(baseInput)
        assertIs<ResolutionResult.Resolved>(baseRes)
        val sameRes = PluginPlanResolver.resolve(baseInput)
        assertIs<ResolutionResult.Resolved>(sameRes)
        assertEquals(baseRes.plan.digest, sameRes.plan.digest)

        val diffSetting = PluginPlanResolver.resolve(
            baseInput.copy(settingValues = mapOf("opt_text" to "value_b")),
        )
        assertIs<ResolutionResult.Resolved>(diffSetting)
        assertNotEquals(baseRes.plan.digest, diffSetting.plan.digest)

        val diffFact = PluginPlanResolver.resolve(
            baseInput.copy(targetFacts = mapOf("ro.product.model" to "ModelB")),
        )
        assertIs<ResolutionResult.Resolved>(diffFact)
        assertNotEquals(baseRes.plan.digest, diffFact.plan.digest)

        val diffUpstream = PluginPlanResolver.resolve(
            baseInput.copy(upstreamCheckpointDigest = "sha256:upstream2"),
        )
        assertIs<ResolutionResult.Resolved>(diffUpstream)
        assertNotEquals(baseRes.plan.digest, diffUpstream.plan.digest)

        val diffPackage = PluginPlanResolver.resolve(
            baseInput.copy(packageContentDigest = "sha256:pkg2"),
        )
        assertIs<ResolutionResult.Resolved>(diffPackage)
        assertNotEquals(baseRes.plan.digest, diffPackage.plan.digest)
    }

    @Test
    fun illegalIntOutOfRangeRejectedWithExpectedCodeAndObjectId() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            settingValues = mapOf("opt_int" to "99"),
            targetFacts = mapOf("ro.product.model" to "Pixel 8"),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        assertTrue(
            result.report.errors.any {
                it.code == PluginPlanResolver.ILLEGAL_SETTING_VALUE &&
                    it.objectId == "opt_int" &&
                    it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun illegalEnumRejectedWithExpectedCodeAndObjectId() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            settingValues = mapOf("opt_enum" to "strawberry"),
            targetFacts = mapOf("ro.product.model" to "Pixel 8"),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        assertTrue(
            result.report.errors.any {
                it.code == PluginPlanResolver.ILLEGAL_SETTING_VALUE &&
                    it.objectId == "opt_enum" &&
                    it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun missingFactRejectedWithExpectedCodeAndObjectId() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            settingValues = emptyMap(),
            targetFacts = emptyMap(),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        assertTrue(
            result.report.errors.any {
                it.code == PluginPlanResolver.MISSING_TARGET_FACT &&
                    it.objectId == "op_prop_fact" &&
                    it.fieldPath == "ro.product.model" &&
                    it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun missingCapabilityRejectedWithExpectedCodeAndObjectId() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            toolCapabilities = emptySet(),
            requiredCapabilities = listOf("cap_required"),
            targetFacts = mapOf("ro.product.model" to "Pixel 8"),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        assertTrue(
            result.report.errors.any {
                it.code == PluginPlanResolver.UNSUPPORTED_CAPABILITY &&
                    it.objectId == "cap_required" &&
                    it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun validatorErrorSurfacesUnchangedAsRejected() {
        val invalidTemplate = sampleTemplate().copy(id = "")
        val input = ResolutionInput(
            template = invalidTemplate,
            packageContentDigest = "sha256:pkg",
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        assertTrue(
            result.report.errors.any {
                it.code == PluginPlanValidator.CODE_EMPTY_ID &&
                    it.severity == Severity.ERROR
            },
        )
    }

    @Test
    fun oneRunReportingSeveralProblemsListsAll() {
        val template = sampleTemplate()
        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            settingValues = mapOf(
                "opt_int" to "999",
                "opt_enum" to "mint",
            ),
            targetFacts = emptyMap(),
            toolCapabilities = emptySet(),
            requiredCapabilities = listOf("missing_cap1", "missing_cap2"),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Rejected>(result)
        val errors = result.report.errors

        assertTrue(errors.any { it.code == PluginPlanResolver.UNSUPPORTED_CAPABILITY && it.objectId == "missing_cap1" })
        assertTrue(errors.any { it.code == PluginPlanResolver.UNSUPPORTED_CAPABILITY && it.objectId == "missing_cap2" })
        assertTrue(errors.any { it.code == PluginPlanResolver.ILLEGAL_SETTING_VALUE && it.objectId == "opt_int" })
        assertTrue(errors.any { it.code == PluginPlanResolver.ILLEGAL_SETTING_VALUE && it.objectId == "opt_enum" })
        assertTrue(errors.any { it.code == PluginPlanResolver.MISSING_TARGET_FACT && it.objectId == "op_prop_fact" })
    }
}
