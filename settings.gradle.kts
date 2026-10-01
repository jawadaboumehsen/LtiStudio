pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()

    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        google()
        mavenCentral()
        maven("https://www.jitpack.io")
        maven("https://plugins.gradle.org/m2/")
    }
}

plugins {
    id("org.ajoberstar.reckon.settings") version("0.18.3")
}

extensions.configure<org.ajoberstar.reckon.gradle.ReckonExtension> {
    setDefaultInferredScope("patch")
    stages("beta", "rc", "final")
    setScopeCalc { java.util.Optional.of(org.ajoberstar.reckon.core.Scope.PATCH) }
    setScopeCalc(calcScopeFromProp().or(calcScopeFromCommitMessages()))
    setStageCalc(calcStageFromProp())
    setTagWriter { it.toString() }
}

rootProject.name = "LtiRomGui"

val serverSettings = file("../LtiRomServer/settings.gradle.kts")
if (!serverSettings.exists()) {
    throw GradleException("LtiRomGui must be checked out next to LtiRomServer (expected ../LtiRomServer). See LtiRomGui/CLAUDE.md → Build layout.")
}
includeBuild("../LtiRomServer") {
    name = "ltirom-server"
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":detekt-rules")
include(":haze-utils")
include(":haze")
include(":haze-blur")
include(":haze-glass")
include(":haze-glass-material3")
include(":haze-blur-material3")
include(":haze-materials")
include(":lti-shared")
include(":lti-desktop")

include(":core:data")
include(":core:domain")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":core:common")
include(":core:cli")
include(":core:network")
include(":core:model")
include(":core:analytics")
include(":core:testing")

include(":feature:settings")
include(":feature:setup-api")
include(":feature:setup")
include(":feature:workspace-api")
include(":feature:workspace-create")
include(":feature:workspace-run")
include(":feature:workspace-target")
include(":feature:rom-studio:rom-studio-api")
include(":feature:rom-studio:shell")
include(":feature:rom-studio:stage-acquire")
include(":feature:rom-studio:stage-extract")
include(":feature:rom-studio:stage-assemble")
include(":feature:rom-studio:stage-debloat")
include(":feature:rom-studio:stage-patch")
include(":feature:rom-studio:stage-build")
include(":feature:rom-studio:stage-metadata")
include(":feature:rom-studio:stage-publish")
include(":feature:configuration")
include(":feature:plugins")


include(":tool:api")
include(":tool:runtime")
include(":tool:metadata")
include(":tool:testkit")
include(":tool:adapter:android-device")
include(":tool:adapter:android-package")
include(":tool:adapter:image")
include(":tool:adapter:security")
include(":tool:adapter:publishing")
include(":tool:client")
include(":tool:codegen")

include(":sdk:patch-mod")
include(":sdk:patch-mod-runtime")
