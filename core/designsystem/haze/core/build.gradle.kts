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
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.compose.compiler)
}

kotlin {
    applyDefaultHierarchyTemplate()

    jvm("desktop")

    jvmToolchain(21)

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(projects.core.designsystem.haze.utils)
                implementation(libs.jb.composeLifeCycle)
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.animation)
                implementation(compose.ui)
            }
        }

        val skikoMain by creating {
            dependsOn(commonMain)
        }

        // Haze's own jvmMain: the default source set of their unnamed jvm() target - we name
        // ours jvm("desktop"), so it isn't auto-wired the same way; wire it in as an extra parent.
        val jvmMain by creating {
            dependsOn(commonMain)
        }

        val desktopMain by getting {
            dependsOn(skikoMain)
            dependsOn(jvmMain)
        }
    }

    compilerOptions {
        optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
        optIn.add("dev.chrisbanes.haze.InternalHazeApi")
    }
}
