
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.ide.lti.libs
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Plugin that applies the CMP feature plugin and configures it for Desktop/JVM only.
 * This plugin applies the following plugins:
 * - org.ide.lti.kmp.library - Kotlin Multiplatform Library
 * - org.ide.lti.kmp.koin - Koin for Kotlin Multiplatform
 * - org.jetbrains.kotlin.plugin.compose - Kotlin Compose
 * - org.jetbrains.compose - Compose Multiplatform
 * - org.ide.lti.detekt.plugin - Detekt Plugin
 * - org.ide.lti.spotless.plugin - Spotless Plugin
 *
 */
class CMPFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply {
                apply("org.ide.lti.kmp.library")
                apply("org.ide.lti.kmp.koin")
                apply("org.jetbrains.kotlin.plugin.compose")
                apply("org.jetbrains.compose")
                apply("org.ide.lti.detekt.plugin")
                apply("org.ide.lti.spotless.plugin")
            }

            dependencies {
                add("commonMainImplementation", project(":core:ui"))
                add("commonMainImplementation", project(":core:designsystem"))
                add("commonMainImplementation", project(":core:domain"))

                add("commonMainImplementation", libs.findLibrary("koin.compose").get())
                add("commonMainImplementation", libs.findLibrary("koin.compose.viewmodel").get())

                add("commonMainImplementation", libs.findLibrary("jb.composeRuntime").get())
                add("commonMainImplementation", libs.findLibrary("jb.composeViewmodel").get())
                add("commonMainImplementation", libs.findLibrary("jb.lifecycleViewmodel").get())
                add("commonMainImplementation", libs.findLibrary("jb.composeLifeCycle").get())
                add("commonMainImplementation", libs.findLibrary("jb.lifecycleViewmodelSavedState").get())
                add("commonMainImplementation", libs.findLibrary("jb.savedstate").get())
                add("commonMainImplementation", libs.findLibrary("jb.bundle").get())
                add("commonMainImplementation", libs.findLibrary("jb.composeNavigation").get())
                add("commonMainImplementation", libs.findLibrary("kotlinx.collections.immutable").get())
            }


        }
    }
}
