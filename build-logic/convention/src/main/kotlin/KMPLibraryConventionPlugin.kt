
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.ide.lti.configureKotlinMultiplatform
import org.ide.lti.libs

/**
 * Plugin that applies the Kotlin multiplatform plugin and configures it for Desktop/JVM only.
 */
class KMPLibraryConventionPlugin: Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            with(pluginManager) {
                apply("org.jetbrains.kotlin.multiplatform")
                apply("org.ide.lti.kmp.koin")
                apply("org.ide.lti.detekt.plugin")
                apply("org.ide.lti.spotless.plugin")
            }

            configureKotlinMultiplatform()

            dependencies {
                add("commonTestImplementation", libs.findLibrary("kotlin.test").get())
                add("commonTestImplementation", libs.findLibrary("kotlinx.coroutines.test").get())
            }


        }

    }
}
