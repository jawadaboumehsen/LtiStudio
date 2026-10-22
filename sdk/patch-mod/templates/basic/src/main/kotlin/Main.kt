/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.example.mod

import kotlinx.serialization.json.Json
import org.ide.lti.core.model.plugin.PluginManifest
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.sdk.patchmod.cli.CliResult
import org.ide.lti.sdk.patchmod.cli.PatchModTool
import java.io.File

/** Export the Kotlin declaration, then use the SDK's validator and schema generator. */
fun main(args: Array<String>) {
    val output = File(args.single()).apply { mkdirs() }
    val plan = basicMod
    val manifest = PluginManifest(
        sdkApiRange = "1.x",
        publisher = plan.publisher,
        id = plan.id,
        version = plan.version,
        displayName = "Basic Mod",
        license = "MPL-2.0",
        compatibleTargets = plan.compatibility.map { it.device }.distinct(),
    )
    val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    output.resolve("manifest.json").writeText(json.encodeToString(PluginManifest.serializer(), manifest) + "\n")
    output.resolve("plan.json").writeText(json.encodeToString(PluginPlanTemplate.serializer(), plan) + "\n")
    when (val result = PatchModTool.generateModPlan(output)) {
        is CliResult.Success -> println(result.message)
        is CliResult.Failure -> error(result.message + "\n" + result.errors.joinToString("\n"))
    }
    output.resolve("build/settings.schema.json").copyTo(output.resolve("settings.schema.json"), overwrite = true)
}
