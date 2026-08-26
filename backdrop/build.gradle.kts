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

    jvm("desktop") {
        withJava()
    }

    jvmToolchain(21)

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-receivers",
            "-Xexpect-actual-classes"
        )
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.ui)
            }
        }

        val skiaMain by creating {
            dependsOn(commonMain)
        }

        val desktopMain by getting {
            dependsOn(skiaMain)
        }
    }
}