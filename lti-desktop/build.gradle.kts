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
import java.security.MessageDigest

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
            implementation(projects.feature.setup)

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
            appResourcesRootDir.set(layout.buildDirectory.dir("bundledServer"))

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

val stageBundledServer by tasks.registering {
    dependsOn(gradle.includedBuild("ltirom-server").task(":installDist"))

    val bundledServerDir = layout.buildDirectory.dir("bundledServer")
    outputs.dir(bundledServerDir)

    doLast {
        val serverInstallDir = File(project.rootDir.parentFile, "LtiRomServer/build/install/ltirom-server")
        val targetDir = bundledServerDir.get().asFile
        val targetServerDir = File(targetDir, "ltirom-server")

        if (targetDir.exists()) {
            targetDir.deleteRecursively()
        }
        targetServerDir.mkdirs()

        serverInstallDir.copyRecursively(targetServerDir, overwrite = true)

        val binDir = File(targetServerDir, "bin")
        binDir.listFiles()?.forEach { file ->
            if (!file.name.endsWith(".bat")) {
                file.setExecutable(true, false)
            }
        }

        val gitCommit = runCatching {
            val proc = ProcessBuilder("git", "rev-parse", "--short", "HEAD")
                .directory(project.rootDir)
                .start()
            val text = proc.inputStream.bufferedReader().readText().trim()
            proc.waitFor()
            text.ifEmpty { "dev" }
        }.getOrDefault("dev")

        val copiedVersionFile = File(targetServerDir, "server-version.txt")
        val versionText = if (copiedVersionFile.exists()) {
            copiedVersionFile.readText().trim()
        } else {
            "1.0.0-$gitCommit"
        }

        val rootVersionFile = File(targetDir, "server-version.txt")
        rootVersionFile.writeText(versionText)
        if (!copiedVersionFile.exists()) {
            copiedVersionFile.writeText(versionText)
        }

        val checksumsFile = File(targetDir, "checksums.txt")
        val md = MessageDigest.getInstance("SHA-256")
        val lines = mutableListOf<String>()

        targetDir.walkTopDown().filter { it.isFile && it != checksumsFile }.sortedBy { it.relativeTo(targetDir).path }.forEach { file ->
            val relPath = file.relativeTo(targetDir).path.replace('\\', '/')
            val digest = md.digest(file.readBytes())
            val hex = digest.joinToString("") { b -> b.toUByte().toString(16).padStart(2, '0') }
            lines.add("$hex  $relPath")
        }
        checksumsFile.writeText(lines.joinToString("\n") + "\n")
    }
}

tasks.matching { it.name == "prepareAppResources" }.configureEach {
    if (this is Sync) {
        from(stageBundledServer)
    }
}

tasks.matching { it.name == "run" || it.name == "createDistributable" || it.name.startsWith("package") }.configureEach {
    dependsOn(stageBundledServer)
}

tasks.matching { it.name == "run" }.configureEach {
    if (this is JavaExec) {
        jvmArgs("-Xss8m")
        systemProperty("skiko.renderApi", "DIRECT3D")
    }
}
