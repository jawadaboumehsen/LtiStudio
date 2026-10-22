/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
import org.gradle.process.CommandLineArgumentProvider

plugins {
    kotlin("jvm") version "2.4.10"
}

kotlin {
    jvmToolchain(21)
}

val patchmodVersion = providers.gradleProperty("patchmodVersion").get()
// Hooks are resolved only for compileModPayload; the SDK author classpath stays free of bytecode tools.
val hooksCompiler by configurations.creating

dependencies {
    implementation("org.ide.lti:patch-mod-sdk:$patchmodVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    hooksCompiler("org.ide.lti:patch-mod-hooks:$patchmodVersion")
}

val modProject = layout.buildDirectory.dir("mod-project")
val stageModFiles by tasks.registering(Sync::class) {
    from(layout.projectDirectory) {
        include("README.md", "LICENSE", "scenarios.json", "assets/**")
    }
    into(modProject)
    // Keep packages and diagnostics when authors regenerate or recheck an existing project.
    preserve {
        include("build/**")
    }
}

val generateModPlan by tasks.registering(JavaExec::class) {
    group = "patchmod"
    description = "Compile Mod.kt and generate the mod manifest, plan and settings schema"
    dependsOn(stageModFiles)
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("org.example.mod.MainKt")
    argumentProviders.add(CommandLineArgumentProvider { listOf(modProject.get().asFile.absolutePath) })
}

listOf(
    "validateMod" to "Validate the generated mod plan and manifest",
    "testMod" to "Run the author scenarios against the generated plan",
    "checkMod" to "Check edits against -PmodDecoded=<dir> and optionally -PmodTree=<dir>",
    "packageMod" to "Package the mod into build/mod-project/build/*.lti-mod.zip",
    "inspectMod" to "Inspect a packaged .lti-mod.zip without executing it",
).forEach { (taskName, taskDescription) ->
    tasks.register<JavaExec>(taskName) {
        group = "patchmod"
        description = taskDescription
        // Inspection consumes an existing archive without compiling or exporting the current sources.
        if (taskName != "inspectMod") dependsOn(generateModPlan)
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass.set("org.ide.lti.sdk.patchmod.cli.PatchModCliKt")
        val modPackage = providers.gradleProperty("modPackage")
        val modDecoded = providers.gradleProperty("modDecoded")
        val modTree = providers.gradleProperty("modTree")
        argumentProviders.add(CommandLineArgumentProvider {
            when {
                taskName == "inspectMod" && modPackage.isPresent ->
                    listOf(taskName, "--package", modPackage.get())
                taskName == "checkMod" -> listOfNotNull(
                    taskName, "--project", modProject.get().asFile.absolutePath,
                    "--decoded", modDecoded.get(),
                    "--tree".takeIf { modTree.isPresent }, modTree.orNull,
                )
                else -> listOf(taskName, "--project", modProject.get().asFile.absolutePath)
            }
        })
    }
}

tasks.register<JavaExec>("compileModPayload") {
    group = "patchmod"
    description = "Compile Java hooks under hooks/ into assets/payload/ using payload.json"
    classpath = hooksCompiler
    mainClass.set("org.ide.lti.sdk.patchmod.hooks.HookCompilerCliKt")
    args("compileModPayload", "--project", layout.projectDirectory.asFile.absolutePath)
}
