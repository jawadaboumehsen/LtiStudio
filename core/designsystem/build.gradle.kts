/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
plugins {
    alias(libs.plugins.kmp.library.convention)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.hazeGlassMaterial3)
            api(projects.hazeBlurMaterial3)
            api(projects.hazeMaterials)
            implementation(libs.coil.kt.compose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.materialIconsExtended)
            implementation(compose.ui)
            implementation(compose.uiUtil)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)

            implementation(libs.composeIcons.cssGg)
            implementation(libs.composeIcons.weatherIcons)
            implementation(libs.composeIcons.evaIcons)
            implementation(libs.composeIcons.feather)
            implementation(libs.composeIcons.fontAwesome)
            implementation(libs.composeIcons.lineAwesome)
            implementation(libs.composeIcons.linea)
            implementation(libs.composeIcons.octicons)
            implementation(libs.composeIcons.simpleIcons)
            implementation(libs.composeIcons.tablerIcons)

            api(libs.back.handler)
            api(libs.window.size)
            api(libs.capsule)
            api("com.materialkolor:material-kolor:5.0.0")
            api(libs.kermit.logging)
        }
        desktopTest.dependencies {
            implementation(projects.core.testing)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "org.ide.lti.core.designsystem.generated.resources"
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    compilerOptions {
        freeCompilerArgs.add("-Xskip-metadata-version-check")
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
    // Forward -PupdateScreenshots=true / -DupdateScreenshots=true to the forked test JVM -
    // neither a Gradle project property nor a `-D` on the Gradle CLI reaches the test worker
    // process on its own, only environment variables do by default.
    val updateScreenshots = (project.findProperty("updateScreenshots") as String?)
        ?: System.getProperty("updateScreenshots")
    if (updateScreenshots != null) {
        systemProperty("updateScreenshots", updateScreenshots)
    }
}

tasks.register("test") {
    dependsOn("desktopTest")
}
