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
    kotlin("jvm") version "2.0.21"
}

repositories {
    mavenCentral()
    flatDir {
        dirs("libs")
    }
}

dependencies {
    // Documented placeholder: place patchmod-sdk.jar in libs/ or configure your repository
    // for full IntelliJ code completion when editing Mod.kt.
    compileOnly(fileTree("libs") { include("*.jar") })
}

// Documented placeholder path for the PatchMod CLI jar
val patchmodCliJar = providers.gradleProperty("patchmodCliJar")
    .orElse(layout.projectDirectory.file("libs/patchmod-cli.jar").asFile.absolutePath)

// The reserved author task names registered as JavaExec wrappers shelling out to PatchMod CLI (compileModPayload
// is below: it runs the separate patch-mod-hooks jar)
listOf(
    "generateModPlan" to "Generate and validate the author mod plan",
    "validateMod" to "Validate the author mod plan and manifest",
    "testMod" to "Run the author scenarios against the resolved plan",
    "packageMod" to "Package the author mod into a .lti-mod.zip",
    "inspectMod" to "Inspect a packaged .lti-mod.zip without executing it",
    "signMod" to "Sign the author mod package",
).forEach { (taskName, taskDescription) ->
    tasks.register<JavaExec>(taskName) {
        group = "patchmod"
        description = taskDescription
        classpath = files(patchmodCliJar)
        mainClass.set("org.ide.lti.sdk.patchmod.cli.PatchModCliKt")
        val modPackage = providers.gradleProperty("modPackage")
        argumentProviders.add(
            CommandLineArgumentProvider {
                val pkg = modPackage.orNull
                if (taskName == "inspectMod" && pkg != null) {
                    listOf(taskName, "--package", pkg)
                } else {
                    listOf(taskName, "--project", layout.projectDirectory.asFile.absolutePath)
                }
            },
        )
    }
}

// Java hooks (hooks/**/*.java) compile with the separate patch-mod-hooks jar, so the SDK itself carries no
// bytecode libraries.
val patchmodHooksJar = providers.gradleProperty("patchmodHooksJar")
    .orElse(layout.projectDirectory.file("libs/patchmod-hooks.jar").asFile.absolutePath)
tasks.register<JavaExec>("compileModPayload") {
    group = "patchmod"
    description = "Compile the Java hooks under hooks/ into smali payloads"
    classpath = files(patchmodHooksJar)
    mainClass.set("org.ide.lti.sdk.patchmod.hooks.HookCompilerCliKt")
    argumentProviders.add(
        CommandLineArgumentProvider { listOf("compileModPayload", "--project", layout.projectDirectory.asFile.absolutePath) },
    )
}
