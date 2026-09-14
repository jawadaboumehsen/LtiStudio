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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PluginPackageCodecTest {

    private val sampleManifest = PluginManifest(
        schemaVersion = 1,
        sdkApiRange = ">=1.0.0 <2.0.0",
        publisher = "org.mifos",
        id = "test-pkg",
        version = "1.2.3",
        displayName = "Test Package",
        license = "MPL-2.0",
        compatibleTargets = listOf("target-a", "target-b"),
        requiredCapabilities = listOf("cap-root"),
        dependencies = listOf(
            DependencyRef(publisher = "org.mifos", id = "dep-core", versionRange = "^1.0.0"),
        ),
        conflicts = listOf(
            DependencyRef(publisher = "org.other", id = "dep-conflict", versionRange = "*"),
        ),
        payloads = listOf(
            PayloadEntry(
                path = "assets/bin/patch",
                sizeBytes = 1024L,
                sha256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
            ),
        ),
        planPath = "plan.json",
        settingsPath = "settings.schema.json",
    )

    private val sampleTemplate = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = "org.mifos",
        id = "test-pkg",
        version = "1.2.3",
        settings = listOf(
            SettingDefinition.BooleanSetting(
                id = "opt_bool",
                label = "Enable feature",
                help = "Toggles the feature",
                required = true,
                default = true,
            ),
        ),
        operations = listOf(
            Operation.Copy(
                id = "op_copy",
                source = AssetRef("assets/bin/patch"),
                destination = PartitionPath("system", "bin/patch"),
            ),
        ),
    )

    @Test
    fun testManifestRoundTrip() {
        val encoded = PluginPackageCodec.encodeManifest(sampleManifest)
        val decoded = PluginPackageCodec.decodeManifest(encoded)
        assertEquals(sampleManifest, decoded)
    }

    @Test
    fun testPlanRoundTrip() {
        val encoded = PluginPackageCodec.encodePlan(sampleTemplate)
        val decoded = PluginPackageCodec.decodePlan(encoded)
        assertEquals(sampleTemplate, decoded)
    }

    @Test
    fun testSettingsSchemaContainsExpectedKeys() {
        val settings = listOf(
            SettingDefinition.BooleanSetting(
                id = "enable_flag",
                label = "Enable Flag",
                help = "Enables flag behavior",
                required = true,
                default = true,
            ),
            SettingDefinition.IntSetting(
                id = "retries",
                label = "Retry Count",
                help = null,
                required = false,
                default = 3,
                min = 1,
                max = 10,
            ),
            SettingDefinition.TextSetting(
                id = "tag_name",
                label = "Tag Name",
                help = null,
                required = false,
                default = "main",
                maxLength = 64,
            ),
            SettingDefinition.EnumSetting(
                id = "flavor",
                label = "Flavor Choice",
                help = "Flavor selection",
                required = false,
                default = "mint",
                options = listOf("mint", "berry"),
            ),
        )

        val schemaJson = PluginPackageCodec.settingsSchema(settings)
        val jsonRoot = Json.parseToJsonElement(schemaJson).jsonObject

        assertEquals("https://json-schema.org/draft/2020-12/schema", jsonRoot["\$schema"]?.jsonPrimitive?.content)
        assertEquals("object", jsonRoot["type"]?.jsonPrimitive?.content)
        assertEquals(false, jsonRoot["additionalProperties"]?.jsonPrimitive?.boolean)

        val requiredList = jsonRoot["required"]?.jsonArray?.map { it.jsonPrimitive.content }
        assertEquals(listOf("enable_flag"), requiredList)

        val properties = jsonRoot["properties"]?.jsonObject
        assertTrue(properties != null)

        val boolProp = properties["enable_flag"]?.jsonObject
        assertTrue(boolProp != null)
        assertEquals("boolean", boolProp["type"]?.jsonPrimitive?.content)
        assertEquals("Enable Flag", boolProp["title"]?.jsonPrimitive?.content)
        assertEquals("Enables flag behavior", boolProp["description"]?.jsonPrimitive?.content)
        assertEquals(true, boolProp["default"]?.jsonPrimitive?.boolean)

        val intProp = properties["retries"]?.jsonObject
        assertTrue(intProp != null)
        assertEquals("integer", intProp["type"]?.jsonPrimitive?.content)
        assertEquals("Retry Count", intProp["title"]?.jsonPrimitive?.content)
        assertNull(intProp["description"])
        assertEquals(3, intProp["default"]?.jsonPrimitive?.int)
        assertEquals(1, intProp["minimum"]?.jsonPrimitive?.int)
        assertEquals(10, intProp["maximum"]?.jsonPrimitive?.int)

        val textProp = properties["tag_name"]?.jsonObject
        assertTrue(textProp != null)
        assertEquals("string", textProp["type"]?.jsonPrimitive?.content)
        assertEquals("Tag Name", textProp["title"]?.jsonPrimitive?.content)
        assertNull(textProp["description"])
        assertEquals("main", textProp["default"]?.jsonPrimitive?.content)
        assertEquals(64, textProp["maxLength"]?.jsonPrimitive?.int)

        val enumProp = properties["flavor"]?.jsonObject
        assertTrue(enumProp != null)
        assertEquals("string", enumProp["type"]?.jsonPrimitive?.content)
        assertEquals("Flavor Choice", enumProp["title"]?.jsonPrimitive?.content)
        assertEquals("Flavor selection", enumProp["description"]?.jsonPrimitive?.content)
        assertEquals("mint", enumProp["default"]?.jsonPrimitive?.content)
        assertEquals(
            listOf("mint", "berry"),
            enumProp["enum"]?.jsonArray?.map { it.jsonPrimitive.content },
        )
    }

    @Test
    fun testContentIdentityOrderIndependent() {
        val entry1 = PayloadEntry(
            path = "assets/a.txt",
            sizeBytes = 10L,
            sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        )
        val entry2 = PayloadEntry(
            path = "assets/b.txt",
            sizeBytes = 20L,
            sha256 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
        )

        val digest1 = PluginPackageCodec.contentIdentity(listOf(entry1, entry2))
        val digest2 = PluginPackageCodec.contentIdentity(listOf(entry2, entry1))
        assertEquals(digest1, digest2)
        assertTrue(digest1.startsWith("sha256:"))
    }

    @Test
    fun testContentIdentityChangesOnAnyField() {
        val base = PayloadEntry(
            path = "assets/a.txt",
            sizeBytes = 10L,
            sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        )
        val baseDigest = PluginPackageCodec.contentIdentity(listOf(base))

        val pathChanged = base.copy(path = "assets/changed.txt")
        assertNotEquals(baseDigest, PluginPackageCodec.contentIdentity(listOf(pathChanged)))

        val sizeChanged = base.copy(sizeBytes = 99L)
        assertNotEquals(baseDigest, PluginPackageCodec.contentIdentity(listOf(sizeChanged)))

        val shaChanged = base.copy(sha256 = "cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc")
        assertNotEquals(baseDigest, PluginPackageCodec.contentIdentity(listOf(shaChanged)))
    }

    @Test
    fun testValidateMembersValidPackagePasses() {
        val payload = PayloadEntry(
            path = "assets/app.apk",
            sizeBytes = 100L,
            sha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        )
        val manifest = sampleManifest.copy(payloads = listOf(payload))
        val members = listOf(
            PluginPackageCodec.MANIFEST,
            PluginPackageCodec.PLAN,
            PluginPackageCodec.SETTINGS,
            PluginPackageCodec.README,
            PluginPackageCodec.LICENSE,
            PluginPackageCodec.SIGNATURE,
            "assets/app.apk",
        )

        val report = PluginPackageCodec.validateMembers(members, manifest)
        assertFalse(report.hasBlockingErrors())
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun testValidateMembersReportsAllCodesFromCraftedList() {
        val declaredPayload = PayloadEntry(
            path = "assets/missing.bin",
            sizeBytes = 50L,
            sha256 = "1111111111111111111111111111111111111111111111111111111111111111",
        )
        val manifest = sampleManifest.copy(payloads = listOf(declaredPayload))

        val baseMembers = mutableListOf(
            PluginPackageCodec.MANIFEST,
            PluginPackageCodec.PLAN,
            PluginPackageCodec.SETTINGS,
            PluginPackageCodec.README,
            "../evil",
            "assets/dup.bin",
            "assets/DUP.bin",
            "assets/undeclared.bin",
            "unexpected.txt",
        )

        val dummyMembers = (1..10_005).map { "assets/filler_$it.bin" }
        val craftedMembers = baseMembers + dummyMembers

        val report = PluginPackageCodec.validateMembers(craftedMembers, manifest)
        val codes = report.errors.map { it.code }.toSet()

        assertTrue(PluginPackageCodec.MISSING_REQUIRED_MEMBER in codes)
        assertTrue(PluginPackageCodec.INVALID_MEMBER_PATH in codes)
        assertTrue(PluginPackageCodec.DUPLICATE_MEMBER in codes)
        assertTrue(PluginPackageCodec.UNDECLARED_PAYLOAD in codes)
        assertTrue(PluginPackageCodec.MISSING_PAYLOAD in codes)
        assertTrue(PluginPackageCodec.UNEXPECTED_MEMBER in codes)
        assertTrue(PluginPackageCodec.TOO_MANY_MEMBERS in codes)

        for (err in report.errors) {
            assertEquals(StageId.MODULE_APPLICATION, err.stageId)
            assertEquals(Severity.ERROR, err.severity)
        }
    }
}
