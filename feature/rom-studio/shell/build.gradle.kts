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
    alias(libs.plugins.cmp.feature.convention)
    alias(libs.plugins.roborazzi)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.feature.romStudio.romStudioApi)
            implementation(projects.core.domain)
            implementation(projects.core.model)
            implementation(projects.core.designsystem)
            implementation(projects.core.ui)
            implementation(projects.feature.workspaceTarget)
            implementation(projects.feature.workspaceRun)
            implementation(compose.ui)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            implementation(compose.materialIconsExtended)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)
        }
        commonTest.dependencies {
            implementation(libs.multiplatform.settings)
            implementation(libs.multiplatform.settings.test)
            implementation(projects.core.testing)
        }
        desktopTest.dependencies {
            implementation(projects.core.testing)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(compose.desktop.currentOs)
            implementation(libs.roborazzi.core)
            implementation(libs.roborazzi.compose.desktop)
        }
    }
}
