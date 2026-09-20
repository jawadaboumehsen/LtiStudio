/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/mobile-wallet/blob/master/LICENSE.md
 */
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.jetbrainsCompose)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvm {
        compilerOptions {
            freeCompilerArgs.add("-Xskip-metadata-version-check")
        }
    }

    jvmToolchain(21)

    sourceSets {
        jvmMain.dependencies {
            implementation(projects.core.common)
            implementation(projects.core.designsystem)

            implementation(projects.ltiShared)

            implementation(compose.material3)
            implementation(compose.foundation)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(compose.desktop.currentOs)
            implementation(libs.jb.kotlin.stdlib)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.nucleus.application)
            implementation(libs.nucleus.decorated.window)
            implementation(libs.nucleus.fs.watcher)
            implementation(libs.composenativetray)
            implementation("org.slf4j:slf4j-simple:2.0.16")
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(projects.core.domain)
            implementation(projects.tool.client)
        }
    }
}

val appName: String = libs.versions.packageName.get()
val packageNameSpace: String = libs.versions.packageNamespace.get()
val appVersion: String = libs.versions.packageVersion.get()




compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs += listOf(
            "-Xss4m",
            "-Dskiko.renderApi=DIRECT3D",
        )
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = appName
            packageVersion = appVersion
            description = "Desktop Application"
            copyright = "© 2024 Mifos Initiative. All rights reserved."
            vendor = "Mifos Initiative"
            licenseFile.set(project.file("../LICENSE"))
            includeAllModules = true

            macOS {
                bundleID = packageNameSpace
                dockName = appName
                iconFile.set(project.file("icons/ic_launcher.icns"))
                notarization {
                    val providers = project.providers
                    appleID.set(providers.environmentVariable("NOTARIZATION_APPLE_ID"))
                    password.set(providers.environmentVariable("NOTARIZATION_PASSWORD"))
                    teamID.set(providers.environmentVariable("NOTARIZATION_TEAM_ID"))
                }
            }

            windows {
                menuGroup = appName
                shortcut = true
                dirChooser = true
                perUserInstall = true
                iconFile.set(project.file("icons/ic_launcher.ico"))
            }

            linux {
                modules("jdk.security.auth")
                iconFile.set(project.file("icons/ic_launcher.png"))
            }
        }
        buildTypes.release.proguard {
            configurationFiles.from(file("compose-desktop.pro"))
            obfuscate.set(true)
            optimize.set(true)
        }
    }
}

tasks.matching { it.name == "run" }.configureEach {
    if (this is JavaExec) {
        jvmArgs("-Xss8m")
        systemProperty("skiko.renderApi", "DIRECT3D")
    }
}

