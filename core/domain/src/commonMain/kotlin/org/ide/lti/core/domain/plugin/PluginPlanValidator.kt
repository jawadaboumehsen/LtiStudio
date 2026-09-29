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
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.run.StageId

/**
 * Pure validator for [PluginPlanTemplate] enforcing package policy limits and integrity constraints.
 * Every violation is a blocking error; all findings are collected in one report.
 */
object PluginPlanValidator {

    const val CODE_DUPLICATE_SETTING_ID: String = "DUPLICATE_SETTING_ID"
    const val CODE_DUPLICATE_OPERATION_ID: String = "DUPLICATE_OPERATION_ID"
    const val CODE_EMPTY_ID: String = "EMPTY_ID"
    const val CODE_EMPTY_VERSION: String = "EMPTY_VERSION"
    const val CODE_DEFAULT_OUT_OF_RANGE: String = "DEFAULT_OUT_OF_RANGE"
    const val CODE_MIN_GREATER_THAN_MAX: String = "MIN_GREATER_THAN_MAX"
    const val CODE_DEFAULT_NOT_IN_OPTIONS: String = "DEFAULT_NOT_IN_OPTIONS"
    const val CODE_EMPTY_OPTIONS: String = "EMPTY_OPTIONS"
    const val CODE_DEFAULT_EXCEEDS_MAX_LENGTH: String = "DEFAULT_EXCEEDS_MAX_LENGTH"
    const val CODE_UNDECLARED_SETTING_REFERENCE: String = "UNDECLARED_SETTING_REFERENCE"
    const val CODE_MAX_CONDITION_DEPTH_EXCEEDED: String = "MAX_CONDITION_DEPTH_EXCEEDED"
    const val CODE_MAX_OPERATIONS_EXCEEDED: String = "MAX_OPERATIONS_EXCEEDED"
    const val CODE_INVALID_PARTITION: String = "INVALID_PARTITION"
    const val CODE_INVALID_RELATIVE_PATH: String = "INVALID_RELATIVE_PATH"
    const val CODE_INVALID_ASSET_PATH: String = "INVALID_ASSET_PATH"
    const val CODE_INVALID_SHA256: String = "INVALID_SHA256"
    const val CODE_EMPTY_EDITS: String = "EMPTY_EDITS"
    const val CODE_EMPTY_CLASS_DESCRIPTORS: String = "EMPTY_CLASS_DESCRIPTORS"
    const val CODE_EMPTY_OPCODE_SEQUENCE: String = "EMPTY_OPCODE_SEQUENCE"

    private val SHA256_REGEX = Regex("^[0-9a-f]{64}$")
    private val ALLOWED_PARTITIONS =
        setOf("system", "vendor", "product", "system_ext", "odm", "vendor_dlkm", "odm_dlkm", "system_dlkm")

    fun validate(template: PluginPlanTemplate): ValidationReport = Run(template).report()

    private class Run(private val template: PluginPlanTemplate) {
        private val errors = mutableListOf<ValidationError>()
        private val declaredSettingIds = template.settings.map { it.id }.toSet()

        fun report(): ValidationReport {
            if (template.id.isEmpty()) {
                error(
                    template.id,
                    "id",
                    CODE_EMPTY_ID,
                    "Plugin plan template id must not be empty",
                )
            }
            if (template.version.isEmpty()) {
                error(template.id, "version", CODE_EMPTY_VERSION, "Plugin plan template version must not be empty")
            }
            val seenSettings = mutableSetOf<String>()
            template.settings.forEach { setting(it, seenSettings) }
            if (template.operations.size > PluginPackagePolicy.MAX_OPERATIONS) {
                error(
                    template.id,
                    "operations",
                    CODE_MAX_OPERATIONS_EXCEEDED,
                    "Operations count (${template.operations.size}) exceeds limit of " +
                        "${PluginPackagePolicy.MAX_OPERATIONS}",
                )
            }
            val seenOperations = mutableSetOf<String>()
            template.operations.forEach { operation(it, seenOperations) }
            return ValidationReport(errors = errors)
        }

        private fun error(objectId: String, fieldPath: String, code: String, message: String) {
            errors += ValidationError(StageId.MODULE_APPLICATION, objectId, fieldPath, code, Severity.ERROR, message)
        }

        private fun setting(s: SettingDefinition, seen: MutableSet<String>) {
            if (s.id.isEmpty()) {
                error(s.id, "id", CODE_EMPTY_ID, "Setting id must not be empty")
            } else if (!seen.add(s.id)) {
                error(s.id, "id", CODE_DUPLICATE_SETTING_ID, "Duplicate setting id: '${s.id}'")
            }
            when (s) {
                is SettingDefinition.BooleanSetting -> Unit
                is SettingDefinition.IntSetting -> {
                    if (s.min > s.max) {
                        error(
                            s.id,
                            "min",
                            CODE_MIN_GREATER_THAN_MAX,
                            "IntSetting min (${s.min}) cannot be greater than max (${s.max})",
                        )
                    }
                    if (s.default !in s.min..s.max) {
                        error(
                            s.id,
                            "default",
                            CODE_DEFAULT_OUT_OF_RANGE,
                            "IntSetting default (${s.default}) outside range ${s.min}..${s.max}",
                        )
                    }
                }
                is SettingDefinition.TextSetting -> if (s.default.length > s.maxLength) {
                    error(
                        s.id,
                        "default",
                        CODE_DEFAULT_EXCEEDS_MAX_LENGTH,
                        "TextSetting default length (${s.default.length}) exceeds maxLength (${s.maxLength})",
                    )
                }
                is SettingDefinition.EnumSetting -> when {
                    s.options.isEmpty() -> error(
                        s.id,
                        "options",
                        CODE_EMPTY_OPTIONS,
                        "EnumSetting options must not be empty",
                    )
                    s.default !in s.options ->
                        error(
                            s.id,
                            "default",
                            CODE_DEFAULT_NOT_IN_OPTIONS,
                            "EnumSetting default '${s.default}' not in options ${s.options}",
                        )
                }
            }
        }

        private fun operation(op: Operation, seen: MutableSet<String>) {
            if (op.id.isEmpty()) {
                error(op.id, "id", CODE_EMPTY_ID, "Operation id must not be empty")
            } else if (!seen.add(op.id)) {
                error(op.id, "id", CODE_DUPLICATE_OPERATION_ID, "Duplicate operation id: '${op.id}'")
            }
            condition(op.condition, op.id)
            when (op) {
                is Operation.Copy -> {
                    asset(op.source, op.id)
                    partitionPath(op.destination, op.id)
                }
                is Operation.Replace -> {
                    asset(op.source, op.id)
                    partitionPath(op.destination, op.id)
                    sha256(op.expectedSha256, op.id, "expectedSha256")
                }
                is Operation.Delete -> partitionPath(op.target, op.id)
                is Operation.PropertyPatch -> {
                    partitionPath(op.target, op.id)
                    valueRef(op.value, op.id, "value")
                }
                is Operation.TextPatch -> {
                    partitionPath(op.target, op.id)
                    valueRef(op.replacement, op.id, "replacement")
                    sha256(op.expectedSha256, op.id, "expectedSha256")
                }
                is Operation.ApkJarPatch -> {
                    partitionPath(op.target, op.id)
                    if (op.edits.isEmpty()) {
                        error(
                            op.id,
                            "edits",
                            CODE_EMPTY_EDITS,
                            "ApkJarPatch must contain at least one edit",
                        )
                    }
                    op.edits.forEach { sha256(it.expectedSha256, op.id, "expectedSha256") }
                }
                is Operation.CompiledClassMerge -> {
                    partitionPath(op.target, op.id)
                    asset(op.payload, op.id)
                    if (op.classDescriptors.isEmpty()) {
                        error(
                            op.id,
                            "classDescriptors",
                            CODE_EMPTY_CLASS_DESCRIPTORS,
                            "CompiledClassMerge must contain at least one class descriptor",
                        )
                    }
                }
                is Operation.HookInjection -> {
                    partitionPath(op.target, op.id)
                    sha256(op.match.expectedPreimageSha256, op.id, "expectedPreimageSha256")
                    if (op.match.anchor.opcodeSequence.isEmpty()) {
                        error(
                            op.id,
                            "opcodeSequence",
                            CODE_EMPTY_OPCODE_SEQUENCE,
                            "HookInjection anchor opcodeSequence must not be empty",
                        )
                    }
                }
            }
        }

        private fun condition(c: Condition, operationId: String) {
            val depth = depth(c)
            if (depth > PluginPackagePolicy.MAX_CONDITION_DEPTH) {
                error(
                    operationId,
                    "condition",
                    CODE_MAX_CONDITION_DEPTH_EXCEEDED,
                    "Condition nesting depth $depth exceeds limit of ${PluginPackagePolicy.MAX_CONDITION_DEPTH}",
                )
            }
            valueRefs(c).forEach { valueRef(it, operationId, "condition") }
        }

        private fun depth(c: Condition): Int = when (c) {
            is Condition.Always, is Condition.Equals, is Condition.In -> 1
            is Condition.Not -> 1 + depth(c.operand)
            is Condition.And -> 1 + (c.operands.maxOfOrNull(::depth) ?: 0)
            is Condition.Or -> 1 + (c.operands.maxOfOrNull(::depth) ?: 0)
        }

        private fun valueRefs(c: Condition): List<ValueRef> = when (c) {
            is Condition.Always -> emptyList()
            is Condition.Not -> valueRefs(c.operand)
            is Condition.And -> c.operands.flatMap(::valueRefs)
            is Condition.Or -> c.operands.flatMap(::valueRefs)
            is Condition.Equals -> listOf(c.left, c.right)
            is Condition.In -> listOf(c.value) + c.options
        }

        private fun valueRef(ref: ValueRef, operationId: String, fieldPath: String) {
            when (ref) {
                is ValueRef.Literal, is ValueRef.TargetFact -> Unit
                is ValueRef.Setting -> if (ref.settingId !in declaredSettingIds) {
                    error(
                        operationId,
                        fieldPath,
                        CODE_UNDECLARED_SETTING_REFERENCE,
                        "Setting reference '${ref.settingId}' is not declared in settings",
                    )
                }
            }
        }

        private fun partitionPath(p: PartitionPath, operationId: String) {
            if (p.partition !in ALLOWED_PARTITIONS) {
                error(
                    operationId,
                    "partition",
                    CODE_INVALID_PARTITION,
                    "Partition '${p.partition}' is not in allowed set $ALLOWED_PARTITIONS",
                )
            }
            val rel = p.relativePath
            val absolute = rel.startsWith("/") || rel.startsWith("\\") || (rel.length >= 2 && rel[1] == ':')
            when {
                rel.isEmpty() -> error(
                    operationId,
                    "relativePath",
                    CODE_INVALID_RELATIVE_PATH,
                    "Partition relativePath must not be empty",
                )
                absolute -> error(
                    operationId,
                    "relativePath",
                    CODE_INVALID_RELATIVE_PATH,
                    "Partition relativePath must not be absolute: '$rel'",
                )
            }
            if (".." in
                rel
            ) {
                error(
                    operationId,
                    "relativePath",
                    CODE_INVALID_RELATIVE_PATH,
                    "Partition relativePath must not contain '..': '$rel'",
                )
            }
            if (' ' in
                rel
            ) {
                error(
                    operationId,
                    "relativePath",
                    CODE_INVALID_RELATIVE_PATH,
                    "Partition relativePath must not contain NUL character",
                )
            }
        }

        private fun asset(a: AssetRef, operationId: String) {
            val path = a.path
            if (!path.startsWith(
                    "assets/",
                )
            ) {
                error(
                    operationId,
                    "path",
                    CODE_INVALID_ASSET_PATH,
                    "AssetRef path must start with 'assets/': '$path'",
                )
            }
            if (".." in
                path
            ) {
                error(operationId, "path", CODE_INVALID_ASSET_PATH, "AssetRef path must not contain '..': '$path'")
            }
            if (' ' in
                path
            ) {
                error(operationId, "path", CODE_INVALID_ASSET_PATH, "AssetRef path must not contain NUL character")
            }
        }

        private fun sha256(value: String, operationId: String, fieldPath: String) {
            if (!SHA256_REGEX.matches(value)) {
                error(
                    operationId,
                    fieldPath,
                    CODE_INVALID_SHA256,
                    "Field '$fieldPath' must be 64 lowercase hex characters: '$value'",
                )
            }
        }
    }
}
