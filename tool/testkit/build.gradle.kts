plugins {
    kotlin("jvm")
}

dependencies {
    api(projects.tool.api)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.test)
    implementation(libs.kotlin.test)
}
