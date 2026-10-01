/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
plugins {
    alias(libs.plugins.kmp.library.convention)
    id("kotlinx-serialization")
}

group = "org.ide.lti"
version = if (project.version.toString().isNotEmpty() && project.version.toString() != "unspecified") {
    project.version.toString()
} else {
    "0.1.0-SNAPSHOT"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(libs.kotlinx.serialization.json)
        }
        desktopMain.dependencies {
            // Uses desktop JVM APIs (ZipFile, ProcessBuilder, Path)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
