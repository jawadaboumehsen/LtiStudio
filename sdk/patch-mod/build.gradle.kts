/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
import org.gradle.process.CommandLineArgumentProvider

plugins {
    alias(libs.plugins.kmp.library.convention)
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(libs.kotlinx.serialization.json)
        }
        desktopMain.dependencies {
            implementation(projects.core.data)
            implementation(libs.clikt)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

val desktopTarget = kotlin.targets.getByName("desktop") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
val mainCompilation = desktopTarget.compilations.getByName("main")
val defaultModProject = layout.projectDirectory.dir("examples/file-property").asFile.absolutePath

// The reserved sdk-api.md author task names. signMod is deliberately absent
// until its implementation lands, rather than registered as a task that does nothing.
listOf(
    "compileModPayload" to "Compile author hook Kotlin sources into smali payloads",
    "generateModPlan" to "Generate and validate the author mod plan",
    "validateMod" to "Validate the author mod plan and manifest",
    "testMod" to "Run the author scenarios against the resolved plan",
    "packageMod" to "Package the author mod into a .lti-mod.zip",
    "inspectMod" to "Inspect a packaged .lti-mod.zip without executing it",
).forEach { (taskName, taskDescription) ->
    tasks.register<JavaExec>(taskName) {
        group = "sdk"
        description = taskDescription
        classpath = mainCompilation.output.allOutputs + mainCompilation.runtimeDependencyFiles
        mainClass.set("org.ide.lti.sdk.patchmod.cli.PatchModCliKt")
        val modProject = providers.gradleProperty("modProject").orElse(defaultModProject)
        val modPackage = providers.gradleProperty("modPackage")
        argumentProviders.add(
            CommandLineArgumentProvider {
                val pkg = modPackage.orNull
                if (taskName == "inspectMod" && pkg != null) {
                    listOf(taskName, "--package", pkg)
                } else {
                    listOf(taskName, "--project", modProject.get())
                }
            },
        )
    }
}
