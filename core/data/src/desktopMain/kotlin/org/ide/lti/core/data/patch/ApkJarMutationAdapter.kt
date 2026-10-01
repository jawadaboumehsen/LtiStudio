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

import org.ide.lti.core.data.patch.BytecodeMatcher.MatchOutcome
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
import org.ide.lti.core.model.plugin.HookPlacement
import org.ide.lti.core.model.plugin.SmaliEdit
import org.ide.lti.core.model.run.StageId
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Adapter executing APK and JAR bytecode mutations, hook injections, and compiled class merges.
 *
 * Operations targeting the same component archive are grouped and executed under a strict decode-once
 * lifecycle: the component archive is decoded once, its operations are applied in plan order, the component
 * is rebuilt once, and the decode directory is discarded. Any failure during execution immediately aborts
 * rebuilding and discards the decoded tree, leaving the container intact.
 */
@Suppress("LargeClass", "TooManyFunctions", "ReturnCount")
class ApkJarMutationAdapter(private val patchPort: ComponentPatchPort, private val defaultAssetRoot: String = "") {

    private fun patchError(objectId: String, fieldPath: String, code: String, message: String) =
        ValidationError(StageId.MODULE_APPLICATION, objectId, fieldPath, code, Severity.ERROR, message)

    suspend fun apply(
        operations: List<ConcreteOperation>,
        ctx: MutationContext,
        assetRoot: String = defaultAssetRoot,
        preview: Boolean = false,
    ): ApkJarOutcome {
        val resolutionErrors = mutableListOf<ValidationError>()
        val componentOperations = mutableMapOf<Path, MutableList<ConcreteOperation>>()

        for (op in operations) {
            when (op) {
                is ConcreteOperation.ApkJarPatch,
                is ConcreteOperation.CompiledClassMerge,
                is ConcreteOperation.HookInjection,
                -> {
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
            val outcome = processComponent(componentPath, ops, assetRoot, preview)
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
        assetRoot: String,
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
        val addedClasses = mutableListOf<String>()
        val changedMethods = mutableListOf<String>()
        val memberBuffer = mutableMapOf<String, String>()

        try {
            for (op in ops) {
                val opErrors = when (op) {
                    is ConcreteOperation.ApkJarPatch -> applyApkJarPatch(
                        op = op,
                        decoded = decoded,
                        memberBuffer = memberBuffer,
                        changedMethods = changedMethods,
                        preview = preview,
                    )
                    is ConcreteOperation.CompiledClassMerge -> applyCompiledClassMerge(
                        op = op,
                        decoded = decoded,
                        assetRoot = assetRoot,
                        memberBuffer = memberBuffer,
                        addedClasses = addedClasses,
                        preview = preview,
                    )
                    is ConcreteOperation.HookInjection -> applyHookInjection(
                        op = op,
                        decoded = decoded,
                        memberBuffer = memberBuffer,
                        changedMethods = changedMethods,
                        preview = preview,
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
                addedClasses = addedClasses.distinct(),
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
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (edit in op.edits) {
            val editErrors = applySmaliEdit(op.id, edit, decoded, memberBuffer, changedMethods, preview)
            if (editErrors.isNotEmpty()) {
                errors.addAll(editErrors)
                break
            }
        }
        return errors
    }

    private suspend fun applySmaliEdit(
        opId: String,
        edit: SmaliEdit,
        decoded: DecodedComponent,
        memberBuffer: MutableMap<String, String>,
        changedMethods: MutableList<String>,
        preview: Boolean,
    ): List<ValidationError> {
        val originalContent = memberBuffer[edit.memberDescriptor]
            ?: patchPort.readMember(decoded, edit.memberDescriptor)
            ?: return listOf(
                patchError(
                    opId,
                    edit.memberDescriptor,
                    ComponentPatchCodes.SMALI_MEMBER_MISSING,
                    "Smali member '${edit.memberDescriptor}' not found in decoded component",
                ),
            )

        val actualSha = sha256Of(originalContent)
        if (!actualSha.equals(edit.expectedSha256, ignoreCase = true)) {
            return listOf(
                patchError(
                    opId,
                    edit.memberDescriptor,
                    ComponentPatchCodes.SMALI_PREIMAGE_MISMATCH,
                    "Preimage mismatch for '${edit.memberDescriptor}': " +
                        "expected ${edit.expectedSha256}, actual $actualSha",
                ),
            )
        }

        val hunk = parseSingleHunk(edit.unifiedDiff)
        val normalizedContent = originalContent.replace("\r\n", "\n")
        val count = countOccurrences(normalizedContent, hunk.contextAndRemoved)
        if (count == 0) {
            return listOf(
                patchError(
                    opId,
                    edit.memberDescriptor,
                    ComponentPatchCodes.PATCH_CONTEXT_NOT_FOUND,
                    "Patch context anchor not found in '${edit.memberDescriptor}'",
                ),
            )
        }
        if (count > 1) {
            return listOf(
                patchError(
                    opId,
                    edit.memberDescriptor,
                    ComponentPatchCodes.PATCH_CONTEXT_AMBIGUOUS,
                    "Patch context anchor is ambiguous (found $count times) " + "in '${edit.memberDescriptor}'",
                ),
            )
        }

        val idx = normalizedContent.indexOf(hunk.contextAndRemoved)
        val replacedContent = normalizedContent.substring(0, idx) +
            hunk.contextAndAdded +
            normalizedContent.substring(idx + hunk.contextAndRemoved.length)

        memberBuffer[edit.memberDescriptor] = replacedContent
        if (!preview) {
            patchPort.writeMember(decoded, edit.memberDescriptor, replacedContent)
        }
        changedMethods.add(edit.memberDescriptor)
        return emptyList()
    }

    private suspend fun applyCompiledClassMerge(
        op: ConcreteOperation.CompiledClassMerge,
        decoded: DecodedComponent,
        assetRoot: String,
        memberBuffer: MutableMap<String, String>,
        addedClasses: MutableList<String>,
        preview: Boolean,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val payloadPath = Path.of(assetRoot).resolve(op.payload.path).normalize()
        if (!Files.exists(payloadPath) || !Files.isRegularFile(payloadPath)) {
            errors.add(
                patchError(
                    op.id,
                    op.payload.path,
                    ComponentPatchCodes.PAYLOAD_MISSING,
                    "Payload asset '${op.payload.path}' not found at '$payloadPath'",
                ),
            )
        }

        val allExistingMembers = patchPort.listMembers(decoded, "").toSet()
        errors.addAll(validateClassDescriptors(op.id, op.classDescriptors, decoded, allExistingMembers, addedClasses))
        errors.addAll(
            validateDependencies(
                op.id,
                op.dependencies,
                op.classDescriptors,
                decoded,
                allExistingMembers,
                addedClasses,
            ),
        )

        if (errors.isNotEmpty()) return errors

        val payloadContent = Files.readString(payloadPath, Charsets.UTF_8)
        val primaryDescriptor = op.classDescriptors.first()
        val targetRelPath = "smali_classes2/${descriptorToRelPath(primaryDescriptor)}"

        memberBuffer[targetRelPath] = payloadContent
        if (!preview) {
            patchPort.writeMember(decoded, targetRelPath, payloadContent)
        }
        addedClasses.addAll(op.classDescriptors)
        return emptyList()
    }

    private suspend fun validateClassDescriptors(
        opId: String,
        classDescriptors: List<String>,
        decoded: DecodedComponent,
        allExistingMembers: Set<String>,
        addedClasses: List<String>,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (desc in classDescriptors) {
            if (desc.startsWith("Landroid/") || desc.startsWith("Ljava/")) {
                errors.add(
                    patchError(
                        opId,
                        desc,
                        ComponentPatchCodes.PLATFORM_CLASS_COLLISION,
                        "Class descriptor '$desc' collides with platform package",
                    ),
                )
            }

            val relPath = descriptorToRelPath(desc)
            val classAlreadyExists = desc in addedClasses ||
                allExistingMembers.any { it == relPath || it.endsWith("/$relPath") } ||
                patchPort.readMember(decoded, relPath) != null ||
                patchPort.readMember(decoded, "smali/$relPath") != null

            if (classAlreadyExists) {
                errors.add(
                    patchError(
                        opId,
                        desc,
                        ComponentPatchCodes.DUPLICATE_CLASS,
                        "Class descriptor '$desc' already exists in component",
                    ),
                )
            }
        }
        return errors
    }

    private suspend fun validateDependencies(
        opId: String,
        dependencies: List<String>,
        classDescriptors: List<String>,
        decoded: DecodedComponent,
        allExistingMembers: Set<String>,
        addedClasses: List<String>,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        for (dep in dependencies) {
            val depRelPath = descriptorToRelPath(dep)
            val depResolved = dep in classDescriptors ||
                dep in addedClasses ||
                allExistingMembers.any { it == depRelPath || it.endsWith("/$depRelPath") } ||
                patchPort.readMember(decoded, depRelPath) != null ||
                patchPort.readMember(decoded, "smali/$depRelPath") != null

            if (!depResolved) {
                errors.add(
                    patchError(
                        opId,
                        dep,
                        ComponentPatchCodes.UNRESOLVED_DEPENDENCY,
                        "Dependency '$dep' is unresolved in component",
                    ),
                )
            }
        }
        return errors
    }

    @Suppress("LongMethod", "CyclomaticComplexMethod")
    private suspend fun applyHookInjection(
        op: ConcreteOperation.HookInjection,
        decoded: DecodedComponent,
        memberBuffer: MutableMap<String, String>,
        changedMethods: MutableList<String>,
        preview: Boolean,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        if (!HOOK_DESCRIPTOR_REGEX.matches(op.hookDescriptor)) {
            errors.add(
                patchError(
                    op.id,
                    op.hookDescriptor,
                    ComponentPatchCodes.HOOK_SIGNATURE_MISMATCH,
                    "Hook descriptor '${op.hookDescriptor}' does not match expected format",
                ),
            )
            return errors
        }

        val allExistingMembers = patchPort.listMembers(decoded, "")
        val classRelPath = descriptorToRelPath(op.match.classDescriptor)
        val targetRelPath = memberBuffer.keys.firstOrNull { it == classRelPath || it.endsWith("/$classRelPath") }
            ?: allExistingMembers.firstOrNull { it == classRelPath || it.endsWith("/$classRelPath") }
            ?: resolveFallbackClassMember(decoded, classRelPath)

        if (targetRelPath == null) {
            errors.add(
                patchError(
                    op.id,
                    op.match.classDescriptor,
                    ComponentPatchCodes.SMALI_MEMBER_MISSING,
                    "Class member for '${op.match.classDescriptor}' not found in decoded component",
                ),
            )
            return errors
        }

        val classSmali = memberBuffer[targetRelPath] ?: patchPort.readMember(decoded, targetRelPath)
        if (classSmali == null) {
            errors.add(
                patchError(
                    op.id,
                    op.match.classDescriptor,
                    ComponentPatchCodes.SMALI_MEMBER_MISSING,
                    "Smali content for '${op.match.classDescriptor}' could not be read",
                ),
            )
            return errors
        }

        val matchOutcome = BytecodeMatcher.match(classSmali, op.match)
        when (matchOutcome) {
            is MatchOutcome.Matched -> {
                val lines = classSmali.lines().toMutableList()
                val refIndex = if (op.placement == HookPlacement.BEFORE) {
                    matchOutcome.anchorStartLine
                } else {
                    matchOutcome.anchorEndLine
                }
                val indent = if (refIndex in lines.indices) {
                    lines[refIndex].takeWhile { it.isWhitespace() }
                } else {
                    "    "
                }
                val effectiveIndent = if (indent.isEmpty()) "    " else indent
                val hookLine = "${effectiveIndent}invoke-static {}, ${op.hookDescriptor}"

                val insertIndex = if (op.placement == HookPlacement.BEFORE) {
                    matchOutcome.anchorStartLine
                } else {
                    matchOutcome.anchorEndLine + 1
                }
                lines.add(insertIndex, hookLine)
                val newSmali = lines.joinToString("\n")

                memberBuffer[targetRelPath] = newSmali
                if (!preview) {
                    patchPort.writeMember(decoded, targetRelPath, newSmali)
                }
                changedMethods.add("${op.match.classDescriptor}->${op.match.methodDescriptor}")
            }
            is MatchOutcome.Ambiguous -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.methodDescriptor,
                        ComponentPatchCodes.ANCHOR_AMBIGUOUS,
                        "Anchor is ambiguous in '${op.match.methodDescriptor}' " +
                            "(candidates at: ${matchOutcome.candidateStartLines})",
                    ),
                )
            }
            is MatchOutcome.NearMiss -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.methodDescriptor,
                        ComponentPatchCodes.ANCHOR_NOT_FOUND,
                        "Anchor not found (near-miss score=${matchOutcome.score}, " +
                            "diverging opcode at index ${matchOutcome.divergingOpcodeIndex}: " +
                            "expected '${matchOutcome.expected}', actual '${matchOutcome.actual}')",
                    ),
                )
            }
            MatchOutcome.NoCandidate -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.methodDescriptor,
                        ComponentPatchCodes.ANCHOR_NOT_FOUND,
                        "Anchor pattern candidate not found for method '${op.match.methodDescriptor}'",
                    ),
                )
            }
            is MatchOutcome.MethodNotFound -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.methodDescriptor,
                        ComponentPatchCodes.ANCHOR_NOT_FOUND,
                        "Method '${matchOutcome.methodDescriptor}' not found in class " +
                            "'${op.match.classDescriptor}'",
                    ),
                )
            }
            is MatchOutcome.PreimageMismatch -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.methodDescriptor,
                        ComponentPatchCodes.ANCHOR_PREIMAGE_MISMATCH,
                        "Preimage mismatch for method '${op.match.methodDescriptor}': " +
                            "expected ${matchOutcome.expected}, actual ${matchOutcome.actual}",
                    ),
                )
            }
            is MatchOutcome.ClassMismatch -> {
                errors.add(
                    patchError(
                        op.id,
                        op.match.classDescriptor,
                        ComponentPatchCodes.SMALI_MEMBER_MISSING,
                        "Class mismatch: expected ${matchOutcome.expected}, actual ${matchOutcome.actual}",
                    ),
                )
            }
        }

        return errors
    }

    private suspend fun resolveFallbackClassMember(decoded: DecodedComponent, classRelPath: String): String? = when {
        patchPort.readMember(decoded, "smali/$classRelPath") != null -> "smali/$classRelPath"
        patchPort.readMember(decoded, classRelPath) != null -> classRelPath
        else -> null
    }

    private data class ParsedHunk(val contextAndRemoved: String, val contextAndAdded: String)

    private fun parseSingleHunk(diffText: String): ParsedHunk {
        val lines = diffText.replace("\r\n", "\n").lines()
        val hunkLines = extractHunkLines(lines)
        val removedList = mutableListOf<String>()
        val addedList = mutableListOf<String>()

        for (line in hunkLines) {
            when {
                line.startsWith("-") && !line.startsWith("---") -> removedList.add(line.substring(1))
                line.startsWith("+") && !line.startsWith("+++") -> addedList.add(line.substring(1))
                line.startsWith(" ") -> {
                    val context = line.substring(1)
                    removedList.add(context)
                    addedList.add(context)
                }
                line.isEmpty() -> {
                    removedList.add("")
                    addedList.add("")
                }
            }
        }

        return ParsedHunk(
            contextAndRemoved = removedList.joinToString("\n"),
            contextAndAdded = addedList.joinToString("\n"),
        )
    }

    private fun extractHunkLines(lines: List<String>): List<String> {
        val startIndex = lines.indexOfFirst {
            it.startsWith("@@") || it.startsWith(" ") || it.startsWith("-") || it.startsWith("+")
        }
        if (startIndex == -1) return emptyList()
        val candidateLines = if (lines[startIndex].startsWith("@@")) {
            lines.drop(startIndex + 1)
        } else {
            lines.drop(startIndex)
        }
        return candidateLines.filter { !it.startsWith("---") && !it.startsWith("+++") }
    }

    private fun descriptorToRelPath(descriptor: String): String {
        val trimmed = descriptor.removePrefix("L").removeSuffix(";")
        return "$trimmed.smali"
    }

    private fun sha256Of(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(text.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun countOccurrences(text: String, context: String): Int {
        if (context.isEmpty()) return 0
        var count = 0
        var idx = 0
        while (idx < text.length) {
            val found = text.indexOf(context, idx)
            if (found == -1) break
            count++
            idx = found + context.length
        }
        return count
    }

    private sealed interface ComponentProcessResult {
        data class Applied(val effects: List<MutationEffect>, val receipt: ComponentPatchReceipt) :
            ComponentProcessResult
        data class Refused(val report: ValidationReport) : ComponentProcessResult
    }

    private companion object {
        val HOOK_DESCRIPTOR_REGEX = Regex("""^L[^\s;]+;->[^\s(]+\([^)\s]*\)[^\s]+$""")
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
