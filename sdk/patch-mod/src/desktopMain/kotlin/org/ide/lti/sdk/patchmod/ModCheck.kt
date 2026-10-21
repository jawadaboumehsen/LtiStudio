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

import org.ide.lti.core.domain.plugin.edit.EditResult
import org.ide.lti.core.domain.plugin.edit.MemberEdits
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SmaliEdit
import java.io.File

/** One edit of a mod checked against a decoded firmware: [failure] is null when it applies. */
data class EditCheck(val mod: String, val operationId: String, val changed: String, val failure: String?)

/**
 * Checks mods against a firmware without building it: their APK/JAR edits are applied in memory, with the same
 * engine as a build, to the folders `apktool d` decoded the firmware's components into. Nothing is written.
 */
object ModCheck {

    /**
     * Applies [mods]' edits in order (a later mod sees what an earlier one changed, as in a build). [decoded] gives
     * the folder an operation's component was decoded into, or null when it was not (those edits are reported as
     * not checked). Every operation is checked, whatever its condition.
     */
    fun check(mods: List<PluginPlanTemplate>, decoded: (PartitionPath) -> File?): List<EditCheck> {
        val buffers = mutableMapOf<File, MutableMap<String, String>>()
        val listings = mutableMapOf<File, List<String>>()
        return mods.flatMap { mod ->
            mod.operations.filterIsInstance<Operation.ApkJarPatch>().flatMap { op ->
                val dir = decoded(op.target)
                if (dir == null) {
                    listOf(EditCheck(mod.id, op.id, op.target.relativePath, "not checked: no decoded ${op.target}"))
                } else {
                    val buffer = buffers.getOrPut(dir) { mutableMapOf() }
                    val listing = listings.getOrPut(dir) { members(dir) }
                    op.edits.map { edit ->
                        when (val result = apply(edit, dir, buffer, listing)) {
                            is EditResult.Changed -> EditCheck(mod.id, op.id, result.changed, null)
                            is EditResult.Failed ->
                                EditCheck(mod.id, op.id, edit.memberDescriptor, "${result.code}: ${result.message}")
                        }
                    }
                }
            }
        }
    }

    private fun apply(
        edit: SmaliEdit,
        dir: File,
        buffer: MutableMap<String, String>,
        listing: List<String>,
    ): EditResult {
        val member = if (edit.method == null) {
            edit.memberDescriptor
        } else {
            when (val found = MemberEdits.classMember(edit.memberDescriptor, buffer.keys + listing)) {
                is EditResult.Failed -> return found
                is EditResult.Changed -> found.content
            }
        }
        val existing = buffer[member] ?: File(dir, member).takeIf { it.isFile }?.readText()
        return MemberEdits.apply(existing, edit).also { if (it is EditResult.Changed) buffer[member] = it.content }
    }

    private fun members(dir: File): List<String> =
        dir.walk().filter { it.isFile }.map { it.relativeTo(dir).invariantSeparatorsPath }.toList()
}
