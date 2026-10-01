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

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.plugin.PayloadEntry
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.run.StageId
import java.security.MessageDigest

/**
 * Pure codec and structural member validator for .lti-mod.zip packages.
 */
object PluginPackageCodec {
    const val MANIFEST: String = "manifest.json"
    const val PLAN: String = "plan.json"
    const val SETTINGS: String = "settings.schema.json"
    const val README: String = "README.md"
    const val LICENSE: String = "LICENSE"
    const val SIGNATURE: String = "signature.json"
    const val ASSETS_DIR: String = "assets/"
    const val EXTENSION: String = ".lti-mod.zip"

    const val MISSING_REQUIRED_MEMBER: String = "MISSING_REQUIRED_MEMBER"
    const val INVALID_MEMBER_PATH: String = "INVALID_MEMBER_PATH"
    const val DUPLICATE_MEMBER: String = "DUPLICATE_MEMBER"
    const val UNDECLARED_PAYLOAD: String = "UNDECLARED_PAYLOAD"
    const val MISSING_PAYLOAD: String = "MISSING_PAYLOAD"
    const val UNEXPECTED_MEMBER: String = "UNEXPECTED_MEMBER"
    const val TOO_MANY_MEMBERS: String = "TOO_MANY_MEMBERS"

    private val REQUIRED_MEMBERS = setOf(MANIFEST, PLAN, SETTINGS, README, LICENSE)

    fun encodeManifest(m: PluginManifest): String =
        PluginPlanTemplate.canonicalJson.encodeToString(PluginManifest.serializer(), m)

    fun decodeManifest(text: String): PluginManifest =
        PluginPlanTemplate.canonicalJson.decodeFromString(PluginManifest.serializer(), text)

    fun encodePlan(p: PluginPlanTemplate): String =
        PluginPlanTemplate.canonicalJson.encodeToString(PluginPlanTemplate.serializer(), p)

    fun decodePlan(text: String): PluginPlanTemplate =
        PluginPlanTemplate.canonicalJson.decodeFromString(PluginPlanTemplate.serializer(), text)

    fun settingsSchema(settings: List<SettingDefinition>): String {
        val properties = mutableMapOf<String, JsonElement>()
        val required = mutableListOf<JsonPrimitive>()

        for (setting in settings) {
            if (setting.required) {
                required.add(JsonPrimitive(setting.id))
            }
            properties[setting.id] = buildPropertyObject(setting)
        }

        val schemaObject = JsonObject(
            mapOf(
                "\$schema" to JsonPrimitive("https://json-schema.org/draft/2020-12/schema"),
                "type" to JsonPrimitive("object"),
                "properties" to JsonObject(properties),
                "required" to JsonArray(required),
                "additionalProperties" to JsonPrimitive(false),
            ),
        )
        return PluginPlanTemplate.canonicalJson.encodeToString(JsonObject.serializer(), schemaObject)
    }

    private fun buildPropertyObject(setting: SettingDefinition): JsonObject {
        val propertyMap = mutableMapOf<String, JsonElement>()
        when (setting) {
            is SettingDefinition.BooleanSetting -> {
                propertyMap["type"] = JsonPrimitive("boolean")
                propertyMap["title"] = JsonPrimitive(setting.label)
                if (setting.help != null) {
                    propertyMap["description"] = JsonPrimitive(setting.help)
                }
                propertyMap["default"] = JsonPrimitive(setting.default)
            }
            is SettingDefinition.IntSetting -> {
                propertyMap["type"] = JsonPrimitive("integer")
                propertyMap["title"] = JsonPrimitive(setting.label)
                if (setting.help != null) {
                    propertyMap["description"] = JsonPrimitive(setting.help)
                }
                propertyMap["default"] = JsonPrimitive(setting.default)
                propertyMap["minimum"] = JsonPrimitive(setting.min)
                propertyMap["maximum"] = JsonPrimitive(setting.max)
            }
            is SettingDefinition.TextSetting -> {
                propertyMap["type"] = JsonPrimitive("string")
                propertyMap["title"] = JsonPrimitive(setting.label)
                if (setting.help != null) {
                    propertyMap["description"] = JsonPrimitive(setting.help)
                }
                propertyMap["default"] = JsonPrimitive(setting.default)
                propertyMap["maxLength"] = JsonPrimitive(setting.maxLength)
            }
            is SettingDefinition.EnumSetting -> {
                propertyMap["type"] = JsonPrimitive("string")
                propertyMap["title"] = JsonPrimitive(setting.label)
                if (setting.help != null) {
                    propertyMap["description"] = JsonPrimitive(setting.help)
                }
                propertyMap["default"] = JsonPrimitive(setting.default)
                propertyMap["enum"] = JsonArray(setting.options.map { JsonPrimitive(it) })
            }
        }
        return JsonObject(propertyMap)
    }

    fun contentIdentity(entries: List<PayloadEntry>): String {
        val md = MessageDigest.getInstance("SHA-256")
        val sorted = entries.sortedBy { it.path }
        for (entry in sorted) {
            val line = "${entry.path}\t${entry.sizeBytes}\t${entry.sha256}\n"
            md.update(line.toByteArray(Charsets.UTF_8))
        }
        val hash = md.digest().joinToString("") { "%02x".format(it) }
        return "sha256:$hash"
    }

    fun validateMembers(memberPaths: List<String>, manifest: PluginManifest): ValidationReport {
        val errors = mutableListOf<ValidationError>()
        val manifestId = manifest.id
        val memberSet = memberPaths.toSet()

        checkMemberLimits(memberPaths, manifestId, errors)
        checkRequiredMembers(memberSet, manifestId, errors)
        checkDuplicates(memberPaths, manifestId, errors)
        checkMemberPathValidity(memberPaths, manifestId, errors)
        checkUnexpectedMembers(memberPaths, manifestId, errors)
        checkPayloadMembers(memberPaths, memberSet, manifest, errors)

        return ValidationReport(errors = errors)
    }

    private fun checkMemberLimits(memberPaths: List<String>, manifestId: String, errors: MutableList<ValidationError>) {
        if (memberPaths.size > PluginPackagePolicy.MAX_PACKAGE_MEMBERS) {
            errors += ValidationError(
                stageId = StageId.MODULE_APPLICATION,
                objectId = manifestId,
                fieldPath = "memberPaths",
                code = TOO_MANY_MEMBERS,
                severity = Severity.ERROR,
                message = "Package member count (${memberPaths.size}) exceeds limit of " +
                    "${PluginPackagePolicy.MAX_PACKAGE_MEMBERS}",
            )
        }
    }

    private fun checkRequiredMembers(memberSet: Set<String>, manifestId: String, errors: MutableList<ValidationError>) {
        for (required in REQUIRED_MEMBERS) {
            if (required !in memberSet) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = required,
                    code = MISSING_REQUIRED_MEMBER,
                    severity = Severity.ERROR,
                    message = "Missing required package member: '$required'",
                )
            }
        }
    }

    private fun checkDuplicates(memberPaths: List<String>, manifestId: String, errors: MutableList<ValidationError>) {
        val seenLower = mutableSetOf<String>()
        for (path in memberPaths) {
            val lower = path.lowercase()
            if (!seenLower.add(lower)) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = path,
                    code = DUPLICATE_MEMBER,
                    severity = Severity.ERROR,
                    message = "Duplicate package member: '$path'",
                )
            }
        }
    }

    private fun checkMemberPathValidity(
        memberPaths: List<String>,
        manifestId: String,
        errors: MutableList<ValidationError>,
    ) {
        for (path in memberPaths) {
            if (isPathInvalid(path)) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = path,
                    code = INVALID_MEMBER_PATH,
                    severity = Severity.ERROR,
                    message = "Invalid package member path: '$path'",
                )
            }
        }
    }

    private fun isPathInvalid(path: String): Boolean = hasInvalidPrefixOrChars(path) || hasInvalidSegments(path)

    private fun hasInvalidPrefixOrChars(path: String): Boolean {
        val isAbsolute = path.startsWith("/") || path.startsWith("\\") || (path.length >= 2 && path[1] == ':')
        val hasBadChars = ".." in path || '\u0000' in path || '\\' in path
        return isAbsolute || hasBadChars
    }

    private fun hasInvalidSegments(path: String): Boolean {
        if (path.endsWith("/") || path.isEmpty()) {
            return true
        }
        return path.split('/').any { it.isEmpty() }
    }

    private fun checkUnexpectedMembers(
        memberPaths: List<String>,
        manifestId: String,
        errors: MutableList<ValidationError>,
    ) {
        val allowedTopLevel = REQUIRED_MEMBERS + SIGNATURE
        for (path in memberPaths) {
            val isUnderAssets = path.startsWith(ASSETS_DIR) && path.length > ASSETS_DIR.length
            if (path !in allowedTopLevel && !isUnderAssets) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = path,
                    code = UNEXPECTED_MEMBER,
                    severity = Severity.ERROR,
                    message = "Unexpected package member: '$path'",
                )
            }
        }
    }

    private fun checkPayloadMembers(
        memberPaths: List<String>,
        memberSet: Set<String>,
        manifest: PluginManifest,
        errors: MutableList<ValidationError>,
    ) {
        val declaredPayloadPaths = manifest.payloads.map { it.path }.toSet()
        val manifestId = manifest.id

        for (path in memberPaths) {
            if (path.startsWith(ASSETS_DIR) && path !in declaredPayloadPaths) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = path,
                    code = UNDECLARED_PAYLOAD,
                    severity = Severity.ERROR,
                    message = "Member under assets/ is not listed in manifest payloads: '$path'",
                )
            }
        }

        for (payload in manifest.payloads) {
            if (payload.path !in memberSet) {
                errors += ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = manifestId,
                    fieldPath = payload.path,
                    code = MISSING_PAYLOAD,
                    severity = Severity.ERROR,
                    message = "Declared payload has no corresponding package member: '${payload.path}'",
                )
            }
        }
    }
}
