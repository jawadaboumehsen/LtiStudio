plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
}

dependencies {
    compileOnly(libs.detekt.api)
    // ruleauthors is scoped to this module only - it lints rules for AUTHORING detekt Rule/
    // RuleSetProvider classes (e.g. prefer Entity.atName over Entity.from(getNameIdentifier)),
    // meaningless for regular application code, so it's not added to the shared convention plugin.
    detektPlugins(libs.detekt.ruleauthors)
}

kotlin {
    jvmToolchain(21)
}

detekt {
    source.setFrom(files("src"))
    config.setFrom(files(rootProject.file("config/detekt/detekt-rules.yml")))
    baseline = rootProject.file("config/detekt/baseline/detekt-rules.xml")
}
