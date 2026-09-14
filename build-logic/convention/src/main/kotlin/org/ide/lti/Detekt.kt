package org.ide.lti

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.named

/**
 * Configures the Detekt plugin with the [extension] configuration.
 * This includes setting the JVM target to 17 and enabling all reports.
 * Additionally, it adds the `detekt-formatting` and `compose-rules-detekt` plugins.
 * @see DetektExtension
 * @see Detekt
 */
internal fun Project.configureDetekt(extension: DetektExtension) = extension.apply {
    source.setFrom(files("src"))
    baseline.set(
        rootProject.file(
            "config/detekt/baseline/${project.path.replace(":", "-").removePrefix("-")}.xml",
        ),
    )
    tasks.named<Detekt>("detekt") {
        jvmTarget.set("17")
        reports {
            html { required.set(true) }
            sarif { required.set(true) }
            markdown { required.set(true) }
        }
    }
    dependencies {
        "detektPlugins"(libs.findLibrary("detekt-formatting").get())
        "detektPlugins"(libs.findLibrary("compose-rules-detekt").get())
        "detektPlugins"(project(":detekt-rules"))
    }
}