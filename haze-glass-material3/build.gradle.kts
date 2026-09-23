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
                api(projects.hazeGlass)
                implementation(compose.runtime)
                implementation(compose.material3)
                implementation(compose.ui)
            }
        }
    }

    compilerOptions {
        optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi")
    }
}
