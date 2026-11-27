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

import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.plugin.Operation
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.plugin.SettingDefinition

/**
 * What moving a mod from [from] to [to] changes, from the two installed versions' own plans and manifests: its
 * options, the files and packages it edits, the devices and firmwares it is made for, and its dependencies and
 * conflicts. Each section is a list of lines; an empty section did not change. Whether a workspace's selection still
 * checks with [to] is the mod lab's to say (it needs the workspace's tree).
 */
data class ModUpdateReview(
    val key: String,
    val from: String,
    val to: String,
    val sections: Map<String, List<String>>,
) {
    /** Nothing a review would show changed (a rebuild of the same mod, say). */
    val unchanged: Boolean get() = sections.values.all { it.isEmpty() }

    companion object {
        const val OPTIONS: String = "Options"
        const val FILES: String = "Files it edits"
        const val DEVICES: String = "Devices and firmwares"
        const val DEPENDENCIES: String = "Dependencies and conflicts"

        /** [old] and [new] (null: that version's plan could not be read, said in its section). */
        @Suppress("LongParameterList")
        fun of(
            key: String,
            from: String,
            to: String,
            old: PluginPlanTemplate?,
            new: PluginPlanTemplate?,
            oldPackage: InstalledPackage?,
            newPackage: InstalledPackage?,
        ): ModUpdateReview {
            val unreadable = listOfNotNull(
                "The plan of $from could not be read".takeIf { old == null },
                "The plan of $to could not be read".takeIf { new == null },
            )
            val sections = if (old == null || new == null) {
                mapOf(OPTIONS to unreadable, FILES to emptyList(), DEVICES to emptyList())
            } else {
                mapOf(OPTIONS to options(old, new), FILES to files(old, new), DEVICES to devices(old, new))
            }
            return ModUpdateReview(key, from, to, sections + (DEPENDENCIES to dependencies(oldPackage, newPackage)))
        }

        private fun options(old: PluginPlanTemplate, new: PluginPlanTemplate): List<String> {
            val before = old.settings.associateBy { it.id }
            val after = new.settings.associateBy { it.id }
            return (before.keys + after.keys).sorted().mapNotNull { id ->
                val a = before[id]
                val b = after[id]
                when {
                    a == null -> "new: ${b!!.label} (${describe(b)})"
                    b == null -> "gone: ${a.label} (its value is no longer used)"
                    describe(a) != describe(b) -> "${b.label}: ${describe(a)} → ${describe(b)}"
                    else -> null
                }
            }
        }

        private fun describe(s: SettingDefinition): String = when (s) {
            is SettingDefinition.BooleanSetting -> "on/off, default ${if (s.default) "on" else "off"}"
            is SettingDefinition.IntSetting -> "number ${s.min}-${s.max}, default ${s.default}"
            is SettingDefinition.TextSetting -> "text, default \"${s.default}\""
            is SettingDefinition.EnumSetting -> "one of ${s.options.joinToString("/")}, default ${s.default}"
        } + if (s.required) ", required" else ""

        /** Per file: newly edited, no longer edited, or edited by another number or kind of operations. */
        private fun files(old: PluginPlanTemplate, new: PluginPlanTemplate): List<String> {
            val before = old.operations.groupBy(::target).mapValues { (_, ops) -> kinds(ops) }
            val after = new.operations.groupBy(::target).mapValues { (_, ops) -> kinds(ops) }
            return (before.keys + after.keys).sorted().mapNotNull { file ->
                val a = before[file]
                val b = after[file]
                when {
                    a == null -> "now edits $file ($b)"
                    b == null -> "no longer edits $file (was $a)"
                    a != b -> "$file: $a → $b"
                    else -> null
                }
            }
        }

        private fun target(op: Operation): String = when (op) {
            is Operation.Copy -> op.destination
            is Operation.Replace -> op.destination
            is Operation.Delete -> op.target
            is Operation.PropertyPatch -> op.target
            is Operation.TextPatch -> op.target
            is Operation.ApkJarPatch -> op.target
        }.let { "${it.partition}/${it.relativePath}" }

        /** "2 APK/JAR edits, 1 property" for one file's operations. */
        private fun kinds(ops: List<Operation>): String = ops.groupingBy { kindOf(it) }.eachCount().entries
            .sortedBy { it.key }.joinToString { (kind, n) -> "$n $kind" }

        private fun kindOf(op: Operation): String = when (op) {
            is Operation.Copy -> "add"
            is Operation.Replace -> "replace"
            is Operation.Delete -> "delete"
            is Operation.PropertyPatch -> "property"
            is Operation.TextPatch -> "text edit"
            is Operation.ApkJarPatch -> "APK/JAR edit"
        }

        private fun devices(old: PluginPlanTemplate, new: PluginPlanTemplate): List<String> {
            fun of(t: PluginPlanTemplate) = t.compatibility.associate { it.device to it.firmwares.toSet() }
            val before = of(old)
            val after = of(new)
            if (before.isEmpty() != after.isEmpty()) {
                return listOf(
                    if (after.isEmpty()) "now made for any device" else "now only for ${list(after.keys)}",
                )
            }
            return (before.keys + after.keys).sorted().mapNotNull { device ->
                val a = before[device]
                val b = after[device]
                when {
                    a == null -> "now also for $device"
                    b == null -> "no longer for $device"
                    a != b -> "$device firmwares: ${list(a)} → ${list(b)}"
                    else -> null
                }
            }
        }

        private fun list(firmwares: Set<String>): String = firmwares.sorted().joinToString().ifEmpty { "any" }

        private fun dependencies(old: InstalledPackage?, new: InstalledPackage?): List<String> {
            fun refs(p: InstalledPackage?, of: (InstalledPackage) -> List<DependencyRef>) =
                p?.let(of).orEmpty().associate { "${it.publisher}:${it.id}" to it.versionRange }
            return changes("needs", refs(old) { it.dependencies }, refs(new) { it.dependencies }) +
                changes("conflicts with", refs(old) { it.conflicts }, refs(new) { it.conflicts })
        }

        private fun changes(verb: String, before: Map<String, String>, after: Map<String, String>): List<String> =
            (before.keys + after.keys).sorted().mapNotNull { key ->
                val a = before[key]
                val b = after[key]
                when {
                    a == null -> "now $verb $key $b"
                    b == null -> "no longer $verb $key"
                    a != b -> "$verb $key: $a → $b"
                    else -> null
                }
            }
    }
}
