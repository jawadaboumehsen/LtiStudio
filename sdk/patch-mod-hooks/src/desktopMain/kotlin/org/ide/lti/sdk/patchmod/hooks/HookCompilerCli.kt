/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod.hooks

import kotlinx.serialization.json.Json
import java.io.File
import kotlin.system.exitProcess

/**
 * Compiles the `.java` files under `<project>/hooks` against the firmware `payload.json` names into
 * `<project>/assets/payload/` smali. Returns the problems found (empty when it compiled, or with no `payload.json`).
 */
fun compileModPayload(project: File): List<String> {
    val payload = project.resolve("payload.json")
    val parsed = payload.takeIf { it.isFile }?.let {
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString(PayloadConfig.serializer(), it.readText()) }
    }
    val config = parsed?.getOrNull()
    return when {
        parsed == null -> emptyList()
        config == null -> listOf("payload.json: ${parsed.exceptionOrNull()?.message}")
        else -> HookCompiler.compile(
            roots = listOf(HookCompiler.Root(project.resolve("hooks"), project.resolve("assets/payload"))),
            firmware = config.firmware.map { File(it).takeIf(File::isAbsolute) ?: project.resolve(it) },
            work = project.resolve("build/hooks"),
            minApi = config.minRuntimeApi,
        )
    }
}

/** `compileModPayload --project <dir>`. */
fun main(args: Array<String>) {
    val project = args.indexOf("--project").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) } ?: "."
    if (args.firstOrNull() != "compileModPayload") {
        System.err.println("usage: compileModPayload --project <dir>")
        exitProcess(2)
    }
    val problems = compileModPayload(File(project))
    problems.forEach { System.err.println("ERROR $it") }
    if (problems.isNotEmpty()) exitProcess(1)
    println("OK compileModPayload")
}
