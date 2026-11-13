/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.ReleaseSettings

/** The named inputs a stage's cache record keeps, and what changed between two of them ("why did this run?"). */
public object StageInputs {
    /** [stage]'s own settings, one entry per field (`build.compression`), as JSON text. */
    public fun settings(stage: StageId, snapshot: ConfigurationSnapshot, json: Json): Map<String, String> {
        val (name, element) = when (stage) {
            StageId.FIRMWARE_ACQUISITION -> "acquisition" to json.encodeToJsonElement(
                AcquisitionSettings.serializer(),
                snapshot.acquisition,
            )

            StageId.FIRMWARE_EXTRACTION -> "extraction" to json.encodeToJsonElement(
                ExtractionSettings.serializer(),
                snapshot.extraction,
            )

            StageId.WORK_TREE_ASSEMBLY -> "assembly" to json.encodeToJsonElement(
                AssemblySettings.serializer(),
                snapshot.assembly,
            )

            StageId.DEBLOAT -> "debloat" to json.encodeToJsonElement(DebloatSettings.serializer(), snapshot.debloat)

            StageId.MODULE_APPLICATION ->
                "customization" to json.encodeToJsonElement(CustomizationSettings.serializer(), snapshot.customization)

            StageId.BUILD_FLASHABLE_ZIP -> "build" to json.encodeToJsonElement(
                BuildSettings.serializer(),
                snapshot.build,
            )

            StageId.GENERATE_OTA_MANIFEST -> "release" to json.encodeToJsonElement(
                ReleaseSettings.serializer(),
                snapshot.release,
            )

            else -> return emptyMap()
        }
        return flatten(name, element)
    }

    private fun flatten(path: String, element: JsonElement): Map<String, String> = when (element) {
        is JsonObject -> element.entries.flatMap { (k, v) -> flatten("$path.$k", v).entries }.associate { it.toPair() }
        is JsonPrimitive -> mapOf(path to element.content)
        else -> mapOf(path to element.toString())
    }

    /** Each input that differs, as `name: before → now` (long values shortened), in a stable order. */
    public fun changes(before: Map<String, String>, now: Map<String, String>): List<String> =
        (before.keys + now.keys).sorted().filter { before[it] != now[it] }.map { key ->
            "$key: ${short(before[key] ?: "(none)")} → ${short(now[key] ?: "(none)")}"
        }

    private fun short(value: String): String = if (value.length <= SHOWN) value else value.take(SHOWN) + "…"

    private const val SHOWN = 48
}
