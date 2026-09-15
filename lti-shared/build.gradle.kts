/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */

plugins {
    alias(libs.plugins.kmp.library.convention)
    alias(libs.plugins.cmp.feature.convention)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
}

kotlin {
    jvm("desktop")


    sourceSets {
        commonMain.dependencies {
            // Core Modules
            implementation(projects.core.common)
            implementation(projects.core.model)
            implementation(projects.core.domain)
            api(projects.core.data)
            api(projects.core.network)

            implementation(projects.feature.home)
            implementation(projects.feature.profile)
            implementation(projects.feature.settings)
            implementation(projects.feature.setup)
            implementation(projects.feature.workspace)
            implementation(projects.feature.configuration)
            implementation(projects.feature.plugins)


            //put your multiplatform dependencies here
            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.components.resources)
            implementation(libs.window.size)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }

        desktopMain.dependencies {
            // Desktop specific dependencies
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.common)
        }

        desktopTest.dependencies {
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.junit)
            implementation(libs.koin.test)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
}

tasks.register("test") {
    dependsOn("desktopTest")
}

compose.resources {
    publicResClass = true
    generateResClass = always
    packageOfResClass = "org.ide.lti.shared.generated.resources"
}
