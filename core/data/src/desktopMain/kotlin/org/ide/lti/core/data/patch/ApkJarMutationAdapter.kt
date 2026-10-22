/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.patch

import kotlinx.coroutines.yield
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.ApkJarOutcome
import org.ide.lti.core.domain.plugin.ComponentPatchCodes
import org.ide.lti.core.domain.plugin.ComponentPatchPort
import org.ide.lti.core.domain.plugin.ComponentPatchReceipt
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.DecodedComponent
import org.ide.lti.core.domain.plugin.EffectKind
import org.ide.lti.core.domain.plugin.FileMutationCodes
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.domain.plugin.MutationEffect
import org.ide.lti.core.domain.plugin.edit.EditResult
import org.ide.lti.core.domain.plugin.edit.MemberEdits
import org.ide.lti.core.model.plugin.SmaliEdit
import org.ide.lti.core.model.run.StageId
import java.nio.file.Files
import java.nio.file.Path

/**
 * Adapter executing the smali and resource edits of APK and JAR components.
 *
 * Operations targeting the same component archive are grouped and executed under a strict decode-once
 * lifecycle: the component archive is decoded once, its operations are applied in plan order, the component
 * is rebuilt once, and the decode directory is discarded. Any failure during execution immediately aborts
 * rebuilding and discards the decoded tree, leaving the container intact.
 */
@Suppress("LargeClass", "TooManyFunctions", "ReturnCount")
class ApkJarMutationAdapter(private val patchPort: ComponentPatchPort) {

    private fun patchError(objectId: String, fieldPath: String, code: String, message: String) =
        ValidationError(StageId.MODULE_APPLICATION, objectId, fieldPath, code, Severity.ERROR, message)

    suspend fun apply(
        operations: List<ConcreteOperation>,
        ctx: MutationContext,
        preview: Boolean = false,
    ): ApkJarOutcome {
        val resolutionErrors = mutableListOf<ValidationError>()
        val componentOperations = mutableMapOf<Path, MutableList<ConcreteOperation>>()

        for (op in operations) {
            when (op) {
                is ConcreteOperation.ApkJarPatch -> {
                    when (val resolved = PartitionPathResolver.resolve(op.targetPartitionPath(), op.id, ctx)) {
                        is ResolvedTarget.Invalid -> resolutionErrors.add(resolved.error)
                        is ResolvedTarget.Valid -> {
                            if (!Files.exists(resolved.path) || !Files.isRegularFile(resolved.path)) {
                                resolutionErrors.add(
                                    patchError(
                                        op.id,
                                        op.targetPartitionPath().relativePath,
                                        ComponentPatchCodes.COMPONENT_MISSING,
                                        "Target component '${op.targetPartitionPath().relativePath}' " +
                                            "not found at '${resolved.path}'",
                                    ),
                                )
                            } else {
                                componentOperations.getOrPut(resolved.path) { mutableListOf() }.add(op)
                            }
                        }
                    }
                }
                is ConcreteOperation.Copy,
                is ConcreteOperation.Replace,
                is ConcreteOperation.Delete,
                is ConcreteOperation.PropertyPatch,
                is ConcreteOperation.TextPatch,
                -> {
                    resolutionErrors.add(
                        patchError(
                            op.id,
                            op.targetPartitionPath().relativePath,
                            FileMutationCodes.UNSUPPORTED_OPERATION,
                            "Operation '${op.id}' is not an APK/JAR component mutation",
                        ),
                    )
                }
            }
        }

        if (resolutionErrors.isNotEmpty()) {
            return ApkJarOutcome.Refused(ValidationReport(resolutionErrors))
        }

        val allEffects = mutableListOf<MutationEffect>()
        val allReceipts = mutableListOf<ComponentPatchReceipt>()

        for ((componentPath, ops) in componentOperations) {
            yield()
            val outcome = processComponent(componentPath, ops, preview)
            when (outcome) {
                is ComponentProcessResult.Refused -> return ApkJarOutcome.Refused(outcome.report)
                is ComponentProcessResult.Applied -> {
                    allEffects.addAll(outcome.effects)
                    allReceipts.add(outcome.receipt)
                }
            }
        }

        return ApkJarOutcome.Applied(allEffects, allReceipts)
    }

    private suspend fun processComponent(
        componentPath: Path,
        ops: List<ConcreteOperation>,
        preview: Boolean,
    ): ComponentProcessResult {
        val decoded = patchPort.decode(componentPath.toString())
            ?: return ComponentProcessResult.Refused(
                ValidationReport(
                    listOf(
                        patchError(
                            ops.first().id,
                            ops.first().targetPartitionPath().relativePath,
                            ComponentPatchCodes.DECODE_FAILED,
                            "Failed to decode component at '$componentPath'",
                        ),
                    ),
                ),
            )

        val componentErrors = mutableListOf<ValidationError>()
        val changedMethods = mutableListOf<String>()
        val memberBuffer = mutableMapOf<String, String>()
        // Listed once, when a method edit first has to find its class among the dex folders.
        var listed: List<String>? = null
        val members: suspend () -> List<String> = { listed ?: patchPort.listMembers(decoded, "").also { listed = it } }

        try {
            for (op in ops) {
                val opErrors = when (op) {
                    is ConcreteOperation.ApkJarPatch -> applyApkJarPatch(
                        op = op,
                        decoded = decoded,
                        memberBuffer = memberBuffer,
                        changedMethods = changedMethods,
                        preview = preview,
                        members = members,
                    )
                    is ConcreteOperation.Copy,
                    is ConcreteOperation.Replace,
                    is ConcreteOperation.Delete,
                    is ConcreteOperation.PropertyPatch,
                    is ConcreteOperation.TextPatch,
                    -> emptyList()
                }

                if (opErrors.isNotEmpty()) {
                    componentErrors.addAll(opErrors)
                    break
                }
            }

            if (componentErrors.isNotEmpty()) {
                return ComponentProcessResult.Refused(ValidationReport(componentErrors))
            }

            if (!preview) {
                yield()
                val rebuildSuccess = patchPort.rebuild(decoded, componentPath.toString())
                if (!rebuildSuccess) {
                    return ComponentProcessResult.Refused(
                        ValidationReport(
                            listOf(
                                patchError(
                                    ops.first().id,
                                    ops.first().targetPartitionPath().relativePath,
                                    ComponentPatchCodes.REBUILD_FAILED,
                                    "Failed to rebuild component at '$componentPath'",
                                ),
                            ),
                        ),
                    )
                }
            }

            val receipt = ComponentPatchReceipt(
                componentPath = componentPath.toString().replace('\\', '/'),
                decodedOnce = true,
                operationIds = ops.map { it.id },
                changedMethods = changedMethods.distinct(),
                aotArtifactsInvalidated = computeAotArtifacts(componentPath),
            )

            val effects = ops.map { op ->
                MutationEffect(
                    operationId = op.id,
                    partition = op.targetPartitionPath().partition,
                    relativePath = op.targetPartitionPath().relativePath,
                    kind = EffectKind.REPLACED,
                    sidecarUpdates = emptyList(),
                )
            }

            return ComponentProcessResult.Applied(effects, receipt)
        } finally {
            patchPort.discard(decoded)
        }
    }

    private suspend fun applyApkJarPatch(
        op: ConcreteOperation.ApkJarPatch,
        decoded: DecodedComponent,
        memberBuffer: MutableMap<String, String>,
        changedMethods: MutableList<String>,
        preview: Boolean,
        members: suspend () -> List<String>,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (edit in op.edits) {
            val editErrors = applyEdit(op.id, edit, decoded, memberBuffer, changedMethods, preview, members)
            if (editErrors.isNotEmpty()) {
                errors.addAll(editErrors)
                break
            }
        }
        return errors
    }

    /**
     * Applies one edit with the shared engine ([MemberEdits]): finds its member (a method edit's class in whichever
     * `smali*` folder holds it), applies it to the member as earlier edits left it, and writes the result.
     */
    @Suppress("LongParameterList")
    private suspend fun applyEdit(
        opId: String,
        edit: SmaliEdit,
        decoded: DecodedComponent,
        memberBuffer: MutableMap<String, String>,
        changed: MutableList<String>,
        preview: Boolean,
        members: suspend () -> List<String>,
    ): List<ValidationError> {
        fun fail(failed: EditResult.Failed) =
            listOf(patchError(opId, edit.memberDescriptor, failed.code, failed.message))
        val member = if (edit.method == null) {
            edit.memberDescriptor
        } else {
            when (val found = MemberEdits.classMember(edit.memberDescriptor, memberBuffer.keys + members())) {
                is EditResult.Failed -> return fail(found)
                is EditResult.Changed -> found.content
            }
        }
        val existing = memberBuffer[member] ?: patchPort.readMember(decoded, member)
        return when (val result = MemberEdits.apply(existing, edit)) {
            is EditResult.Failed -> fail(result)
            is EditResult.Changed -> {
                memberBuffer[member] = result.content
                if (!preview) patchPort.writeMember(decoded, member, result.content)
                changed += result.changed
                emptyList()
            }
        }
    }

    private sealed interface ComponentProcessResult {
        data class Applied(val effects: List<MutationEffect>, val receipt: ComponentPatchReceipt) :
            ComponentProcessResult
        data class Refused(val report: ValidationReport) : ComponentProcessResult
    }
}

/**
 * Computes the sibling Ahead-Of-Time (AOT) compilation artifacts that must be purged
 * downstream when an APK or JAR component is modified.
 *
 * Android runtime caches pre-compiled native code and dex verification artifacts alongside
 * framework and app components in an "oat/" directory next to the container. Any modification
 * to the bytecode invalidates these cached artifacts.
 *
 * This adapter reports the paths (<name>.odex, <name>.vdex, <name>.art under "oat/") whether or
 * not they currently exist on disk. It does NOT delete them directly; stage-settings.md assigns
 * the purging and cache-invalidation responsibility to the downstream assembly/build stage.
 *
 * @param componentPath The filesystem path to the modified APK/JAR component.
 * @return Sibling AOT artifact paths normalized with forward slashes.
 */
internal fun computeAotArtifacts(componentPath: Path): List<String> {
    val parent = componentPath.parent ?: Path.of(".")
    val oatDir = parent.resolve("oat").normalize()
    val baseName = componentPath.fileName.toString().substringBeforeLast('.')
    return listOf(
        oatDir.resolve("$baseName.odex").normalize().toString().replace('\\', '/'),
        oatDir.resolve("$baseName.vdex").normalize().toString().replace('\\', '/'),
        oatDir.resolve("$baseName.art").normalize().toString().replace('\\', '/'),
    )
}
