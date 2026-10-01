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
    id("kotlinx-serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(libs.filekit.core)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            api(libs.coil.kt)
            api(libs.coil.core)
            api(libs.coil.svg)
            api(libs.coil.network.ktor)
            api(libs.kermit.logging)
            api(libs.squareup.okio)
            api(libs.jb.kotlin.stdlib)
            api(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }

        desktopMain.dependencies {
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.kotlin.reflect)
        }
    }
}
