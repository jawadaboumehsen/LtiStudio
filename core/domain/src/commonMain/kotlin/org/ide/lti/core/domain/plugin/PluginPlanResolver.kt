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
import org.ide.lti.core.model.plugin.Condition
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.ValueRef
import org.ide.lti.core.model.run.StageId
import java.security.MessageDigest

data class ResolutionInput(
    val template: PluginPlanTemplate,
    val packageContentDigest: String,
    val settingValues: Map<String, String> = emptyMap(),
    val targetFacts: Map<String, String> = emptyMap(),
    val toolCapabilities: Set<String> = emptySet(),
    val requiredCapabilities: List<String> = emptyList(),
    val upstreamCheckpointDigest: String,
)

sealed interface ResolutionResult {
    data class Resolved(val plan: ConcreteMutationPlan) : ResolutionResult
    data class Rejected(val report: ValidationReport) : ResolutionResult
}

/**
 * Pure plan resolver converting a [PluginPlanTemplate] into a [ConcreteMutationPlan].
 *
 * Evaluates conditions, binds setting and target fact references, enforces capability requirements,
 * and generates a deterministic plan digest covering all effective inputs.
 */
object PluginPlanResolver {

    const val ILLEGAL_SETTING_VALUE: String = "ILLEGAL_SETTING_VALUE"
    const val MISSING_TARGET_FACT: String = "MISSING_TARGET_FACT"
    const val UNSUPPORTED_CAPABILITY: String = "UNSUPPORTED_CAPABILITY"

    fun resolve(input: ResolutionInput): ResolutionResult {
        val validatorReport = PluginPlanValidator.validate(input.template)
        return if (validatorReport.hasBlockingErrors()) {
            ResolutionResult.Rejected(validatorReport)
        } else {
            Run(input).execute()
        }
    }

    private class Run(private val input: ResolutionInput) {
        private val errors = mutableListOf<ValidationError>()
        private val settingDefs = input.template.settings.associateBy { it.id }
        private val usedSettings = mutableMapOf<String, String>()
        private val readFacts = mutableMapOf<String, String>()

        private fun error(objectId: String, fieldPath: String, code: String, message: String) {
            errors += ValidationError(StageId.MODULE_APPLICATION, objectId, fieldPath, code, Severity.ERROR, message)
        }

        fun execute(): ResolutionResult {
            checkCapabilities()
            checkSettings()
            val (operations, notApplicable) = processOperations()
            return if (errors.isNotEmpty()) {
                ResolutionResult.Rejected(ValidationReport(errors = errors))
            } else {
                val digest = computeDigest(operations)
                ResolutionResult.Resolved(
                    ConcreteMutationPlan(
                        publisher = input.template.publisher,
                        id = input.template.id,
                        version = input.template.version,
                        operations = operations,
                        notApplicable = notApplicable,
                        digest = digest,
                    ),
                )
            }
        }

        private fun checkCapabilities() {
            for (requiredCap in input.requiredCapabilities) {
                if (requiredCap !in input.toolCapabilities) {
                    error(
                        requiredCap,
                        "requiredCapabilities",
                        UNSUPPORTED_CAPABILITY,
                        "Required capability '$requiredCap' is not supported by current toolchain",
                    )
                }
            }
        }

        private fun checkSettings() {
            for (setting in input.template.settings) {
                val rawValue = input.settingValues[setting.id]
                if (rawValue != null && !checkSettingValue(setting, rawValue)) {
                    error(
                        setting.id,
                        "value",
                        ILLEGAL_SETTING_VALUE,
                        "Illegal value '$rawValue' for setting '${setting.id}'",
                    )
                }
            }
        }

        private fun checkSettingValue(setting: SettingDefinition, value: String): Boolean = when (setting) {
            is SettingDefinition.BooleanSetting -> value == "true" || value == "false"
            is SettingDefinition.IntSetting -> {
                val parsed = value.toIntOrNull()
                parsed != null && parsed in setting.min..setting.max
            }
            is SettingDefinition.TextSetting -> value.length <= setting.maxLength
            is SettingDefinition.EnumSetting -> value in setting.options
        }

        private fun defaultString(setting: SettingDefinition): String = when (setting) {
            is SettingDefinition.BooleanSetting -> setting.default.toString()
            is SettingDefinition.IntSetting -> setting.default.toString()
            is SettingDefinition.TextSetting -> setting.default
            is SettingDefinition.EnumSetting -> setting.default
        }

        private fun bindValueRef(ref: ValueRef, operationId: String): String = when (ref) {
            is ValueRef.Literal -> ref.value
            // Undeclared setting ids are rejected by the validator before resolution starts.
            is ValueRef.Setting -> (
                input.settingValues[ref.settingId]
                    ?: defaultString(settingDefs.getValue(ref.settingId))
                )
                .also { usedSettings[ref.settingId] = it }
            is ValueRef.TargetFact -> input.targetFacts[ref.factId]?.also { readFacts[ref.factId] = it } ?: run {
                error(
                    operationId,
                    ref.factId,
                    MISSING_TARGET_FACT,
                    "Missing target fact '${ref.factId}' for operation '$operationId'",
                )
                ""
            }
        }

        private fun evaluateCondition(condition: Condition, operationId: String): Boolean = when (condition) {
            is Condition.Always -> true
            is Condition.Not -> !evaluateCondition(condition.operand, operationId)
            // map() first so every operand is bound and its errors collected, not short-circuited away.
            is Condition.And -> condition.operands.map { evaluateCondition(it, operationId) }.all { it }
            is Condition.Or -> condition.operands.map { evaluateCondition(it, operationId) }.any { it }
            is Condition.Equals -> {
                val left = bindValueRef(condition.left, operationId)
                val right = bindValueRef(condition.right, operationId)
                left == right
            }
            is Condition.In -> {
                val value = bindValueRef(condition.value, operationId)
                val options = condition.options.map { bindValueRef(it, operationId) }
                value in options
            }
        }

        private fun processOperations(): Pair<List<ConcreteOperation>, List<NotApplicable>> {
            val concreteOperations = mutableListOf<ConcreteOperation>()
            val notApplicable = mutableListOf<NotApplicable>()

            for (op in input.template.operations) {
                val satisfied = evaluateCondition(op.condition, op.id)
                if (satisfied) {
                    concreteOperations.add(toConcreteOperation(op))
                } else {
                    notApplicable.add(NotApplicable(op.id, "condition evaluated to false"))
                }
            }
            return Pair(concreteOperations, notApplicable)
        }

        private fun toConcreteOperation(op: Operation): ConcreteOperation = when (op) {
            is Operation.Copy -> ConcreteOperation.Copy(
                id = op.id,
                source = op.source,
                destination = op.destination,
            )
            is Operation.Replace -> ConcreteOperation.Replace(
                id = op.id,
                source = op.source,
                destination = op.destination,
                expectedSha256 = op.expectedSha256,
            )
            is Operation.Delete -> ConcreteOperation.Delete(
                id = op.id,
                target = op.target,
            )
            is Operation.PropertyPatch -> ConcreteOperation.PropertyPatch(
                id = op.id,
                target = op.target,
                key = op.key,
                value = bindValueRef(op.value, op.id),
            )
            is Operation.TextPatch -> ConcreteOperation.TextPatch(
                id = op.id,
                target = op.target,
                context = op.context,
                replacement = bindValueRef(op.replacement, op.id),
                expectedSha256 = op.expectedSha256,
            )
            is Operation.ApkJarPatch -> ConcreteOperation.ApkJarPatch(
                id = op.id,
                target = op.target,
                edits = op.edits,
            )
            is Operation.CompiledClassMerge -> ConcreteOperation.CompiledClassMerge(
                id = op.id,
                target = op.target,
                payload = op.payload,
                classDescriptors = op.classDescriptors,
                dependencies = op.dependencies,
            )
            is Operation.HookInjection -> ConcreteOperation.HookInjection(
                id = op.id,
                target = op.target,
                match = op.match,
                placement = op.placement,
                hookDescriptor = op.hookDescriptor,
            )
        }

        private fun computeDigest(concreteOperations: List<ConcreteOperation>): String {
            val canonicalString = buildString {
                append("template=").append(input.template.canonicalDigest()).append('\n')
                append("packageContent=").append(input.packageContentDigest).append('\n')
                append("upstreamCheckpoint=").append(input.upstreamCheckpointDigest).append('\n')
                append("toolCapabilities=").append(input.toolCapabilities.sorted().joinToString(",")).append('\n')
                append("settings=")
                usedSettings.toSortedMap().forEach { (k, v) ->
                    append(k).append('=').append(v).append(';')
                }
                append('\n')
                append("facts=")
                readFacts.toSortedMap().forEach { (k, v) ->
                    append(k).append('=').append(v).append(';')
                }
                append('\n')
                append("operations=").append(concreteOperations.map { it.id }.joinToString(",")).append('\n')
            }

            val md = MessageDigest.getInstance("SHA-256")
            val hashBytes = md.digest(canonicalString.toByteArray(Charsets.UTF_8))
            return "sha256:" + hashBytes.joinToString("") { "%02x".format(it) }
        }
    }
}
