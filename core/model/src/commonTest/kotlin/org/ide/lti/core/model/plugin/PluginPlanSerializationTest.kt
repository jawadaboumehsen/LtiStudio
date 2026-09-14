/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.plugin

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class PluginPlanSerializationTest {

    private val sampleSha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

    private val fullTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "full-plugin",
        version = "1.0.0",
        settings = listOf(
            SettingDefinition.BooleanSetting(
                id = "bool_opt",
                label = "Enable feature",
                help = "Toggle feature on or off",
                required = true,
                advanced = false,
                default = true,
            ),
            SettingDefinition.IntSetting(
                id = "int_opt",
                label = "Buffer size",
                help = "Size in KiB",
                required = false,
                advanced = true,
                default = 1024,
                min = 512,
                max = 4096,
            ),
            SettingDefinition.TextSetting(
                id = "text_opt",
                label = "Hostname",
                help = "Remote server hostname",
                required = true,
                advanced = false,
                default = "localhost",
                maxLength = 256,
            ),
            SettingDefinition.EnumSetting(
                id = "enum_opt",
                label = "Mode",
                help = "Execution mode",
                required = false,
                advanced = false,
                default = "fast",
                options = listOf("fast", "safe", "extreme"),
            ),
        ),
        operations = listOf(
            Operation.Copy(
                id = "op_copy",
                source = AssetRef("assets/bin/helper"),
                destination = PartitionPath("system", "bin/helper"),
                condition = Condition.Always,
            ),
            Operation.Replace(
                id = "op_replace",
                source = AssetRef("assets/lib/libcustom.so"),
                destination = PartitionPath("vendor", "lib64/libcustom.so"),
                expectedSha256 = sampleSha256,
                condition = Condition.Not(Condition.Always),
            ),
            Operation.Delete(
                id = "op_delete",
                target = PartitionPath("system", "priv-app/UnwantedApp"),
                condition = Condition.And(
                    listOf(
                        Condition.Always,
                        Condition.Not(Condition.Always),
                    ),
                ),
            ),
            Operation.PropertyPatch(
                id = "op_prop",
                target = PartitionPath("system", "build.prop"),
                key = "ro.custom.version",
                value = ValueRef.Literal("1.2.3"),
                condition = Condition.Or(
                    listOf(
                        Condition.Always,
                        Condition.Not(Condition.Always),
                    ),
                ),
            ),
            Operation.TextPatch(
                id = "op_text",
                target = PartitionPath("vendor", "etc/config.xml"),
                context = "<config>",
                replacement = ValueRef.Setting("text_opt"),
                expectedSha256 = sampleSha256,
                condition = Condition.Equals(
                    left = ValueRef.Setting("text_opt"),
                    right = ValueRef.Literal("localhost"),
                ),
            ),
            Operation.ApkJarPatch(
                id = "op_apk",
                target = PartitionPath("system", "framework/services.jar"),
                edits = listOf(
                    SmaliEdit(
                        memberDescriptor = "Lcom/android/server/am/ActivityManagerService;->isUserRunning()Z",
                        expectedSha256 = sampleSha256,
                        unifiedDiff = "--- a/ActivityManagerService.smali\n+++ b/ActivityManagerService.smali",
                    ),
                ),
                condition = Condition.In(
                    value = ValueRef.TargetFact("build.flavor"),
                    options = listOf(
                        ValueRef.Literal("user"),
                        ValueRef.Literal("userdebug"),
                    ),
                ),
            ),
            Operation.CompiledClassMerge(
                id = "op_merge",
                target = PartitionPath("system", "framework/framework.jar"),
                payload = AssetRef("assets/classes/patch.dex"),
                classDescriptors = listOf(
                    "Lcom/android/internal/util/CustomHelper;",
                    "Lcom/android/internal/util/CustomHelper\$Inner;",
                ),
                dependencies = listOf("ext.jar"),
                condition = Condition.Always,
            ),
            Operation.HookInjection(
                id = "op_hook",
                target = PartitionPath("system", "framework/services.jar"),
                match = BytecodeMatchSpec(
                    classDescriptor = "Lcom/android/server/pm/PackageManagerService;",
                    methodDescriptor = "installPackage()V",
                    anchor = AnchorPattern(
                        opcodeSequence = listOf("invoke-virtual", "return-void"),
                        stringConstants = listOf("installing"),
                        invokeSignature = "Lcom/android/server/pm/PackageManagerService;->verify()Z",
                    ),
                    expectedPreimageSha256 = sampleSha256,
                ),
                placement = HookPlacement.BEFORE,
                hookDescriptor = "Lorg/ide/lti/hook/HookHandler;->onInstall()V",
                condition = Condition.Always,
            ),
        ),
    )

    @Test
    fun roundTripsThroughCanonicalJsonWithEveryHierarchyNodeIntact() {
        val json = PluginPlanTemplate.canonicalJson.encodeToString(fullTemplate)
        val decoded = PluginPlanTemplate.canonicalJson.decodeFromString<PluginPlanTemplate>(json)
        assertEquals(fullTemplate, decoded)
    }

    @Test
    fun canonicalDigestIsDeterministicAndSensitiveToChanges() {
        val clone = fullTemplate.copy()
        assertEquals(fullTemplate.canonicalDigest(), clone.canonicalDigest())

        val modOperation = fullTemplate.copy(
            operations = fullTemplate.operations.mapIndexed { idx, op ->
                if (idx == 0) {
                    (op as Operation.Copy).copy(
                        destination = PartitionPath("system", "bin/modified_helper"),
                    )
                } else {
                    op
                }
            },
        )
        assertNotEquals(
            fullTemplate.canonicalDigest(),
            modOperation.canonicalDigest(),
            "Digest must change when an operation changes",
        )

        val modSetting = fullTemplate.copy(
            settings = fullTemplate.settings.mapIndexed { idx, s ->
                if (idx == 0) {
                    (s as SettingDefinition.BooleanSetting).copy(default = false)
                } else {
                    s
                }
            },
        )
        assertNotEquals(
            fullTemplate.canonicalDigest(),
            modSetting.canonicalDigest(),
            "Digest must change when a setting changes",
        )
    }

    @Test
    fun unknownOperationTypeDiscriminatorFailsToDecode() {
        val invalidJson = """
            {
                "schemaVersion": 1,
                "publisher": "org.mifos",
                "id": "unknown-op-plan",
                "version": "1.0.0",
                "settings": [],
                "operations": [
                    {
                        "type": "UnknownFutureOperation",
                        "id": "unknown_1",
                        "condition": {"type": "Always"}
                    }
                ]
            }
        """.trimIndent()

        assertFailsWith<SerializationException> {
            PluginPlanTemplate.canonicalJson.decodeFromString<PluginPlanTemplate>(invalidJson)
        }
    }

    @Test
    fun manifestRoundTrips() {
        val manifest = PluginManifest(
            schemaVersion = 1,
            sdkApiRange = ">=1.0.0 <2.0.0",
            publisher = "org.mifos",
            id = "security-patch",
            version = "1.0.0",
            displayName = "Security Patch Plugin",
            license = "MPL-2.0",
            compatibleTargets = listOf("target-a", "target-b"),
            requiredCapabilities = listOf("root_mount"),
            dependencies = listOf(
                DependencyRef("org.mifos", "base-framework", ">=1.0.0"),
            ),
            conflicts = listOf(
                DependencyRef("org.bad", "legacy-patch", "*"),
            ),
            payloads = listOf(
                PayloadEntry("assets/bin/helper", 1024L, sampleSha256),
            ),
            planPath = "plan.json",
            settingsPath = "settings.schema.json",
        )

        val json = PluginPlanTemplate.canonicalJson.encodeToString(manifest)
        val decoded = PluginPlanTemplate.canonicalJson.decodeFromString<PluginManifest>(json)
        assertEquals(manifest, decoded)
    }
}
