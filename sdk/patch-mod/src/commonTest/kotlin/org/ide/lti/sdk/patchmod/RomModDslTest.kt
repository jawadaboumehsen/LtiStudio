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
import org.ide.lti.core.domain.plugin.PluginPlanValidator
import org.ide.lti.core.domain.plugin.ResolutionInput
import org.ide.lti.core.domain.plugin.ResolutionResult
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Compatibility
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.MethodMatch
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
                    edits = listOf(SmaliEdit("smali/test/A.smali", validSha, "@@ @@\n-x\n+y")),
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
                copy("op_copy", "assets/bin/test", Partition.SYSTEM, "bin/test")
                replace(
                    "op_replace",
                    "assets/lib/test.so",
                    Partition.VENDOR,
                    "lib64/test.so",
                    expectedSha256 = validSha,
                )
                delete("op_delete", Partition.PRODUCT, "app/TestApp")
                propertyPatch("op_prop", Partition.SYSTEM_EXT, "build.prop", "ro.test", setting("opt_text"))
                textPatch("op_text", Partition.ODM, "etc/conf", "ctx", literal("val"), expectedSha256 = validSha)
                apkJarPatch("op_apk", Partition.VENDOR_DLKM, "etc/test.apk") {
                    change("smali/test/A.smali", preimage = validSha) { replace("x", "y") }
                }
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
                    copy("op_dup", "assets/bin/test1", Partition.SYSTEM, "bin/test1")
                    copy("op_dup", "assets/bin/test2", Partition.SYSTEM, "bin/test2")
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
            val enabled = settings.boolean("opt_bool", "Enable feature", default = true)
            val value = settings.text("opt_val", "Value", default = "val_default")
            operations {
                copy("op_copy", "assets/bin/test", Partition.SYSTEM, "bin/test")
                propertyPatch(
                    id = "op_prop",
                    partition = Partition.SYSTEM_EXT,
                    path = "build.prop",
                    key = "ro.prop",
                    value = value.ref,
                    condition = enabled.isOn,
                )
                delete(
                    id = "op_del",
                    partition = Partition.PRODUCT,
                    path = "app/Unwanted",
                    condition = enabled eq false,
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

    @Test
    fun textChangesBecomeHunksPlacedByTheirText() {
        val template = romMod("p", "m", "1.0.0") {
            operations {
                apkJarPatch("op", Partition.SYSTEM, "system/framework/services.jar") {
                    change("smali/A.smali") {
                        replace("    const/4 v0, 0x0", "    const/4 v0, 0x1")
                        insertAfter(".method public run()V\n    .locals 1\n", "    nop")
                        insertBefore("</resources>", "    <s/>\n    <t/>")
                    }
                    addFile("smali/B.smali", ".class public LB;\n")
                }
            }
        }
        val edits = (template.operations.single() as org.ide.lti.core.model.plugin.Operation.ApkJarPatch).edits
        assertEquals(listOf("smali/A.smali", "smali/B.smali"), edits.map { it.memberDescriptor })
        assertEquals("", edits[0].expectedSha256, "no preimage: placed by text alone")
        assertEquals(
            listOf(
                "@@ @@", "-    const/4 v0, 0x0", "+    const/4 v0, 0x1",
                // A trailing newline is an empty last line of the anchor.
                "@@ @@", " .method public run()V", "     .locals 1", " ", "+    nop",
                "@@ @@", "+    <s/>", "+    <t/>", " </resources>",
            ).joinToString("\n"),
            edits[0].unifiedDiff,
        )
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", edits[1].expectedSha256)
        assertEquals("@@ -0,0 +1 @@\n+.class public LB;", edits[1].unifiedDiff)
    }

    @Test
    fun aMethodEditNamesItsClassAndPicksTheMethodBySignatureOrFingerprint() {
        val template = romMod("p", "m", "1.0.0") {
            operations {
                apkJarPatch("op", Partition.SYSTEM, "system/framework/services.jar") {
                    method("Lcom/a/B;", "run(ILjava/lang/String;)V") { insertAfter("    .locals 1", "    nop") }
                    method("Lcom/a/B;") {
                        returns("Z")
                        strings("getFocusedRootTaskInfo()")
                        references("Landroid/os/Binder;->getCallingUid")
                        opcodes("invoke-static", "move-result")
                        replace("    const/4 v0, 0x0", "    const/4 v0, 0x1")
                    }
                }
            }
        }
        val edits = (template.operations.single() as Operation.ApkJarPatch).edits
        assertEquals(listOf("Lcom/a/B;", "Lcom/a/B;"), edits.map { it.memberDescriptor })
        assertEquals(MethodMatch(name = "run", parameters = "ILjava/lang/String;", returns = "V"), edits[0].method)
        val byFingerprint = MethodMatch(
            returns = "Z",
            strings = listOf("getFocusedRootTaskInfo()"),
            references = listOf("Landroid/os/Binder;->getCallingUid"),
            opcodes = listOf("invoke-static", "move-result"),
        )
        assertEquals(byFingerprint, edits[1].method)
        assertEquals("@@ @@\n     .locals 1\n+    nop", edits[0].unifiedDiff)
        assertEquals("", edits[0].expectedSha256, "a method edit is anchored by its method and lines, not a file hash")
    }

    @Test
    fun compatibleDevicesAndFirmwaresGoIntoThePlan() {
        val template = romMod("p", "m", "1.0.0") {
            compatibleWith("PQ84P01", "REDMAGICOS10.5.15_NP05J_GB")
            compatibleWith("NX769J")
        }
        assertEquals(
            listOf(Compatibility("PQ84P01", listOf("REDMAGICOS10.5.15_NP05J_GB")), Compatibility("NX769J")),
            template.compatibility,
        )
    }

    @Test
    fun theSdkOffersExactlyThePartitionsAPlanMayTarget() {
        assertEquals(PluginPlanValidator.ALLOWED_PARTITIONS, Partition.entries.map { it.id }.toSet())
    }
}
