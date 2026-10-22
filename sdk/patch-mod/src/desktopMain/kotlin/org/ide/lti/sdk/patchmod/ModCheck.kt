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

import org.ide.lti.core.domain.plugin.FileMutationCodes
import org.ide.lti.core.domain.plugin.edit.EditResult
import org.ide.lti.core.domain.plugin.edit.FileEdits
import org.ide.lti.core.domain.plugin.edit.MemberEdits
import org.ide.lti.core.model.plugin.AssetRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition
import org.ide.lti.core.model.plugin.SmaliEdit
import org.ide.lti.core.model.plugin.ValueRef
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** One edit of a mod checked against a firmware: [failure] is null when it applies. */
data class EditCheck(val mod: String, val operationId: String, val changed: String, val failure: String?) {
    /** The firmware part it needs (a decoded component, the work tree) was not given: it says nothing either way. */
    val notChecked: Boolean get() = failure?.startsWith(NOT_CHECKED) == true

    /** It does not apply. */
    val failed: Boolean get() = failure != null && !notChecked

    companion object {
        const val NOT_CHECKED: String = "not checked"
    }
}

/**
 * Checks mods against a firmware without building it, with the same engines as a build, and writes nothing:
 * - APK/JAR edits are applied in memory to the folders `apktool d` decoded the firmware's components into;
 * - file operations (copy, replace, delete, property and text patches) are applied in memory to an assembled work
 *   tree (a folder per partition, as `Assemble` leaves it).
 */
object ModCheck {

    /**
     * Applies [mods]' operations in order (a later mod sees what an earlier one changed, as in a build). [decoded]
     * gives the folder an operation's component was decoded into, or null when it was not; [tree] is the work tree
     * (null: file operations are reported as not checked); [assetExists] says whether a package asset is there
     * (it defaults to yes). Every operation is checked, whatever its condition.
     */
    fun check(
        mods: List<PluginPlanTemplate>,
        tree: File? = null,
        assetExists: (PluginPlanTemplate, AssetRef) -> Boolean = { _, _ -> true },
        decoded: (PartitionPath) -> File?,
    ): List<EditCheck> {
        val buffers = mutableMapOf<File, MutableMap<String, String>>()
        val listings = mutableMapOf<File, List<String>>()
        val files = WorkTreeOverlay(tree)
        return mods.flatMap { mod ->
            val touched = mutableMapOf<String, Operation>()
            mod.operations.flatMap { op ->
                when (op) {
                    is Operation.ApkJarPatch -> checkComponent(mod, op, decoded, buffers, listings)
                    is Operation.CompiledClassMerge, is Operation.HookInjection -> emptyList()
                    else -> listOf(checkFile(mod, op, files, touched, assetExists))
                }
            }
        }
    }

    private fun checkComponent(
        mod: PluginPlanTemplate,
        op: Operation.ApkJarPatch,
        decoded: (PartitionPath) -> File?,
        buffers: MutableMap<File, MutableMap<String, String>>,
        listings: MutableMap<File, List<String>>,
    ): List<EditCheck> {
        val dir = decoded(op.target)
            ?: return listOf(skipped(mod, op, op.target.relativePath, "no decoded ${op.target}"))
        val buffer = buffers.getOrPut(dir) { mutableMapOf() }
        val listing = listings.getOrPut(dir) { members(dir) }
        return op.edits.map { edit ->
            when (val result = apply(edit, dir, buffer, listing)) {
                is EditResult.Changed -> EditCheck(mod.id, op.id, result.changed, null)
                is EditResult.Failed ->
                    EditCheck(mod.id, op.id, edit.memberDescriptor, "${result.code}: ${result.message}")
            }
        }
    }

    private fun skipped(mod: PluginPlanTemplate, op: Operation, changed: String, why: String) =
        EditCheck(mod.id, op.id, changed, "${EditCheck.NOT_CHECKED}: $why")

    @Suppress("CyclomaticComplexMethod")
    private fun checkFile(
        mod: PluginPlanTemplate,
        op: Operation,
        files: WorkTreeOverlay,
        touched: MutableMap<String, Operation>,
        assetExists: (PluginPlanTemplate, AssetRef) -> Boolean,
    ): EditCheck {
        val target = target(op)
        val resolved = files.resolve(target)
        val failure = when {
            resolved is Resolved.Refused -> resolved.reason
            resolved is Resolved.NoTree -> "${EditCheck.NOT_CHECKED}: no work tree (a folder per partition)"
            resolved is Resolved.Path -> {
                val owner = touched.put(resolved.key, op)
                val overlaps = owner != null && !(inPlace(owner) && inPlace(op))
                if (overlaps) {
                    "${FileMutationCodes.OVERLAPPING_MUTATION}: '${op.id}' targets the path of '${owner?.id}'"
                } else {
                    apply(mod, op, resolved.key, files, assetExists)
                }
            }
            else -> null
        }
        return EditCheck(mod.id, op.id, "${target.partition}:${target.relativePath}", failure)
    }

    @Suppress("CyclomaticComplexMethod")
    private fun apply(
        mod: PluginPlanTemplate,
        op: Operation,
        key: String,
        files: WorkTreeOverlay,
        assetExists: (PluginPlanTemplate, AssetRef) -> Boolean,
    ): String? = when (op) {
        is Operation.Copy -> missingAsset(mod, op.source, assetExists) ?: files.put(key, Entry.Opaque).let { null }
        is Operation.Replace -> missingAsset(mod, op.source, assetExists)
            ?: files.requireFile(key, op.destination.relativePath)
            ?: files.preimage(key, op.expectedSha256, op.destination.relativePath)
            ?: files.put(key, Entry.Opaque).let { null }
        is Operation.Delete -> files.requireFile(key, op.target.relativePath)
            ?: files.put(key, Entry.Deleted).let { null }
        is Operation.PropertyPatch -> files.requireFile(key, op.target.relativePath)
            ?: files.edit(key) { FileEdits.propertyPatch(it, op.key, value(mod, op.value)) }
        is Operation.TextPatch -> files.requireFile(key, op.target.relativePath)
            ?: files.edit(key) { text ->
                val actual = if (op.expectedSha256.isEmpty()) "" else files.sha256(key).orEmpty()
                FileEdits.textPatch(
                    text,
                    op.context,
                    value(mod, op.replacement),
                    op.expectedSha256,
                    actual,
                    op.target.relativePath,
                )
            }
        else -> null
    }

    private fun missingAsset(
        mod: PluginPlanTemplate,
        source: AssetRef,
        assetExists: (PluginPlanTemplate, AssetRef) -> Boolean,
    ): String? = if (assetExists(mod, source)) {
        null
    } else {
        "${FileMutationCodes.SOURCE_ASSET_MISSING}: asset '${source.path}' is not in the package"
    }

    private fun target(op: Operation): PartitionPath = when (op) {
        is Operation.Copy -> op.destination
        is Operation.Replace -> op.destination
        is Operation.Delete -> op.target
        is Operation.PropertyPatch -> op.target
        is Operation.TextPatch -> op.target
        is Operation.ApkJarPatch -> op.target
        is Operation.CompiledClassMerge -> op.target
        is Operation.HookInjection -> op.target
    }

    private fun inPlace(op: Operation) = op is Operation.PropertyPatch || op is Operation.TextPatch

    /** A value a plan sets: a literal, or a setting's default (what a first build uses); a fact stands as a name. */
    private fun value(mod: PluginPlanTemplate, ref: ValueRef): String = when (ref) {
        is ValueRef.Literal -> ref.value
        is ValueRef.Setting -> mod.settings.firstOrNull { it.id == ref.settingId }?.let(::default).orEmpty()
        is ValueRef.TargetFact -> "<${ref.factId}>"
    }

    private fun default(setting: SettingDefinition): String = when (setting) {
        is SettingDefinition.BooleanSetting -> setting.default.toString()
        is SettingDefinition.IntSetting -> setting.default.toString()
        is SettingDefinition.TextSetting -> setting.default
        is SettingDefinition.EnumSetting -> setting.default
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

    /** What an earlier operation left at a path: its text, a file whose content is not known, or nothing. */
    private sealed interface Entry {
        data class Text(val content: String) : Entry
        data object Opaque : Entry
        data object Deleted : Entry
    }

    private sealed interface Resolved {
        /** [key] names the file (`partition:path`) among the overlay's entries. */
        data class Path(val key: String, val file: File) : Resolved
        data class Refused(val reason: String) : Resolved
        data object NoTree : Resolved
    }

    /** The work tree with the changes made so far laid over it in memory; the tree itself is never written. */
    private class WorkTreeOverlay(private val root: File?) {
        private val entries = mutableMapOf<String, Entry>()
        private val files = mutableMapOf<String, File>()

        fun resolve(target: PartitionPath): Resolved {
            val tree = root ?: return Resolved.NoTree
            val partition = File(tree, target.partition)
            val root = partition.toPath().toAbsolutePath().normalize()
            val path = root.resolve(target.relativePath).normalize()
            return when {
                !partition.isDirectory -> Resolved.Refused(
                    "${FileMutationCodes.PARTITION_NOT_IN_TREE}: partition '${target.partition}' is not in the tree",
                )
                !path.startsWith(root) -> Resolved.Refused(
                    "${FileMutationCodes.PATH_ESCAPES_PARTITION}: '${target.relativePath}' escapes the partition",
                )
                crossesLink(root, path) -> Resolved.Refused(
                    "${FileMutationCodes.PATH_ESCAPES_PARTITION}: '${target.relativePath}' passes through a link",
                )
                else -> {
                    val key = "${target.partition}:${root.relativize(path).toString().replace('\\', '/')}"
                    files[key] = path.toFile()
                    Resolved.Path(key, path.toFile())
                }
            }
        }

        /** A symbolic link anywhere on the way would lead out of the partition, as the build refuses it. */
        private fun crossesLink(base: Path, path: Path): Boolean {
            val relative = base.relativize(path)
            return (0 until relative.nameCount).any { Files.isSymbolicLink(base.resolve(relative.subpath(0, it + 1))) }
        }

        fun put(key: String, entry: Entry) {
            entries[key] = entry
        }

        private fun exists(key: String): Boolean = when (entries[key]) {
            is Entry.Text, Entry.Opaque -> true
            Entry.Deleted -> false
            null -> files.getValue(key).isFile
        }

        fun requireFile(key: String, relative: String): String? =
            if (exists(key)) null else "${FileMutationCodes.TARGET_MISSING}: file '$relative' is not in the tree"

        /** The file's text now, or null when an earlier operation left content that is not known. */
        private fun text(key: String): String? = when (val entry = entries[key]) {
            is Entry.Text -> entry.content
            Entry.Opaque, Entry.Deleted -> null
            null -> files.getValue(key).readText()
        }

        fun sha256(key: String): String? = when (val entry = entries[key]) {
            is Entry.Text -> hash(entry.content.toByteArray())
            Entry.Opaque, Entry.Deleted -> null
            null -> files.getValue(key).inputStream().use { hash(it.readBytes()) }
        }

        fun preimage(key: String, expected: String, relative: String): String? {
            val actual = sha256(key)
            return if (actual == null || actual.equals(expected, ignoreCase = true)) {
                null
            } else {
                "${FileMutationCodes.PREIMAGE_MISMATCH}: '$relative' expected $expected, actual $actual"
            }
        }

        /** Applies [change] to the file's text and keeps the result; the failure as `code: message`, or null. */
        fun edit(key: String, change: (String) -> EditResult): String? {
            val current = text(key) ?: return null // content left by an earlier copy: nothing to check against
            return when (val result = change(current)) {
                is EditResult.Changed -> null.also { entries[key] = Entry.Text(result.content) }
                is EditResult.Failed -> "${result.code}: ${result.message}"
            }
        }

        private fun hash(bytes: ByteArray) =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
