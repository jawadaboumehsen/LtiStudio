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
            implementation(projects.core.data)
            implementation(projects.core.network)
            implementation(projects.core.cli)
            implementation(projects.core.ui)

            implementation(projects.feature.settings)
            api(projects.feature.setup)
            api(projects.feature.workspaceApi)
            implementation(projects.feature.workspaceCreate)
            implementation(projects.feature.workspaceTarget)
            implementation(projects.feature.workspaceRun)
            implementation(projects.feature.configuration)
            implementation(projects.feature.plugins)
            implementation(projects.feature.romStudio.romStudioApi)
            implementation(projects.feature.romStudio.shell)
            implementation(projects.feature.romStudio.stageAcquire)
            implementation(projects.feature.romStudio.stageExtract)
            implementation(projects.feature.romStudio.stageAssemble)
            implementation(projects.feature.romStudio.stageDebloat)
            implementation(projects.feature.romStudio.stagePatch)
            implementation(projects.feature.romStudio.stageBuild)
            implementation(projects.feature.romStudio.stageMetadata)
            implementation(projects.feature.romStudio.stagePublish)


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
            implementation(projects.tool.api)
            implementation(projects.tool.runtime)
            implementation(projects.tool.client)
            implementation(projects.tool.metadata)
            implementation(projects.tool.adapter.androidDevice)
            implementation(projects.tool.adapter.androidPackage)
            implementation(projects.tool.adapter.image)
            implementation(projects.tool.adapter.security)
            implementation(projects.tool.adapter.publishing)
        }

        desktopTest.dependencies {
            implementation(projects.core.testing)
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
