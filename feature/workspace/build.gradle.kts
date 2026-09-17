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
    alias(libs.plugins.cmp.feature.convention)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.data)
            implementation(projects.core.cli)
            implementation(projects.core.common)
            implementation(projects.tool.core)
            implementation(libs.filekit.core)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(libs.kotlinx.serialization.json)
            implementation(compose.materialIconsExtended)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
        }
        desktopTest.dependencies {
            implementation(projects.core.testing)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
        }
    }
}

tasks.withType<Test>().configureEach {
    testLogging {
        events("passed", "skipped", "failed", "standardOut", "standardError")
        showStandardStreams = true
    }
    val updateScreenshots = (project.findProperty("updateScreenshots") as String?)
        ?: System.getProperty("updateScreenshots")
    if (updateScreenshots != null) {
        systemProperty("updateScreenshots", updateScreenshots)
    }
}

tasks.register("test") {
    dependsOn("desktopTest")
}
