plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.tool.api)
    implementation(libs.kotlinx.serialization.json)
}
