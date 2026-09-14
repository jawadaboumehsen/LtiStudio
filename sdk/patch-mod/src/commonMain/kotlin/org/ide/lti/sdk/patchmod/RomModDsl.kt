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

import org.ide.lti.core.domain.plugin.PluginPlanValidator
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

fun romMod(publisher: String, id: String, version: String, block: RomModScope.() -> Unit): PluginPlanTemplate {
    val scope = RomModScope().apply(block)
    val template = PluginPlanTemplate(
        schemaVersion = 1,
        publisher = publisher,
        id = id,
        version = version,
        settings = scope.buildSettings(),
        operations = scope.buildOperations(),
    )
    val report = PluginPlanValidator.validate(template)
    if (report.hasBlockingErrors()) {
        val message = report.errors.joinToString("\n") {
            "${it.code}@${it.objectId ?: ""}.${it.fieldPath}: ${it.message}"
        }
        throw IllegalArgumentException(message)
    }
    return template
}

class RomModScope {
    private val settingsList = mutableListOf<SettingDefinition>()
    private val operationsList = mutableListOf<Operation>()

    fun settings(block: SettingsScope.() -> Unit) {
        SettingsScope(settingsList).apply(block)
    }

    fun operations(block: OperationsScope.() -> Unit) {
        OperationsScope(operationsList).apply(block)
    }

    internal fun buildSettings(): List<SettingDefinition> = settingsList.toList()

    internal fun buildOperations(): List<Operation> = operationsList.toList()
}

class SettingsScope internal constructor(private val settings: MutableList<SettingDefinition>) {

    fun boolean(
        id: String,
        label: String,
        default: Boolean,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ) {
        settings.add(
            SettingDefinition.BooleanSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
            ),
        )
    }

    fun int(
        id: String,
        label: String,
        default: Int,
        min: Int,
        max: Int,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ) {
        settings.add(
            SettingDefinition.IntSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                min = min,
                max = max,
            ),
        )
    }

    fun text(
        id: String,
        label: String,
        default: String,
        maxLength: Int = 4096,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ) {
        settings.add(
            SettingDefinition.TextSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                maxLength = maxLength,
            ),
        )
    }

    fun enum(
        id: String,
        label: String,
        default: String,
        options: List<String>,
        help: String? = null,
        required: Boolean = false,
        advanced: Boolean = false,
    ) {
        settings.add(
            SettingDefinition.EnumSetting(
                id = id,
                label = label,
                help = help,
                required = required,
                advanced = advanced,
                default = default,
                options = options,
            ),
        )
    }
}

class OperationsScope internal constructor(private val operations: MutableList<Operation>) {

    fun copy(id: String, source: String, partition: String, path: String, condition: Condition = Always) {
        operations.add(
            Operation.Copy(
                id = id,
                source = AssetRef(source),
                destination = PartitionPath(partition, path),
                condition = condition,
            ),
        )
    }

    fun replace(
        id: String,
        source: String,
        partition: String,
        path: String,
        expectedSha256: String,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.Replace(
                id = id,
                source = AssetRef(source),
                destination = PartitionPath(partition, path),
                expectedSha256 = expectedSha256,
                condition = condition,
            ),
        )
    }

    fun delete(id: String, partition: String, path: String, condition: Condition = Always) {
        operations.add(
            Operation.Delete(
                id = id,
                target = PartitionPath(partition, path),
                condition = condition,
            ),
        )
    }

    fun propertyPatch(
        id: String,
        partition: String,
        path: String,
        key: String,
        value: ValueRef,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.PropertyPatch(
                id = id,
                target = PartitionPath(partition, path),
                key = key,
                value = value,
                condition = condition,
            ),
        )
    }

    fun textPatch(
        id: String,
        partition: String,
        path: String,
        context: String,
        replacement: ValueRef,
        expectedSha256: String,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.TextPatch(
                id = id,
                target = PartitionPath(partition, path),
                context = context,
                replacement = replacement,
                expectedSha256 = expectedSha256,
                condition = condition,
            ),
        )
    }

    fun apkJarPatch(
        id: String,
        partition: String,
        path: String,
        condition: Condition = Always,
        block: ApkJarScope.() -> Unit,
    ) {
        val edits = ApkJarScope().apply(block).buildEdits()
        operations.add(
            Operation.ApkJarPatch(
                id = id,
                target = PartitionPath(partition, path),
                edits = edits,
                condition = condition,
            ),
        )
    }

    fun compiledClassMerge(
        id: String,
        partition: String,
        path: String,
        payload: String,
        classDescriptors: List<String>,
        dependencies: List<String> = emptyList(),
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.CompiledClassMerge(
                id = id,
                target = PartitionPath(partition, path),
                payload = AssetRef(payload),
                classDescriptors = classDescriptors,
                dependencies = dependencies,
                condition = condition,
            ),
        )
    }

    fun hookInjection(
        id: String,
        partition: String,
        path: String,
        classDescriptor: String,
        methodDescriptor: String,
        opcodeSequence: List<String>,
        expectedPreimageSha256: String,
        hookDescriptor: String,
        placement: HookPlacement = HookPlacement.BEFORE,
        stringConstants: List<String> = emptyList(),
        invokeSignature: String? = null,
        condition: Condition = Always,
    ) {
        operations.add(
            Operation.HookInjection(
                id = id,
                target = PartitionPath(partition, path),
                match = BytecodeMatchSpec(
                    classDescriptor = classDescriptor,
                    methodDescriptor = methodDescriptor,
                    anchor = AnchorPattern(
                        opcodeSequence = opcodeSequence,
                        stringConstants = stringConstants,
                        invokeSignature = invokeSignature,
                    ),
                    expectedPreimageSha256 = expectedPreimageSha256,
                ),
                placement = placement,
                hookDescriptor = hookDescriptor,
                condition = condition,
            ),
        )
    }
}

class ApkJarScope {
    private val editsList = mutableListOf<SmaliEdit>()

    fun edit(memberDescriptor: String, expectedSha256: String, unifiedDiff: String) {
        editsList.add(
            SmaliEdit(
                memberDescriptor = memberDescriptor,
                expectedSha256 = expectedSha256,
                unifiedDiff = unifiedDiff,
            ),
        )
    }

    internal fun buildEdits(): List<SmaliEdit> = editsList.toList()
}

fun literal(v: String): ValueRef.Literal = ValueRef.Literal(v)

fun setting(id: String): ValueRef.Setting = ValueRef.Setting(id)

fun fact(id: String): ValueRef.TargetFact = ValueRef.TargetFact(id)

infix fun ValueRef.eq(other: ValueRef): Condition.Equals = Condition.Equals(this, other)

fun ValueRef.inOptions(vararg options: ValueRef): Condition.In = Condition.In(this, options.toList())

fun and(vararg conditions: Condition): Condition.And = Condition.And(conditions.toList())

fun or(vararg conditions: Condition): Condition.Or = Condition.Or(conditions.toList())

fun not(condition: Condition): Condition.Not = Condition.Not(condition)

val Always: Condition.Always = Condition.Always
