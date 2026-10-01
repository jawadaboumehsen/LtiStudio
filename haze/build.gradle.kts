/*
 * Copyright 2025 LtiRomGui
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
                implementation(projects.hazeUtils)
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
