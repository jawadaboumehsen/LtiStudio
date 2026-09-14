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
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.FileMutationCodes
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.run.StageId
import java.nio.file.Files
import java.nio.file.Path

internal sealed interface ResolvedTarget {
    data class Valid(val path: Path, val partitionRoot: Path) : ResolvedTarget
    data class Invalid(val error: ValidationError) : ResolvedTarget
}

internal fun ConcreteOperation.targetPartitionPath(): PartitionPath = when (this) {
    is ConcreteOperation.Copy -> destination
    is ConcreteOperation.Replace -> destination
    is ConcreteOperation.Delete -> target
    is ConcreteOperation.PropertyPatch -> target
    is ConcreteOperation.TextPatch -> target
    is ConcreteOperation.ApkJarPatch -> target
    is ConcreteOperation.CompiledClassMerge -> target
    is ConcreteOperation.HookInjection -> target
}

internal object PartitionPathResolver {

    @Suppress("ReturnCount")
    fun resolve(partPath: PartitionPath, opId: String, ctx: MutationContext): ResolvedTarget {
        val partitionRel = ctx.partitionRoots[partPath.partition]
            ?: return ResolvedTarget.Invalid(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = opId,
                    fieldPath = partPath.partition,
                    code = FileMutationCodes.PARTITION_NOT_IN_TREE,
                    severity = Severity.ERROR,
                    message = "Partition '${partPath.partition}' not found in work tree partition roots",
                ),
            )

        val workTree = Path.of(ctx.workTreeRelPath).toAbsolutePath().normalize()
        val partitionRoot = workTree.resolve(partitionRel).normalize()
        if (!partitionRoot.startsWith(workTree)) {
            return ResolvedTarget.Invalid(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = opId,
                    fieldPath = partPath.partition,
                    code = FileMutationCodes.PATH_ESCAPES_PARTITION,
                    severity = Severity.ERROR,
                    message = "Partition root '$partitionRel' escapes work tree root",
                ),
            )
        }

        val realPartitionRoot = if (Files.exists(partitionRoot)) {
            partitionRoot.toRealPath()
        } else {
            partitionRoot
        }

        val resolved = partitionRoot.resolve(partPath.relativePath).normalize()
        if (!resolved.startsWith(partitionRoot)) {
            return ResolvedTarget.Invalid(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = opId,
                    fieldPath = partPath.relativePath,
                    code = FileMutationCodes.PATH_ESCAPES_PARTITION,
                    severity = Severity.ERROR,
                    message = "Path '${partPath.relativePath}' escapes partition root '${partPath.partition}'",
                ),
            )
        }

        if (Files.exists(partitionRoot) && !resolved.toAbsolutePath().normalize().startsWith(realPartitionRoot)) {
            return ResolvedTarget.Invalid(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = opId,
                    fieldPath = partPath.relativePath,
                    code = FileMutationCodes.PATH_ESCAPES_PARTITION,
                    severity = Severity.ERROR,
                    message = "Path '${partPath.relativePath}' escapes real partition root",
                ),
            )
        }

        val relToRoot = partitionRoot.relativize(resolved)
        var curr = partitionRoot
        for (i in 0 until relToRoot.nameCount) {
            val segment = relToRoot.getName(i)
            if (segment.toString().isEmpty()) continue
            curr = curr.resolve(segment)
            if (Files.isSymbolicLink(curr)) {
                return ResolvedTarget.Invalid(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = opId,
                        fieldPath = partPath.relativePath,
                        code = FileMutationCodes.PATH_ESCAPES_PARTITION,
                        severity = Severity.ERROR,
                        message = "Path element '$segment' is a symbolic link",
                    ),
                )
            }
            if (Files.exists(curr) && !curr.toRealPath().startsWith(realPartitionRoot)) {
                return ResolvedTarget.Invalid(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = opId,
                        fieldPath = partPath.relativePath,
                        code = FileMutationCodes.PATH_ESCAPES_PARTITION,
                        severity = Severity.ERROR,
                        message = "Path element '$segment' traverses outside real partition root",
                    ),
                )
            }
        }

        return ResolvedTarget.Valid(resolved, partitionRoot)
    }
}
