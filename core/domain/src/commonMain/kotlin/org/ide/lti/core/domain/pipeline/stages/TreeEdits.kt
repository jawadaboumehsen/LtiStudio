/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import org.ide.lti.core.domain.pipeline.HostRequest
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.TreeEdit

/** Rewrites the fs_config and SELinux lines of [edits] (already applied to the files) in `work/configs`. */
public data class TreeEditSidecars(val edits: List<TreeEdit>) : HostRequest

/**
 * The work-tree renames and deletions kept in [org.ide.lti.core.model.workspace.AssemblySettings.treeEdits]. The
 * Patches stage applies them last: each is checked and done in WSL (the path must still exist and stay inside its
 * partition, a rename must not replace anything), then its sidecar lines follow it, so the item keeps its owner,
 * mode and label under the new name.
 */
public object TreeEdits {
    public const val SIDECARS_KEY: String = "tree.edits.sidecars"
    public const val INVALID_TREE_EDIT: String = "INVALID_TREE_EDIT"

    /**
     * `sh -c APPLY_SCRIPT sh <partition dir> <path> [<new name>]`: deletes or renames `<partition dir>/<path>`.
     * Refuses (exit 3, with the reason) a path that is gone, a folder on the way that leads out of the partition, or
     * a new name that is already taken.
     */
    public const val APPLY_SCRIPT: String = """
        set -e
        p="$1"; s="$1/$2"; n="$3"
        if [ ! -e "${'$'}s" ] && [ ! -L "${'$'}s" ]; then
          echo "TREE_EDIT_MISSING: $2 is no longer in the work tree. Remove this change in Assemble > Image browser." >&2
          exit 3
        fi
        base=$(realpath -m "${'$'}p")
        case "$(realpath -m "$(dirname "${'$'}s")")/" in
          "${'$'}base"/*) ;;
          *) echo "TREE_EDIT_OUTSIDE: a folder on the way to $2 leads out of the partition." >&2; exit 3 ;;
        esac
        if [ -z "${'$'}n" ]; then rm -rf "${'$'}s"; exit 0; fi
        d="$(dirname "${'$'}s")/${'$'}n"
        if [ -e "${'$'}d" ] || [ -L "${'$'}d" ]; then
          echo "TREE_EDIT_TAKEN: ${'$'}n already exists next to $2. Pick another name in Assemble > Image browser." >&2
          exit 3
        fi
        mv -T "${'$'}s" "${'$'}d"
    """

    /** Why [edit] cannot be applied as written, or null. [partitions] are the device's dynamic partitions. */
    public fun problem(edit: TreeEdit, partitions: List<String>?): String? {
        val parts = edit.path.trim('/').split('/')
        return when {
            partitions != null && edit.partition !in partitions -> "${edit.partition} is not a partition of the device."

            edit.path.isBlank() || parts.any { it.isEmpty() || it == "." || it == ".." } ->
                "'${edit.path}' is not a path inside ${edit.partition}."

            else -> edit.renameTo?.let { nameProblem(it, parts.last()) }
        }
    }

    private fun nameProblem(name: String, old: String): String? = when {
        name.isBlank() || name == "." || name == ".." || '/' in name ->
            "'$name' is not a file name (no '/', not '.' or '..')."

        name.any(Char::isWhitespace) -> "'$name' holds a space: no permission or SELinux line could describe it."

        name == old -> "'$name' is the name it already has."

        else -> null
    }

    /** Every problem of [edits] for the Assemble settings check: each edit's own, and a path changed twice. */
    public fun validationErrors(edits: List<TreeEdit>, partitions: List<String>?): List<ValidationError> {
        val own = edits.mapIndexedNotNull { i, edit ->
            problem(edit, partitions)?.let { error("treeEdits[$i]", "Change to ${edit.partition}/${edit.path}: $it") }
        }
        val twice = edits.groupBy { it.partition to it.path.trim('/') }.filterValues { it.size > 1 }.keys
            .map { (p, path) -> error("treeEdits", "$p/$path has more than one change; keep one") }
        return own + twice
    }

    private fun error(field: String, message: String) =
        ValidationError(StageId.WORK_TREE_ASSEMBLY, null, field, INVALID_TREE_EDIT, Severity.ERROR, message)

    /** The path [edit] leaves in the tree: the new one for a rename, null for a deletion. */
    public fun result(edit: TreeEdit): String? = edit.renameTo?.let { new ->
        val path = edit.path.trim('/')
        (path.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }) + new
    }

    public fun steps(ctx: StageContext): List<PipelineStep> {
        val edits = ctx.snapshot.assembly.treeEdits
        if (edits.isEmpty()) return emptyList()
        val apply = edits.mapIndexed { i, edit ->
            PipelineStep.Tool(
                stepCommand(
                    "sh",
                    listOf("-c", APPLY_SCRIPT, "sh", ctx.abs("work/${edit.partition}"), edit.path.trim('/')) +
                        listOfNotNull(edit.renameTo),
                    ctx.wsPath,
                ),
                "${if (edit.renameTo == null) "delete" else "rename"} ${edit.partition}/${edit.path} (${i + 1})",
            )
        }
        return apply + PipelineStep.Host("move the sidecar lines of tree edits", SIDECARS_KEY, TreeEditSidecars(edits))
    }

    /** [fsConfig] and [fileContexts] of [partition] with [edits] (of that partition) applied, in order. */
    public fun sidecars(
        partition: String,
        edits: List<TreeEdit>,
        fsConfig: String,
        fileContexts: String,
    ): Pair<String, String> = edits.filter { it.partition == partition }.fold(
        fsConfig to fileContexts,
    ) { (fs, ctx), e ->
        val path = e.path.trim('/')
        when (val to = result(e)) {
            null -> SidecarEditor.withoutFsConfig(fs, partition, listOf(path)) to
                SidecarEditor.withoutFileContext(ctx, partition, listOf(path))

            else -> SidecarEditor.withRenamedFsConfig(fs, partition, path, to) to
                SidecarEditor.withRenamedFileContext(ctx, partition, path, to)
        }
    }
}
