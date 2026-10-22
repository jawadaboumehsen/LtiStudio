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

/**
 * Owner, group, octal mode and SELinux label an added file gets in the image (its `fs_config` and
 * `file_context` entries). Without it, a file is root-owned 0644 and labelled like its folder.
 */
@Serializable
data class FileMetadata(val uid: Int = 0, val gid: Int = 0, val mode: String = "0644", val label: String? = null)

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

/**
 * A change to one member of a decoded APK/JAR. With [method], [memberDescriptor] is a class descriptor
 * (`Lcom/example/Foo;`, found in whichever `smali*` folder holds it) and the hunks apply inside that one method.
 */
@Serializable
data class SmaliEdit(
    val memberDescriptor: String,
    val expectedSha256: String,
    val unifiedDiff: String,
    val method: MethodMatch? = null,
    /** Structured changes to an XML member (a resource), applied instead of [unifiedDiff] when not empty. */
    val xml: List<XmlChange> = emptyList(),
)

/** Elements of an XML document with this [tag] (qualified or local name; any when null) and all these [attributes]. */
@Serializable
data class XmlMatch(val tag: String? = null, val attributes: Map<String, String> = emptyMap())

@Serializable
enum class XmlChangeKind {
    /** Adds [XmlChange.fragment] as the last children of the matched element, or of the root without a match. */
    APPEND,
    INSERT_BEFORE,
    INSERT_AFTER,
    REPLACE,
    REMOVE,
}

/** One change to an XML member; [match] must pick exactly one element (except APPEND into the root). */
@Serializable
data class XmlChange(val kind: XmlChangeKind, val match: XmlMatch? = null, val fragment: String = "")

/**
 * Picks one method of a class by what it is, not where it is: its [name], and when that is not enough (overloads,
 * obfuscated names) its [parameters] (the descriptors between the parentheses, e.g. `ILjava/lang/String;`),
 * [returns] type, [strings] it loads with `const-string`, [references] its instructions use (a method, field or
 * type, or part of one: `Landroid/app/Instrumentation;->newApplication`) and an [opcodes] run (consecutive
 * instructions, `*` for any one). Exactly one method must match.
 */
@Serializable
data class MethodMatch(
    val name: String? = null,
    val parameters: String? = null,
    val returns: String? = null,
    val strings: List<String> = emptyList(),
    val references: List<String> = emptyList(),
    val opcodes: List<String> = emptyList(),
)

/** A device ([device] codename) a mod is made for, and its firmware versions ([firmwares]; empty means any). */
@Serializable
data class Compatibility(val device: String, val firmwares: List<String> = emptyList())

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
        val metadata: FileMetadata? = null,
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
    /** The devices and firmwares this mod is made for; empty means any. */
    val compatibility: List<Compatibility> = emptyList(),
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
