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
import java.io.InputStream
import java.net.URI
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
            implementation(projects.core.domain)
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
            implementation(projects.core.data)
            implementation(projects.tool.client)
            implementation(compose.desktop.uiTestJUnit4)
            implementation(libs.jb.lifecycleViewmodel)
        }
    }
}

val appName: String = libs.versions.packageName.get()
val packageNameSpace: String = libs.versions.packageNamespace.get()
val appVersion: String = libs.versions.packageVersion.get()

compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs +=
            listOf(
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

val serverJreUrl = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.6%2B7/OpenJDK21U-jre_x64_linux_hotspot_21.0.6_7.tar.gz"
val serverJreSha256 = "7fc9d6837da5fa1f12e0f41901fd70a73154914b8c8ecbbcad2d44176a989937"

abstract class DownloadServerJreTask : DefaultTask() {
    @get:Input
    abstract val url: Property<String>

    @get:Input
    abstract val expectedSha256: Property<String>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun download() {
        val file = outputFile.get().asFile
        val expected = expectedSha256.get()
        val downloadUrl = url.get()
        if (file.exists()) {
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            val hex = digest.joinToString("") { b -> "%02x".format(b) }
            if (hex.equals(expected, ignoreCase = true)) {
                return
            }
            file.delete()
        }
        file.parentFile.mkdirs()
        println("Downloading Temurin 21 JRE linux-x64 from $downloadUrl...")
        URI(downloadUrl).toURL().openStream().use { input: InputStream ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
        val hex = digest.joinToString("") { b -> "%02x".format(b) }
        check(hex.equals(expected, ignoreCase = true)) {
            file.delete()
            "SHA-256 mismatch for server JRE! Expected $expected but got $hex"
        }
    }
}

abstract class StageBundledServerTask : DefaultTask() {
    @get:InputDirectory
    abstract val serverInstallDir: DirectoryProperty

    @get:InputFile
    abstract val serverVersionFile: RegularFileProperty

    @get:InputFile
    abstract val jreTarGzFile: RegularFileProperty

    @get:OutputDirectory
    abstract val bundledServerDir: DirectoryProperty

    @TaskAction
    fun stage() {
        val versionFile = serverVersionFile.get().asFile
        check(versionFile.isFile) { "Missing $versionFile: run :installDist in LtiRomServer first." }
        val targetDir = bundledServerDir.get().asFile
        val targetServerDir = File(targetDir, "ltirom-server")
        targetDir.deleteRecursively()
        targetServerDir.mkdirs()
        serverInstallDir.get().asFile.copyRecursively(targetServerDir, overwrite = true)
        File(targetServerDir, "server-version.txt").writeText(versionFile.readText().trim())

        val runtimeDir = File(targetServerDir, "runtime")
        runtimeDir.mkdirs()
        val jreFile = jreTarGzFile.get().asFile
        jreFile.copyTo(File(runtimeDir, "jre.tar.gz"), overwrite = true)

        val checksumsFile = File(targetServerDir, "checksums.txt")
        val lines =
            targetServerDir.walkTopDown()
                .filter { it.isFile && it != checksumsFile }
                .sortedBy { it.relativeTo(targetServerDir).invariantSeparatorsPath }
                .map { file ->
                    val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                    val hex = digest.joinToString("") { b -> "%02x".format(b) }
                    "$hex  ${file.relativeTo(targetServerDir).invariantSeparatorsPath}"
                }
        checksumsFile.writeText(lines.joinToString("\n", postfix = "\n"))
    }
}

val downloadServerJre =
    tasks.register<DownloadServerJreTask>("downloadServerJre") {
        url.set(serverJreUrl)
        expectedSha256.set(serverJreSha256)
        outputFile.set(layout.buildDirectory.file("downloads/server-jre.tar.gz"))
    }

/**
 * Stages the build service into the app resources as `ltirom-server/` with `server-version.txt`,
 * `runtime/jre.tar.gz` and `checksums.txt` (relative to that folder, `sha256sum -c` format).
 * The installer in WSL verifies the copy against these checksums before activating it.
 */
val stageBundledServer =
    tasks.register<StageBundledServerTask>("stageBundledServer") {
        dependsOn(gradle.includedBuild("ltirom-server").task(":installDist"))
        dependsOn(downloadServerJre)

        val serverProjectDir = rootDir.parentFile.resolve("LtiRomServer")
        serverInstallDir.set(serverProjectDir.resolve("build/install/ltirom-server"))
        serverVersionFile.set(serverProjectDir.resolve("build/generated/resources/version/server-version.txt"))
        jreTarGzFile.set(downloadServerJre.flatMap { it.outputFile })
        bundledServerDir.set(layout.buildDirectory.dir("bundledServer"))
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

tasks.named<Test>("jvmTest") {
    filter {
        excludeTestsMatching("org.ide.lti.desktop.realhost.*")
    }
}

val realHostTest by tasks.registering(Test::class) {
    description = "Runs real-host tests against the real build server on Linux or WSL"
    group = "verification"
    dependsOn(stageBundledServer)

    testClassesDirs = tasks.named<Test>("jvmTest").get().testClassesDirs
    classpath = tasks.named<Test>("jvmTest").get().classpath

    filter {
        includeTestsMatching("org.ide.lti.desktop.realhost.*")
    }

    val isWindows = System.getProperty("os.name").lowercase().contains("windows")
    val defaultHost = if (isWindows) "wsl" else ""
    val defaultDistro = if (isWindows) "LtiRomTest" else ""

    val hostVal =
        providers.environmentVariable("LTI_REAL_HOST").orNull
            ?: providers.systemProperty("lti.real.host").orNull
            ?: System.getenv("LTI_REAL_HOST")
            ?: System.getProperty("lti.real.host")
            ?: defaultHost

    val distroVal =
        providers.environmentVariable("LTI_TEST_DISTRO").orNull
            ?: providers.systemProperty("lti.test.distro").orNull
            ?: System.getenv("LTI_TEST_DISTRO")
            ?: System.getProperty("lti.test.distro")
            ?: defaultDistro

    if (hostVal.isNotBlank()) {
        systemProperty("lti.real.host", hostVal)
        environment("LTI_REAL_HOST", hostVal)
    }
    if (distroVal.isNotBlank()) {
        systemProperty("lti.test.distro", distroVal)
        environment("LTI_TEST_DISTRO", distroVal)
    }
}
