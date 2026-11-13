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

import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * A changelog drafted from what the last build really did: the mods it applied (from `work/modules/plan.json`) and
 * the files they changed, the apps Debloat removed (`work/debloat/result.json`) and the space they freed, and the
 * build type and property overrides of the settings. Plain text the user edits before release.
 */
public object ChangelogDraft {
    /** The files the last build left: `work/modules/plan.json` and Debloat's `result.json`, as text. */
    public fun of(planJson: String?, debloatJson: String?, snapshot: ConfigurationSnapshot?): String = of(
        planJson?.let {
            runCatching {
                ConfigurationSnapshot.canonicalJson.decodeFromString<AppliedModulePlan>(
                    it,
                )
            }.getOrNull()
        },
        debloatJson?.let(DebloatStage::parseResult),
        snapshot,
    )

    public const val PLAN: String = "work/modules/plan.json"

    public fun of(plan: AppliedModulePlan?, debloat: DebloatDecision?, snapshot: ConfigurationSnapshot?): String {
        val sections = buildList {
            plan?.takeIf { it.lockfileEntries.isNotEmpty() }?.let { p ->
                add(
                    "Mods\n" + p.lockfileEntries.joinToString("\n") { "- ${it.id} ${it.version}" } +
                        p.operations.map { it.target.substringAfterLast('/') }.distinct().takeIf { it.isNotEmpty() }
                            ?.let { files ->
                                "\n\n${p.operations.size} change(s) to ${files.take(FILES_SHOWN).joinToString()}" +
                                    if (files.size > FILES_SHOWN) " and ${files.size - FILES_SHOWN} more" else ""
                            }.orEmpty(),
                )
            }
            debloat?.removals?.takeIf { it.isNotEmpty() }?.let { removed ->
                add(
                    "Removed apps (${debloat.logicalBytesFreed / MIB} MB freed)\n" +
                        removed.joinToString("\n") { r ->
                            "- ${r.relativePath.substringAfterLast('/')}" + (r.packageId?.let { " ($it)" } ?: "")
                        },
                )
            }
            snapshot?.assembly?.let { a ->
                val buildType = listOfNotNull("- build type ${a.buildType}".takeIf { a.buildType != "user" })
                val props = a.propertyOverrides.entries.sortedBy { it.key }.map { "- ${it.key}=${it.value}" }
                val lines = buildType + props
                if (lines.isNotEmpty()) add((listOf("System") + lines).joinToString("\n"))
            }
        }
        return sections.joinToString("\n\n")
    }

    private const val MIB = 1024L * 1024
    private const val FILES_SHOWN = 8
}
