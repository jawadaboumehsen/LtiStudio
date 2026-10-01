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
import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES
import org.ide.lti.core.model.plugin.AnchorPattern
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.BytecodeMatchSpec
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.SmaliEdit
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("LargeClass")
class PluginPlanContractTest {

    private val validSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private val baseTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "test-plugin",
        version = "1.0.0",
        settings = listOf(
            SettingDefinition.BooleanSetting(id = "opt_bool", label = "Enable feature", default = true),
            SettingDefinition.IntSetting(id = "opt_int", label = "Count", default = 5, min = 1, max = 10),
            SettingDefinition.TextSetting(id = "opt_text", label = "Name", default = "hello", maxLength = 20),
            SettingDefinition.EnumSetting(
                id = "opt_enum",
                label = "Flavor",
                default = "vanilla",
                options = listOf("vanilla", "chocolate"),
            ),
        ),
        operations = listOf(
            Operation.Copy(
                id = "op_copy",
                source = AssetRef("assets/bin/test"),
                destination = PartitionPath("system", "bin/test"),
            ),
            Operation.Replace(
                id = "op_replace",
                source = AssetRef("assets/lib/test.so"),
                destination = PartitionPath("vendor", "lib64/test.so"),
                expectedSha256 = validSha,
            ),
            Operation.Delete(id = "op_delete", target = PartitionPath("product", "app/TestApp")),
            Operation.PropertyPatch(
                id = "op_prop",
                target = PartitionPath("system_ext", "build.prop"),
                key = "ro.test",
                value = ValueRef.Setting("opt_text"),
            ),
            Operation.TextPatch(
                id = "op_text",
                target = PartitionPath("odm", "etc/conf"),
                context = "ctx",
                replacement = ValueRef.Literal("val"),
                expectedSha256 = validSha,
            ),
            Operation.ApkJarPatch(
                id = "op_apk",
                target = PartitionPath("vendor_dlkm", "etc/test.apk"),
                edits = listOf(SmaliEdit("Ltest/Class;->method()V", validSha, "diff")),
            ),
            Operation.CompiledClassMerge(
                id = "op_merge",
                target = PartitionPath("odm_dlkm", "etc/classes.jar"),
                payload = AssetRef("assets/dex/patch.dex"),
                classDescriptors = listOf("Ltest/Helper;"),
            ),
            Operation.HookInjection(
                id = "op_hook",
                target = PartitionPath("system_dlkm", "etc/kernel.jar"),
                match = BytecodeMatchSpec(
                    classDescriptor = "Ltest/Target;",
                    methodDescriptor = "run()V",
                    anchor = AnchorPattern(listOf("nop")),
                    expectedPreimageSha256 = validSha,
                ),
                placement = HookPlacement.AFTER,
                hookDescriptor = "Ltest/Hook;->onRun()V",
            ),
        ),
    )

    private fun withOp(op: Operation) = baseTemplate.copy(operations = listOf(op))

    @Test
    fun validTemplateYieldsEmptyReport() {
        val report = PluginPlanValidator.validate(baseTemplate)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun duplicateSettingIdsRejected() {
        val invalid = baseTemplate.copy(
            settings = baseTemplate.settings + SettingDefinition.BooleanSetting(
                id = "opt_bool",
                label = "Duplicate bool",
                default = false,
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        assertEquals(1, report.errors.size)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_DUPLICATE_SETTING_ID, err.code)
        assertEquals("opt_bool", err.objectId)
        assertEquals(StageId.MODULE_APPLICATION, err.stageId)
        assertEquals(Severity.ERROR, err.severity)
    }

    @Test
    fun duplicateOperationIdsRejected() {
        val invalid = baseTemplate.copy(
            operations = baseTemplate.operations + Operation.Delete(
                id = "op_copy",
                target = PartitionPath("system", "app/Other"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        assertEquals(1, report.errors.size)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_DUPLICATE_OPERATION_ID, err.code)
        assertEquals("op_copy", err.objectId)
        assertEquals(StageId.MODULE_APPLICATION, err.stageId)
        assertEquals(Severity.ERROR, err.severity)
    }

    @Test
    fun emptyTemplateIdRejected() {
        val report = PluginPlanValidator.validate(baseTemplate.copy(id = ""))
        val err = report.errors.first { it.fieldPath == "id" }
        assertEquals(PluginPlanValidator.CODE_EMPTY_ID, err.code)
        assertEquals("", err.objectId)
    }

    @Test
    fun emptyTemplateVersionRejected() {
        val report = PluginPlanValidator.validate(baseTemplate.copy(version = ""))
        val err = report.errors.first { it.fieldPath == "version" }
        assertEquals(PluginPlanValidator.CODE_EMPTY_VERSION, err.code)
        assertEquals(baseTemplate.id, err.objectId)
    }

    @Test
    fun emptySettingIdRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(SettingDefinition.BooleanSetting(id = "", label = "Empty ID", default = false)),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first { it.code == PluginPlanValidator.CODE_EMPTY_ID }
        assertEquals("", err.objectId)
        assertEquals("id", err.fieldPath)
    }

    @Test
    fun emptyOperationIdRejected() {
        val invalid = withOp(Operation.Delete(id = "", target = PartitionPath("system", "app/Test")))
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first { it.code == PluginPlanValidator.CODE_EMPTY_ID }
        assertEquals("", err.objectId)
        assertEquals("id", err.fieldPath)
    }

    @Test
    fun intSettingDefaultOutsideRangeRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(SettingDefinition.IntSetting("bad_int", "Count", default = 20, min = 1, max = 10)),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_DEFAULT_OUT_OF_RANGE, err.code)
        assertEquals("bad_int", err.objectId)
        assertEquals("default", err.fieldPath)
    }

    @Test
    fun intSettingMinGreaterThanMaxRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(SettingDefinition.IntSetting("bad_range", "Count", default = 5, min = 10, max = 1)),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first { it.code == PluginPlanValidator.CODE_MIN_GREATER_THAN_MAX }
        assertEquals("bad_range", err.objectId)
        assertEquals("min", err.fieldPath)
    }

    @Test
    fun enumSettingDefaultNotInOptionsRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(
                SettingDefinition.EnumSetting("bad_enum", "Flavor", default = "strawberry", options = listOf("a", "b")),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_DEFAULT_NOT_IN_OPTIONS, err.code)
        assertEquals("bad_enum", err.objectId)
        assertEquals("default", err.fieldPath)
    }

    @Test
    fun enumSettingEmptyOptionsRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(
                SettingDefinition.EnumSetting("empty_enum", "Flavor", default = "a", options = emptyList()),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_EMPTY_OPTIONS, err.code)
        assertEquals("empty_enum", err.objectId)
        assertEquals("options", err.fieldPath)
    }

    @Test
    fun textSettingDefaultLongerThanMaxLengthRejected() {
        val invalid = baseTemplate.copy(
            settings = listOf(
                SettingDefinition.TextSetting("bad_text", "Name", default = "too long string", maxLength = 5),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_DEFAULT_EXCEEDS_MAX_LENGTH, err.code)
        assertEquals("bad_text", err.objectId)
        assertEquals("default", err.fieldPath)
    }

    @Test
    fun valueRefSettingInOperationReferencingUndeclaredSettingRejected() {
        val invalid = withOp(
            Operation.PropertyPatch(
                id = "op_prop_bad",
                target = PartitionPath("system", "build.prop"),
                key = "prop",
                value = ValueRef.Setting("non_existent_setting"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_UNDECLARED_SETTING_REFERENCE, err.code)
        assertEquals("op_prop_bad", err.objectId)
    }

    @Test
    fun valueRefSettingInConditionReferencingUndeclaredSettingRejected() {
        val invalid = withOp(
            Operation.Delete(
                id = "op_del_bad_cond",
                target = PartitionPath("system", "app/App"),
                condition = Condition.Equals(
                    left = ValueRef.Setting("ghost_setting"),
                    right = ValueRef.Literal("val"),
                ),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_UNDECLARED_SETTING_REFERENCE, err.code)
        assertEquals("op_del_bad_cond", err.objectId)
    }

    @Test
    fun conditionNestingDepth16AcceptedAnd17Rejected() {
        var cond16: Condition = Condition.Always
        repeat(15) { cond16 = Condition.Not(cond16) }
        val op16 = Operation.Delete(
            id = "op_depth_16",
            target = PartitionPath("system", "app/App"),
            condition = cond16,
        )
        val report16 = PluginPlanValidator.validate(withOp(op16))
        assertTrue(report16.errors.none { it.code == PluginPlanValidator.CODE_MAX_CONDITION_DEPTH_EXCEEDED })

        val cond17 = Condition.Not(cond16)
        val op17 = Operation.Delete(
            id = "op_depth_17",
            target = PartitionPath("system", "app/App"),
            condition = cond17,
        )
        val report17 = PluginPlanValidator.validate(withOp(op17))
        val err = report17.errors.first { it.code == PluginPlanValidator.CODE_MAX_CONDITION_DEPTH_EXCEEDED }
        assertEquals("op_depth_17", err.objectId)
        assertEquals("condition", err.fieldPath)
    }

    @Test
    fun operationsCount10000AcceptedAnd10001Rejected() {
        val op10000 = (1..PluginPackagePolicy.MAX_OPERATIONS).map { idx ->
            Operation.Delete(id = "op_$idx", target = PartitionPath("system", "app/App$idx"))
        }
        val report10000 = PluginPlanValidator.validate(baseTemplate.copy(operations = op10000))
        assertFalse(report10000.errors.any { it.code == PluginPlanValidator.CODE_MAX_OPERATIONS_EXCEEDED })

        val op10001 = (1..PluginPackagePolicy.MAX_OPERATIONS + 1).map { idx ->
            Operation.Delete(id = "op_$idx", target = PartitionPath("system", "app/App$idx"))
        }
        val report10001 = PluginPlanValidator.validate(baseTemplate.copy(operations = op10001))
        val err = report10001.errors.first { it.code == PluginPlanValidator.CODE_MAX_OPERATIONS_EXCEEDED }
        assertEquals(baseTemplate.id, err.objectId)
        assertEquals("operations", err.fieldPath)
    }

    @Test
    fun partitionPathInvalidPartitionRejected() {
        val invalid = withOp(Operation.Delete(id = "op_bad_part", target = PartitionPath("boot", "kernel")))
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_PARTITION, err.code)
        assertEquals("op_bad_part", err.objectId)
        assertEquals("partition", err.fieldPath)
    }

    @Test
    fun partitionPathAbsoluteRelativePathRejected() {
        val invalid = withOp(Operation.Delete(id = "op_abs_path", target = PartitionPath("system", "/etc/hosts")))
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_RELATIVE_PATH, err.code)
        assertEquals("op_abs_path", err.objectId)
        assertEquals("relativePath", err.fieldPath)
    }

    @Test
    fun partitionPathEmptyRelativePathRejected() {
        val invalid = withOp(Operation.Delete(id = "op_empty_path", target = PartitionPath("system", "")))
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_RELATIVE_PATH, err.code)
        assertEquals("op_empty_path", err.objectId)
        assertEquals("relativePath", err.fieldPath)
    }

    @Test
    fun partitionPathRelativePathContainingTraversalRejected() {
        val invalid = withOp(
            Operation.Delete(
                id = "op_traverse_path",
                target = PartitionPath("system", "etc/../hosts"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_RELATIVE_PATH, err.code)
        assertEquals("op_traverse_path", err.objectId)
        assertEquals("relativePath", err.fieldPath)
    }

    @Test
    fun partitionPathRelativePathContainingNulRejected() {
        val invalid = withOp(
            Operation.Delete(
                id = "op_nul_path",
                target = PartitionPath("system", "etc/\u0000hosts"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_RELATIVE_PATH, err.code)
        assertEquals("op_nul_path", err.objectId)
        assertEquals("relativePath", err.fieldPath)
    }

    @Test
    fun assetRefPathNotStartingWithAssetsRejected() {
        val invalid = withOp(
            Operation.Copy(
                id = "op_bad_asset_prefix",
                source = AssetRef("payload/bin/helper"),
                destination = PartitionPath("system", "bin/helper"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_ASSET_PATH, err.code)
        assertEquals("op_bad_asset_prefix", err.objectId)
        assertEquals("path", err.fieldPath)
    }

    @Test
    fun assetRefPathContainingTraversalRejected() {
        val invalid = withOp(
            Operation.Copy(
                id = "op_bad_asset_traversal",
                source = AssetRef("assets/../etc/passwd"),
                destination = PartitionPath("system", "bin/helper"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_ASSET_PATH, err.code)
        assertEquals("op_bad_asset_traversal", err.objectId)
        assertEquals("path", err.fieldPath)
    }

    @Test
    fun assetRefPathContainingNulRejected() {
        val invalid = withOp(
            Operation.Copy(
                id = "op_bad_asset_nul",
                source = AssetRef("assets/app\u0000.apk"),
                destination = PartitionPath("system", "bin/helper"),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_ASSET_PATH, err.code)
        assertEquals("op_bad_asset_nul", err.objectId)
        assertEquals("path", err.fieldPath)
    }

    @Test
    fun replaceExpectedSha256Not64LowercaseHexRejected() {
        val invalid = withOp(
            Operation.Replace(
                id = "op_bad_replace_sha",
                source = AssetRef("assets/lib/test.so"),
                destination = PartitionPath("vendor", "lib64/test.so"),
                expectedSha256 = "0123456789ABCDEF0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_SHA256, err.code)
        assertEquals("op_bad_replace_sha", err.objectId)
        assertEquals("expectedSha256", err.fieldPath)
    }

    @Test
    fun textPatchExpectedSha256Not64LowercaseHexRejected() {
        val invalid = withOp(
            Operation.TextPatch(
                id = "op_bad_text_sha",
                target = PartitionPath("vendor", "etc/conf"),
                context = "ctx",
                replacement = ValueRef.Literal("val"),
                expectedSha256 = "short-sha",
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_SHA256, err.code)
        assertEquals("op_bad_text_sha", err.objectId)
        assertEquals("expectedSha256", err.fieldPath)
    }

    @Test
    fun smaliEditExpectedSha256Not64LowercaseHexRejected() {
        val invalid = withOp(
            Operation.ApkJarPatch(
                id = "op_bad_smali_sha",
                target = PartitionPath("system", "framework/test.jar"),
                edits = listOf(SmaliEdit("Ltest/Class;->method()V", "not-a-valid-sha", "diff")),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_SHA256, err.code)
        assertEquals("op_bad_smali_sha", err.objectId)
        assertEquals("expectedSha256", err.fieldPath)
    }

    @Test
    fun bytecodeMatchSpecExpectedPreimageSha256Not64LowercaseHexRejected() {
        val invalid = withOp(
            Operation.HookInjection(
                id = "op_bad_hook_sha",
                target = PartitionPath("system", "framework/test.jar"),
                match = BytecodeMatchSpec(
                    classDescriptor = "Ltest/Target;",
                    methodDescriptor = "run()V",
                    anchor = AnchorPattern(listOf("nop")),
                    expectedPreimageSha256 = "12345",
                ),
                placement = HookPlacement.BEFORE,
                hookDescriptor = "Ltest/Hook;->run()V",
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_INVALID_SHA256, err.code)
        assertEquals("op_bad_hook_sha", err.objectId)
        assertEquals("expectedPreimageSha256", err.fieldPath)
    }

    @Test
    fun apkJarPatchZeroEditsRejected() {
        val invalid = withOp(
            Operation.ApkJarPatch(
                id = "op_empty_edits",
                target = PartitionPath("system", "framework/test.jar"),
                edits = emptyList(),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_EMPTY_EDITS, err.code)
        assertEquals("op_empty_edits", err.objectId)
        assertEquals("edits", err.fieldPath)
    }

    @Test
    fun compiledClassMergeZeroClassDescriptorsRejected() {
        val invalid = withOp(
            Operation.CompiledClassMerge(
                id = "op_empty_classes",
                target = PartitionPath("system", "framework/test.jar"),
                payload = AssetRef("assets/classes.dex"),
                classDescriptors = emptyList(),
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_EMPTY_CLASS_DESCRIPTORS, err.code)
        assertEquals("op_empty_classes", err.objectId)
        assertEquals("classDescriptors", err.fieldPath)
    }

    @Test
    fun hookInjectionEmptyOpcodeSequenceRejected() {
        val invalid = withOp(
            Operation.HookInjection(
                id = "op_empty_opcodes",
                target = PartitionPath("system", "framework/test.jar"),
                match = BytecodeMatchSpec(
                    classDescriptor = "Ltest/Target;",
                    methodDescriptor = "run()V",
                    anchor = AnchorPattern(emptyList()),
                    expectedPreimageSha256 = validSha,
                ),
                placement = HookPlacement.BEFORE,
                hookDescriptor = "Ltest/Hook;->run()V",
            ),
        )
        val report = PluginPlanValidator.validate(invalid)
        val err = report.errors.first()
        assertEquals(PluginPlanValidator.CODE_EMPTY_OPCODE_SEQUENCE, err.code)
        assertEquals("op_empty_opcodes", err.objectId)
        assertEquals("opcodeSequence", err.fieldPath)
    }

    @Test
    fun multipleFindingsReturnedInSingleReport() {
        val invalid = baseTemplate.copy(
            id = "",
            version = "",
            settings = listOf(SettingDefinition.IntSetting("bad_int", "Count", default = 20, min = 1, max = 10)),
            operations = listOf(Operation.Delete(id = "op_bad_part", target = PartitionPath("invalid_partition", "t"))),
        )
        val report = PluginPlanValidator.validate(invalid)
        assertTrue(report.errors.size >= 4, "Report must contain all findings without early exit")
        assertTrue(report.hasBlockingErrors())
    }

    @Test
    fun everyPluginPackagePolicyConstantEqualsDocumentedValue() {
        assertEquals(1_048_576L, PluginPackagePolicy.MANIFEST_MAX_BYTES)
        assertEquals(1_048_576L, PluginPackagePolicy.PLAN_MAX_BYTES)
        assertEquals(1_048_576L, PluginPackagePolicy.SETTINGS_MAX_BYTES)
        assertEquals(262_144L, PluginPackagePolicy.README_MAX_BYTES)
        assertEquals(10_000, PluginPackagePolicy.MAX_OPERATIONS)
        assertEquals(10_000, PluginPackagePolicy.MAX_PACKAGE_MEMBERS)
        assertEquals(16, PluginPackagePolicy.MAX_CONDITION_DEPTH)
        assertEquals(4_294_967_296L, PluginPackagePolicy.PACKAGE_MAX_COMPRESSED_BYTES)
        assertEquals(8_589_934_592L, PluginPackagePolicy.PACKAGE_MAX_EXPANDED_BYTES)
        assertEquals(1, PluginPackagePolicy.POLICY_VERSION)
        assertEquals(MAX_TRANSFER_CHUNK_BYTES, PluginPackagePolicy.TRANSFER_CHUNK_BYTES)
    }
}
