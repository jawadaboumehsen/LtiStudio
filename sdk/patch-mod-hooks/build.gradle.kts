/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.process.CommandLineArgumentProvider

// Compiles a mod's Java hooks to smali (HookCompiler). Its own artifact, so the patch-mod SDK itself stays free of
// bytecode libraries (SdkBoundaryTest): only an author project that has hooks adds patch-mod-hooks.
plugins {
    alias(libs.plugins.kmp.library.convention)
    id("kotlinx-serialization")
    `maven-publish`
}

group = "org.ide.lti"

kotlin {
    sourceSets {
        desktopMain.dependencies {
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.dex2jar)
            implementation(libs.r8)
            implementation(libs.baksmali)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        groupId = "org.ide.lti"
        if (name == "desktop") {
            artifactId = "patch-mod-hooks"
        }
    }
}

val desktopTarget = kotlin.targets.getByName("desktop") as org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget
val mainCompilation = desktopTarget.compilations.getByName("main")

// The reserved author task: `./gradlew :sdk:patch-mod-hooks:compileModPayload -PmodProject=<dir>`.
tasks.register<JavaExec>("compileModPayload") {
    group = "sdk"
    description = "Compile the Java hooks under hooks/ into smali payloads"
    classpath = mainCompilation.output.allOutputs + mainCompilation.runtimeDependencyFiles
    mainClass.set("org.ide.lti.sdk.patchmod.hooks.HookCompilerCliKt")
    val modProject = providers.gradleProperty("modProject")
        .orElse(rootProject.layout.projectDirectory.dir("sdk/patch-mod/examples/compiled-hook").asFile.absolutePath)
    argumentProviders.add(CommandLineArgumentProvider { listOf("compileModPayload", "--project", modProject.get()) })
}
