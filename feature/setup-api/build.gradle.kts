plugins {
    alias(libs.plugins.cmp.feature.convention)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
