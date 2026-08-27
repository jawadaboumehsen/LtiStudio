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
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm("desktop") {
        withJava()
        compilerOptions {
            freeCompilerArgs.add("-Xcontext-receivers")
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    jvmToolchain(21)

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(projects.core.designsystem)
                implementation(projects.core.ui)
                implementation(projects.core.model)
                implementation(projects.backdrop)

                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.materialIconsExtended)
                implementation(compose.ui)
                implementation(libs.jb.kotlin.stdlib)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.ide.lti.catalog.MainKt"
        nativeDistributions {
            packageName = "LtiRomCatalog"
            packageVersion = "1.0.0"
            description = "LtiRom Component and Token Catalog"
        }
    }
}

tasks.register("test") {
    dependsOn("desktopTest")
}
