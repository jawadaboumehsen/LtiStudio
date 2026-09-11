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

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":detekt-rules")
include(":backdrop")
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

include(":feature:home")
include(":feature:profile")
include(":feature:settings")
include(":feature:setup")
include(":feature:workspace")

include(":catalog")

include(":tool:core")
include(":tool:adapters")
include(":tool:client")
include(":tool:codegen")
