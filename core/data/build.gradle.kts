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
            api(projects.core.common)
            api(projects.core.datastore)
            api(projects.core.model)
            implementation(projects.core.domain)
            implementation(projects.core.network)
            implementation(projects.core.analytics)
            implementation(libs.kermit.logging)
            implementation(libs.kotlinx.serialization.json)
        }

        desktopMain.dependencies {
            implementation(projects.tool.api)
            implementation(projects.tool.client)
            implementation(projects.sdk.patchModRuntime)
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
            }
        }

        commonTest.dependencies {
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}