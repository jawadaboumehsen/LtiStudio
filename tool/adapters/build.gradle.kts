plugins {
    kotlin("jvm")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(projects.tool.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.clikt)
    testImplementation(kotlin("test"))
    testImplementation(libs.kotlinx.coroutines.test)
}
