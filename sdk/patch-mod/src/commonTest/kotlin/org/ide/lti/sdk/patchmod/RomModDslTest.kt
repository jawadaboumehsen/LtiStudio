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

import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.PluginPlanResolver
import org.ide.lti.core.domain.plugin.ResolutionInput
import org.ide.lti.core.domain.plugin.ResolutionResult
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RomModDslTest {

    private val validSha = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    @Test
    fun templateBuiltWithDslMatchesHandBuiltTemplate() {
        val handBuilt = PluginPlanTemplate(
            schemaVersion = 1,
            publisher = "org.mifos",
            id = "test-plugin",
            version = "1.0.0",
            settings = listOf(
                SettingDefinition.BooleanSetting(
                    id = "opt_bool",
                    label = "Enable feature",
                    default = true,
                ),
                SettingDefinition.IntSetting(
                    id = "opt_int",
                    label = "Count",
                    default = 5,
                    min = 1,
                    max = 10,
                ),
                SettingDefinition.TextSetting(
                    id = "opt_text",
                    label = "Text",
                    default = "default_text",
                    maxLength = 20,
                ),
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
                    condition = Condition.Always,
                ),
                Operation.Replace(
                    id = "op_replace",
                    source = AssetRef("assets/lib/test.so"),
                    destination = PartitionPath("vendor", "lib64/test.so"),
                    expectedSha256 = validSha,
                    condition = Condition.Always,
                ),
                Operation.Delete(
                    id = "op_delete",
                    target = PartitionPath("product", "app/TestApp"),
                    condition = Condition.Always,
                ),
                Operation.PropertyPatch(
                    id = "op_prop",
                    target = PartitionPath("system_ext", "build.prop"),
                    key = "ro.test",
                    value = ValueRef.Setting("opt_text"),
                    condition = Condition.Always,
                ),
                Operation.TextPatch(
                    id = "op_text",
                    target = PartitionPath("odm", "etc/conf"),
                    context = "ctx",
                    replacement = ValueRef.Literal("val"),
                    expectedSha256 = validSha,
                    condition = Condition.Always,
                ),
                Operation.ApkJarPatch(
                    id = "op_apk",
                    target = PartitionPath("vendor_dlkm", "etc/test.apk"),
                    edits = listOf(SmaliEdit("Ltest/Class;->method()V", validSha, "diff")),
                    condition = Condition.Always,
                ),
                Operation.CompiledClassMerge(
                    id = "op_merge",
                    target = PartitionPath("odm_dlkm", "etc/classes.jar"),
                    payload = AssetRef("assets/dex/patch.dex"),
                    classDescriptors = listOf("Ltest/Helper;"),
                    dependencies = emptyList(),
                    condition = Condition.Always,
                ),
                Operation.HookInjection(
                    id = "op_hook",
                    target = PartitionPath("system_dlkm", "etc/kernel.jar"),
                    match = BytecodeMatchSpec(
                        classDescriptor = "Ltest/Target;",
                        methodDescriptor = "run()V",
                        anchor = AnchorPattern(
                            opcodeSequence = listOf("nop"),
                            stringConstants = emptyList(),
                            invokeSignature = null,
                        ),
                        expectedPreimageSha256 = validSha,
                    ),
                    placement = HookPlacement.AFTER,
                    hookDescriptor = "Ltest/Hook;->onRun()V",
                    condition = Condition.Always,
                ),
            ),
        )

        val dslBuilt = romMod(
            publisher = "org.mifos",
            id = "test-plugin",
            version = "1.0.0",
        ) {
            settings {
                boolean("opt_bool", "Enable feature", default = true)
                int("opt_int", "Count", default = 5, min = 1, max = 10)
                text("opt_text", "Text", default = "default_text", maxLength = 20)
                enum("opt_enum", "Flavor", default = "vanilla", options = listOf("vanilla", "chocolate"))
            }
            operations {
                copy("op_copy", "assets/bin/test", "system", "bin/test")
                replace("op_replace", "assets/lib/test.so", "vendor", "lib64/test.so", expectedSha256 = validSha)
                delete("op_delete", "product", "app/TestApp")
                propertyPatch("op_prop", "system_ext", "build.prop", "ro.test", setting("opt_text"))
                textPatch("op_text", "odm", "etc/conf", "ctx", literal("val"), expectedSha256 = validSha)
                apkJarPatch("op_apk", "vendor_dlkm", "etc/test.apk") {
                    edit("Ltest/Class;->method()V", validSha, "diff")
                }
                compiledClassMerge(
                    "op_merge",
                    "odm_dlkm",
                    "etc/classes.jar",
                    "assets/dex/patch.dex",
                    listOf("Ltest/Helper;"),
                )
                hookInjection(
                    id = "op_hook",
                    partition = "system_dlkm",
                    path = "etc/kernel.jar",
                    classDescriptor = "Ltest/Target;",
                    methodDescriptor = "run()V",
                    opcodeSequence = listOf("nop"),
                    expectedPreimageSha256 = validSha,
                    hookDescriptor = "Ltest/Hook;->onRun()V",
                    placement = HookPlacement.AFTER,
                )
            }
        }

        assertEquals(handBuilt, dslBuilt)
    }

    @Test
    fun duplicateOperationIdThrowsIllegalArgumentExceptionMentioningDuplicateOperationId() {
        val exception = assertFailsWith<IllegalArgumentException> {
            romMod(
                publisher = "org.mifos",
                id = "duplicate-test",
                version = "1.0.0",
            ) {
                operations {
                    copy("op_dup", "assets/bin/test1", "system", "bin/test1")
                    copy("op_dup", "assets/bin/test2", "system", "bin/test2")
                }
            }
        }
        val message = exception.message ?: ""
        assertTrue(
            message.contains("DUPLICATE_OPERATION_ID"),
            "Expected message to contain DUPLICATE_OPERATION_ID, but was: '$message'",
        )
    }

    @Test
    fun resolverResolvesDslBuiltTemplateEndToEnd() {
        val template = romMod(
            publisher = "org.mifos",
            id = "e2e-plugin",
            version = "1.0.0",
        ) {
            settings {
                boolean("opt_bool", "Enable feature", default = true)
                text("opt_val", "Value", default = "val_default")
            }
            operations {
                copy("op_copy", "assets/bin/test", "system", "bin/test")
                propertyPatch(
                    id = "op_prop",
                    partition = "system_ext",
                    path = "build.prop",
                    key = "ro.prop",
                    value = setting("opt_val"),
                    condition = setting("opt_bool") eq literal("true"),
                )
                delete(
                    id = "op_del",
                    partition = "product",
                    path = "app/Unwanted",
                    condition = setting("opt_bool") eq literal("false"),
                )
            }
        }

        val input = ResolutionInput(
            template = template,
            packageContentDigest = "sha256:pkg",
            settingValues = mapOf("opt_val" to "val_custom"),
            upstreamCheckpointDigest = "sha256:upstream",
        )

        val result = PluginPlanResolver.resolve(input)
        assertIs<ResolutionResult.Resolved>(result)
        val plan = result.plan

        assertEquals(2, plan.operations.size)
        assertEquals(1, plan.notApplicable.size)
        assertEquals("op_del", plan.notApplicable[0].operationId)

        val propOp = plan.operations[1]
        assertIs<ConcreteOperation.PropertyPatch>(propOp)
        assertEquals("val_custom", propOp.value)
    }
}
