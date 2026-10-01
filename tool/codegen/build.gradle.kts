import org.gradle.api.tasks.PathSensitivity

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.tool.api)
    implementation(projects.tool.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}

tasks.register<JavaExec>("extractToolSpecs") {
    group = "tooling"
    description = "Capture tool help and generate draft JSON specifications"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.ltirom.tooling.codegen.CompilerCliKt")
    args("extract")
    providers.gradleProperty("tool").orNull?.let { args(it) }
    dependsOn("classes")
}

tasks.register<JavaExec>("generateToolAdapters") {
    group = "tooling"
    description = "Validate JSON specifications and generate adapters/docs"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.ltirom.tooling.codegen.CompilerCliKt")
    args("generate")
    workingDir = rootDir

    inputs.dir(layout.projectDirectory.dir("../specs"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.dir(layout.projectDirectory.dir("../captured-help"))
        .withPathSensitivity(PathSensitivity.RELATIVE)

    outputs.dir(layout.projectDirectory.dir("../adapter/android-device/src/main/kotlin"))
    outputs.dir(layout.projectDirectory.dir("../adapter/android-package/src/main/kotlin"))
    outputs.dir(layout.projectDirectory.dir("../adapter/image/src/main/kotlin"))
    outputs.dir(layout.projectDirectory.dir("../adapter/security/src/main/kotlin"))
    outputs.dir(layout.projectDirectory.dir("../adapter/publishing/src/main/kotlin"))
    outputs.dir(layout.projectDirectory.dir("../aliases"))
    outputs.dir(layout.projectDirectory.dir("../docs"))
    outputs.file(layout.projectDirectory.file("../metadata/src/main/kotlin/io/ltirom/tooling/core/ToolMetadataRegistry.kt"))

    outputs.cacheIf { true }

    dependsOn("classes")
}

tasks.register<JavaExec>("promoteToolSpec") {
    group = "tooling"
    description = "Promote reviewed draft option metadata into one committed specification"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("io.ltirom.tooling.codegen.CompilerCliKt")
    args("promote")
    providers.gradleProperty("tool").orNull?.let { args(it) }
    dependsOn("classes")
}
