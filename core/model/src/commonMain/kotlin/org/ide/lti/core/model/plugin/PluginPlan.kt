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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest

@Serializable
data class AssetRef(val path: String)

@Serializable
data class PartitionPath(val partition: String, val relativePath: String)

@Serializable
sealed interface ValueRef {
    @Serializable
    @SerialName("Literal")
    data class Literal(val value: String) : ValueRef

    @Serializable
    @SerialName("Setting")
    data class Setting(val settingId: String) : ValueRef

    @Serializable
    @SerialName("TargetFact")
    data class TargetFact(val factId: String) : ValueRef
}

@Serializable
sealed interface Condition {
    @Serializable
    @SerialName("Always")
    data object Always : Condition

    @Serializable
    @SerialName("Not")
    data class Not(val operand: Condition) : Condition

    @Serializable
    @SerialName("And")
    data class And(val operands: List<Condition>) : Condition

    @Serializable
    @SerialName("Or")
    data class Or(val operands: List<Condition>) : Condition

    @Serializable
    @SerialName("Equals")
    data class Equals(val left: ValueRef, val right: ValueRef) : Condition

    @Serializable
    @SerialName("In")
    data class In(val value: ValueRef, val options: List<ValueRef>) : Condition
}

@Serializable
sealed interface SettingDefinition {
    val id: String
    val label: String
    val help: String?
    val required: Boolean
    val advanced: Boolean

    @Serializable
    @SerialName("BooleanSetting")
    data class BooleanSetting(
        override val id: String,
        override val label: String,
        override val help: String? = null,
        override val required: Boolean = false,
        override val advanced: Boolean = false,
        val default: Boolean,
    ) : SettingDefinition

    @Serializable
    @SerialName("IntSetting")
    data class IntSetting(
        override val id: String,
        override val label: String,
        override val help: String? = null,
        override val required: Boolean = false,
        override val advanced: Boolean = false,
        val default: Int,
        val min: Int,
        val max: Int,
    ) : SettingDefinition

    @Serializable
    @SerialName("TextSetting")
    data class TextSetting(
        override val id: String,
        override val label: String,
        override val help: String? = null,
        override val required: Boolean = false,
        override val advanced: Boolean = false,
        val default: String,
        val maxLength: Int = 4096,
    ) : SettingDefinition

    @Serializable
    @SerialName("EnumSetting")
    data class EnumSetting(
        override val id: String,
        override val label: String,
        override val help: String? = null,
        override val required: Boolean = false,
        override val advanced: Boolean = false,
        val default: String,
        val options: List<String>,
    ) : SettingDefinition
}

@Serializable
data class SmaliEdit(val memberDescriptor: String, val expectedSha256: String, val unifiedDiff: String)

@Serializable
enum class HookPlacement {
    BEFORE,
    AFTER,
}

@Serializable
data class AnchorPattern(
    val opcodeSequence: List<String>,
    val stringConstants: List<String> = emptyList(),
    val invokeSignature: String? = null,
)

@Serializable
data class BytecodeMatchSpec(
    val classDescriptor: String,
    val methodDescriptor: String,
    val anchor: AnchorPattern,
    val expectedPreimageSha256: String,
)

@Serializable
sealed interface Operation {
    val id: String
    val condition: Condition

    @Serializable
    @SerialName("Copy")
    data class Copy(
        override val id: String,
        val source: AssetRef,
        val destination: PartitionPath,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("Replace")
    data class Replace(
        override val id: String,
        val source: AssetRef,
        val destination: PartitionPath,
        val expectedSha256: String,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("Delete")
    data class Delete(
        override val id: String,
        val target: PartitionPath,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("PropertyPatch")
    data class PropertyPatch(
        override val id: String,
        val target: PartitionPath,
        val key: String,
        val value: ValueRef,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("TextPatch")
    data class TextPatch(
        override val id: String,
        val target: PartitionPath,
        val context: String,
        val replacement: ValueRef,
        val expectedSha256: String,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("ApkJarPatch")
    data class ApkJarPatch(
        override val id: String,
        val target: PartitionPath,
        val edits: List<SmaliEdit>,
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("CompiledClassMerge")
    data class CompiledClassMerge(
        override val id: String,
        val target: PartitionPath,
        val payload: AssetRef,
        val classDescriptors: List<String>,
        val dependencies: List<String> = emptyList(),
        override val condition: Condition = Condition.Always,
    ) : Operation

    @Serializable
    @SerialName("HookInjection")
    data class HookInjection(
        override val id: String,
        val target: PartitionPath,
        val match: BytecodeMatchSpec,
        val placement: HookPlacement,
        val hookDescriptor: String,
        override val condition: Condition = Condition.Always,
    ) : Operation
}

@Serializable
data class DependencyRef(val publisher: String, val id: String, val versionRange: String)

@Serializable
data class PayloadEntry(val path: String, val sizeBytes: Long, val sha256: String)

@Serializable
data class PluginManifest(
    val schemaVersion: Int = 1,
    val sdkApiRange: String,
    val publisher: String,
    val id: String,
    val version: String,
    val displayName: String,
    val license: String,
    val compatibleTargets: List<String>,
    val requiredCapabilities: List<String> = emptyList(),
    val dependencies: List<DependencyRef> = emptyList(),
    val conflicts: List<DependencyRef> = emptyList(),
    val payloads: List<PayloadEntry> = emptyList(),
    val planPath: String = "plan.json",
    val settingsPath: String = "settings.schema.json",
)

@Serializable
data class PluginPlanTemplate(
    val schemaVersion: Int = 1,
    val publisher: String,
    val id: String,
    val version: String,
    val settings: List<SettingDefinition>,
    val operations: List<Operation>,
) {
    fun canonicalDigest(): String {
        val jsonString = canonicalJson.encodeToString(serializer(), this)
        val md = MessageDigest.getInstance("SHA-256")
        val hashBytes = md.digest(jsonString.toByteArray(Charsets.UTF_8))
        return "sha256:" + hashBytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        val canonicalJson = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            isLenient = true
            prettyPrint = false
        }
    }
}
