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

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.ConcreteMutationPlan
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.EffectKind
import org.ide.lti.core.domain.plugin.FileMutationCodes
import org.ide.lti.core.domain.plugin.FileMutationPort
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.domain.plugin.MutationEffect
import org.ide.lti.core.domain.plugin.MutationOutcome
import org.ide.lti.core.domain.plugin.PluginPackagePolicy
import org.ide.lti.core.model.run.StageId
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Filesystem mutation adapter executing concrete mutation operations on a staged ROM work tree.
 *
 * Pure file I/O operations are performed using [java.nio.file]. All file streaming and hashing operations
 * operate in 8 MiB chunks ([PluginPackagePolicy.TRANSFER_CHUNK_BYTES]), avoiding whole-file allocations in memory.
 * Property and text patches are rewritten atomically using temporary files and atomic moves.
 *
 * Sidecar files (fs_config and file_context) are not edited directly by this adapter; downstream debloat
 * and work tree assembly stages own sidecar synchronization. This adapter only reports their paths in
 * [MutationEffect.sidecarUpdates] so callers can act.
 */
@Suppress("LargeClass", "TooManyFunctions")
class FileMutationAdapter : FileMutationPort {

    override suspend fun preview(plan: ConcreteMutationPlan, ctx: MutationContext, assetRoot: String): MutationOutcome =
        when (val checked = check(plan, ctx, assetRoot)) {
            is CheckedPlan.Refused -> MutationOutcome.Refused(checked.report)
            is CheckedPlan.Ready -> MutationOutcome.Applied(
                checked.operations.map { computeEffect(it.operation, it.path, it.partitionRoot) },
            )
        }

    /**
     * Applies in plan order, re-checking each operation immediately before its write. A failure part way
     * through leaves the earlier writes in place: callers run this against a private staging copy of a
     * checkpoint and discard that copy on refusal - nothing mutates an upstream tree.
     */
    override suspend fun apply(plan: ConcreteMutationPlan, ctx: MutationContext, assetRoot: String): MutationOutcome {
        val ready = when (val checked = check(plan, ctx, assetRoot)) {
            is CheckedPlan.Refused -> return MutationOutcome.Refused(checked.report)
            is CheckedPlan.Ready -> checked.operations
        }
        val effects = mutableListOf<MutationEffect>()
        val recheckFailure = ready.firstNotNullOfOrNull { op ->
            val recheck = validateOperation(op.operation, ctx, assetRoot)
            if (recheck.isNotEmpty()) {
                recheck
            } else {
                effects += computeEffect(op.operation, op.path, op.partitionRoot)
                executeOperation(op.operation, op.path, assetRoot)
                null
            }
        }
        return recheckFailure
            ?.let { MutationOutcome.Refused(ValidationReport(errors = it)) }
            ?: MutationOutcome.Applied(effects)
    }

    private data class PlannedOperation(val operation: ConcreteOperation, val path: Path, val partitionRoot: Path)

    private sealed interface CheckedPlan {
        data class Ready(val operations: List<PlannedOperation>) : CheckedPlan
        data class Refused(val report: ValidationReport) : CheckedPlan
    }

    /** Resolves and validates every operation once; two operations writing one path is a plan-level refusal. */
    private fun check(plan: ConcreteMutationPlan, ctx: MutationContext, assetRoot: String): CheckedPlan {
        val errors = mutableListOf<ValidationError>()
        val planned = mutableListOf<PlannedOperation>()
        val seenPaths = mutableMapOf<Path, String>()

        for (op in plan.operations) {
            errors += validateOperation(op, ctx, assetRoot)
            when (val resolved = resolveOperationPath(op, ctx)) {
                is ResolvedTarget.Invalid -> Unit // already reported by validateOperation
                is ResolvedTarget.Valid -> {
                    val owner = seenPaths.put(resolved.path, op.id)
                    if (owner != null) {
                        errors += ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = op.id,
                            fieldPath = op.targetPartitionPath().relativePath,
                            code = FileMutationCodes.OVERLAPPING_MUTATION,
                            severity = Severity.ERROR,
                            message = "Operation '${op.id}' targets same path '${resolved.path}' as '$owner'",
                        )
                    }
                    planned += PlannedOperation(op, resolved.path, resolved.partitionRoot)
                }
            }
        }
        return if (errors.isEmpty()) CheckedPlan.Ready(planned) else CheckedPlan.Refused(ValidationReport(errors))
    }

    private fun validateOperation(
        op: ConcreteOperation,
        ctx: MutationContext,
        assetRoot: String,
    ): List<ValidationError> = when (op) {
        is ConcreteOperation.ApkJarPatch -> unsupported(op.id, op.target.relativePath, "ApkJarPatch", "T059")
        is ConcreteOperation.CompiledClassMerge ->
            unsupported(op.id, op.target.relativePath, "CompiledClassMerge", "T060")
        is ConcreteOperation.HookInjection -> unsupported(op.id, op.target.relativePath, "HookInjection", "T060")
        is ConcreteOperation.Copy -> validateCopy(op, ctx, assetRoot)
        is ConcreteOperation.Replace -> validateReplace(op, ctx, assetRoot)
        is ConcreteOperation.Delete -> validateDelete(op, ctx)
        is ConcreteOperation.PropertyPatch -> validatePropertyPatch(op, ctx)
        is ConcreteOperation.TextPatch -> validateTextPatch(op, ctx)
    }

    /** Bytecode operations fail closed here; they are implemented by the adapters those tasks add. */
    private fun unsupported(opId: String, relativePath: String, kind: String, task: String) = listOf(
        ValidationError(
            stageId = StageId.MODULE_APPLICATION,
            objectId = opId,
            fieldPath = relativePath,
            code = FileMutationCodes.UNSUPPORTED_OPERATION,
            severity = Severity.ERROR,
            message = "Operation '$opId' of type $kind is unsupported here (implemented by task $task)",
        ),
    )

    private fun validateCopy(
        op: ConcreteOperation.Copy,
        ctx: MutationContext,
        assetRoot: String,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        when (val resolved = resolveOperationPath(op, ctx)) {
            is ResolvedTarget.Invalid -> errors.add(resolved.error)
            is ResolvedTarget.Valid -> Unit
        }

        val source = Path.of(assetRoot).resolve(op.source.path).normalize()
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            errors.add(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = op.id,
                    fieldPath = op.source.path,
                    code = FileMutationCodes.SOURCE_ASSET_MISSING,
                    severity = Severity.ERROR,
                    message = "Source asset '${op.source.path}' not found at '$source'",
                ),
            )
        }
        return errors
    }

    private fun validateReplace(
        op: ConcreteOperation.Replace,
        ctx: MutationContext,
        assetRoot: String,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val targetPath = when (val resolved = resolveOperationPath(op, ctx)) {
            is ResolvedTarget.Invalid -> {
                errors.add(resolved.error)
                null
            }
            is ResolvedTarget.Valid -> resolved.path
        }

        val source = Path.of(assetRoot).resolve(op.source.path).normalize()
        if (!Files.exists(source) || !Files.isRegularFile(source)) {
            errors.add(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = op.id,
                    fieldPath = op.source.path,
                    code = FileMutationCodes.SOURCE_ASSET_MISSING,
                    severity = Severity.ERROR,
                    message = "Source asset '${op.source.path}' not found at '$source'",
                ),
            )
        }

        if (targetPath != null) {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                errors.add(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = op.id,
                        fieldPath = op.destination.relativePath,
                        code = FileMutationCodes.TARGET_MISSING,
                        severity = Severity.ERROR,
                        message = "Target file for replace '${op.destination.relativePath}' not found at '$targetPath'",
                    ),
                )
            } else {
                val actualSha = sha256Of(targetPath)
                if (!actualSha.equals(op.expectedSha256, ignoreCase = true)) {
                    errors.add(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = op.id,
                            fieldPath = op.destination.relativePath,
                            code = FileMutationCodes.PREIMAGE_MISMATCH,
                            severity = Severity.ERROR,
                            message = "Preimage mismatch for '${op.destination.relativePath}': " +
                                "expected ${op.expectedSha256}, actual $actualSha",
                        ),
                    )
                }
            }
        }
        return errors
    }

    private fun validateDelete(op: ConcreteOperation.Delete, ctx: MutationContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val targetPath = when (val resolved = resolveOperationPath(op, ctx)) {
            is ResolvedTarget.Invalid -> {
                errors.add(resolved.error)
                null
            }
            is ResolvedTarget.Valid -> resolved.path
        }

        if (targetPath != null) {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                errors.add(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = op.id,
                        fieldPath = op.target.relativePath,
                        code = FileMutationCodes.TARGET_MISSING,
                        severity = Severity.ERROR,
                        message = "Target file for delete '${op.target.relativePath}' not found at '$targetPath'",
                    ),
                )
            }
        }
        return errors
    }

    private fun validatePropertyPatch(
        op: ConcreteOperation.PropertyPatch,
        ctx: MutationContext,
    ): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        if (op.key.isEmpty() || op.key.indexOfAny(INVALID_PROP_KEY_CHARS) != -1) {
            errors.add(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = op.id,
                    fieldPath = op.key,
                    code = FileMutationCodes.PROPERTY_KEY_INVALID,
                    severity = Severity.ERROR,
                    message = "Invalid property key '${op.key}': must not be empty or contain '=', newline, or NUL",
                ),
            )
        }

        val targetPath = when (val resolved = resolveOperationPath(op, ctx)) {
            is ResolvedTarget.Invalid -> {
                errors.add(resolved.error)
                null
            }
            is ResolvedTarget.Valid -> resolved.path
        }

        if (targetPath != null) {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                errors.add(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = op.id,
                        fieldPath = op.target.relativePath,
                        code = FileMutationCodes.TARGET_MISSING,
                        severity = Severity.ERROR,
                        message = "Target file for property patch '${op.target.relativePath}' " +
                            "not found at '$targetPath'",
                    ),
                )
            }
        }
        return errors
    }

    private fun validateTextPatch(op: ConcreteOperation.TextPatch, ctx: MutationContext): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()
        val targetPath = when (val resolved = resolveOperationPath(op, ctx)) {
            is ResolvedTarget.Invalid -> {
                errors.add(resolved.error)
                null
            }
            is ResolvedTarget.Valid -> resolved.path
        }

        if (targetPath != null) {
            if (!Files.exists(targetPath) || !Files.isRegularFile(targetPath)) {
                errors.add(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = op.id,
                        fieldPath = op.target.relativePath,
                        code = FileMutationCodes.TARGET_MISSING,
                        severity = Severity.ERROR,
                        message = "Target file for text patch '${op.target.relativePath}' not found at '$targetPath'",
                    ),
                )
            } else {
                val actualSha = sha256Of(targetPath)
                if (!actualSha.equals(op.expectedSha256, ignoreCase = true)) {
                    errors.add(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = op.id,
                            fieldPath = op.target.relativePath,
                            code = FileMutationCodes.PREIMAGE_MISMATCH,
                            severity = Severity.ERROR,
                            message = "Preimage mismatch for '${op.target.relativePath}': " +
                                "expected ${op.expectedSha256}, actual $actualSha",
                        ),
                    )
                }

                val text = Files.readString(targetPath, Charsets.UTF_8)
                val count = countOccurrences(text, op.context)
                if (count == 0) {
                    errors.add(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = op.id,
                            fieldPath = op.target.relativePath,
                            code = FileMutationCodes.TEXT_CONTEXT_NOT_FOUND,
                            severity = Severity.ERROR,
                            message = "Context anchor '${op.context}' not found in '${op.target.relativePath}'",
                        ),
                    )
                } else if (count > 1) {
                    errors.add(
                        ValidationError(
                            stageId = StageId.MODULE_APPLICATION,
                            objectId = op.id,
                            fieldPath = op.target.relativePath,
                            code = FileMutationCodes.TEXT_CONTEXT_AMBIGUOUS,
                            severity = Severity.ERROR,
                            message = "Context anchor '${op.context}' is ambiguous (found $count times) " +
                                "in '${op.target.relativePath}'",
                        ),
                    )
                }
            }
        }
        return errors
    }

    private fun executeOperation(op: ConcreteOperation, targetPath: Path, assetRoot: String) {
        when (op) {
            is ConcreteOperation.Copy -> {
                val source = Path.of(assetRoot).resolve(op.source.path).normalize()
                targetPath.parent?.let { Files.createDirectories(it) }
                streamCopy(source, targetPath)
            }
            is ConcreteOperation.Replace -> {
                val source = Path.of(assetRoot).resolve(op.source.path).normalize()
                targetPath.parent?.let { Files.createDirectories(it) }
                streamCopy(source, targetPath)
            }
            is ConcreteOperation.Delete -> {
                Files.delete(targetPath)
            }
            is ConcreteOperation.PropertyPatch -> {
                applyPropertyPatch(targetPath, op.key, op.value)
            }
            is ConcreteOperation.TextPatch -> {
                applyTextPatch(targetPath, op.context, op.replacement)
            }
            is ConcreteOperation.ApkJarPatch,
            is ConcreteOperation.CompiledClassMerge,
            is ConcreteOperation.HookInjection,
            -> {
                error("Cannot execute unsupported operation: ${op.id}")
            }
        }
    }

    private fun computeEffect(op: ConcreteOperation, targetPath: Path, partitionRoot: Path): MutationEffect {
        val partPath = op.targetPartitionPath()
        val kind = when (op) {
            is ConcreteOperation.Copy -> if (Files.exists(targetPath)) EffectKind.REPLACED else EffectKind.CREATED
            is ConcreteOperation.Replace -> EffectKind.REPLACED
            is ConcreteOperation.Delete -> EffectKind.DELETED
            is ConcreteOperation.PropertyPatch -> EffectKind.PROPERTY_SET
            is ConcreteOperation.TextPatch -> EffectKind.TEXT_PATCHED
            is ConcreteOperation.ApkJarPatch,
            is ConcreteOperation.CompiledClassMerge,
            is ConcreteOperation.HookInjection,
            -> error("Unsupported operation cannot produce an effect: ${op.id}")
        }
        val sidecars = when (op) {
            is ConcreteOperation.Copy,
            is ConcreteOperation.Replace,
            is ConcreteOperation.Delete,
            -> findSidecarUpdates(partitionRoot, partPath.partition)
            is ConcreteOperation.PropertyPatch,
            is ConcreteOperation.TextPatch,
            is ConcreteOperation.ApkJarPatch,
            is ConcreteOperation.CompiledClassMerge,
            is ConcreteOperation.HookInjection,
            -> emptyList()
        }
        return MutationEffect(
            operationId = op.id,
            partition = partPath.partition,
            relativePath = partPath.relativePath,
            kind = kind,
            sidecarUpdates = sidecars,
        )
    }

    private fun resolveOperationPath(op: ConcreteOperation, ctx: MutationContext): ResolvedTarget =
        PartitionPathResolver.resolve(op.targetPartitionPath(), op.id, ctx)

    private fun findSidecarUpdates(partitionRoot: Path, partition: String): List<String> {
        val configsDir = partitionRoot.resolve("../configs").normalize()
        val results = mutableListOf<String>()
        val fsConfig = configsDir.resolve("fs_config-$partition").normalize()
        if (Files.exists(fsConfig)) {
            results.add(fsConfig.toString().replace('\\', '/'))
        }
        val fileContext = configsDir.resolve("file_context-$partition").normalize()
        if (Files.exists(fileContext)) {
            results.add(fileContext.toString().replace('\\', '/'))
        } else {
            val fileContexts = configsDir.resolve("file_contexts-$partition").normalize()
            if (Files.exists(fileContexts)) {
                results.add(fileContexts.toString().replace('\\', '/'))
            }
        }
        return results
    }

    private fun applyPropertyPatch(targetPath: Path, key: String, value: String) {
        val lines = Files.readAllLines(targetPath, Charsets.UTF_8)
        var keyFound = false
        val newLines = mutableListOf<String>()
        for (line in lines) {
            if (!keyFound && isPropKeyMatch(line, key)) {
                newLines.add("$key=$value")
                keyFound = true
            } else {
                newLines.add(line)
            }
        }
        if (!keyFound) {
            newLines.add("$key=$value")
        }

        val parentDir = targetPath.parent ?: Path.of(".")
        val tempFile = Files.createTempFile(parentDir, "prop-patch-", ".tmp")
        try {
            Files.newBufferedWriter(tempFile, Charsets.UTF_8).use { writer ->
                for (line in newLines) {
                    writer.write(line)
                    writer.write("\n")
                }
            }
            moveAtomically(tempFile, targetPath)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun isPropKeyMatch(line: String, key: String): Boolean {
        val trimmed = line.trimStart()
        val eqIdx = line.indexOf('=')
        return !trimmed.startsWith("#") && eqIdx != -1 && line.substring(0, eqIdx).trim() == key
    }

    private fun applyTextPatch(targetPath: Path, context: String, replacement: String) {
        val content = Files.readString(targetPath, Charsets.UTF_8)
        val idx = content.indexOf(context)
        check(idx != -1) { "Context anchor not found during apply: '$context'" }
        val newContent = content.substring(0, idx) + replacement + content.substring(idx + context.length)

        val parentDir = targetPath.parent ?: Path.of(".")
        val tempFile = Files.createTempFile(parentDir, "text-patch-", ".tmp")
        try {
            Files.writeString(tempFile, newContent, Charsets.UTF_8)
            moveAtomically(tempFile, targetPath)
        } finally {
            Files.deleteIfExists(tempFile)
        }
    }

    private fun moveAtomically(source: Path, destination: Path) {
        try {
            Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                source,
                destination,
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private fun streamCopy(source: Path, destination: Path) {
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        Files.newInputStream(source).use { input ->
            Files.newOutputStream(destination).use { output ->
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
            }
        }
    }

    private fun sha256Of(file: Path): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(PluginPackagePolicy.TRANSFER_CHUNK_BYTES.toInt())
        Files.newInputStream(file).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                md.update(buffer, 0, bytesRead)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
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

    private companion object {
        val INVALID_PROP_KEY_CHARS = charArrayOf('=', '\n', '\r', '\u0000')
    }
}
