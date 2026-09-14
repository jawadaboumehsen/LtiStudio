package org.ide.lti

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configure the Kotlin Multiplatform plugin for Desktop/JVM target only.
 * This includes JVM (desktop) target configuration.
 * @see KotlinMultiplatformExtension
 * @see configure
 */
@OptIn(ExperimentalKotlinGradlePluginApi::class)
internal fun Project.configureKotlinMultiplatform() {
    extensions.configure<KotlinMultiplatformExtension> {
        applyDefaultHierarchyTemplate()

        jvmToolchain(21)

        jvm("desktop")

        sourceSets.all {
            languageSettings {
                optIn("kotlin.ExperimentalStdlibApi")
            }
        }
        compilerOptions {
            freeCompilerArgs.add("-Xexpect-actual-classes")
        }
    }
}
