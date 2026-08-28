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
            implementation(libs.kotlinx.serialization.json)
            api(libs.jb.composeRuntime)
        }

    }
}