/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod

import org.w3c.dom.Element
import java.io.File
import java.util.jar.JarFile
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

class StandaloneDistributionTest {

    @Test
    fun testPublishedPomDependenciesAndForbiddenArtifacts() {
        val repoRoot = locateRepoRoot()
        val sdkDir = File(repoRoot, "sdk/patch-mod")
        val localMavenDir = File(sdkDir, "build/local-maven")

        if (!localMavenDir.exists()) {
            fail(
                "Local maven repository at '${localMavenDir.path}' is absent. " +
                    "Run ':sdk:patch-mod:publishAllPublicationsToLocalMavenRepository' first.",
            )
        }

        val pomFiles = localMavenDir.walkTopDown()
            .filter { it.isFile && it.extension == "pom" && it.name.startsWith("patch-mod-sdk") }
            .toList()

        if (pomFiles.isEmpty()) {
            fail(
                "No published POM found for 'patch-mod-sdk' in '${localMavenDir.path}'. " +
                    "Run ':sdk:patch-mod:publishAllPublicationsToLocalMavenRepository' first.",
            )
        }

        // local-maven accumulates one timestamped version per build; assert against the newest.
        val pomFile = pomFiles.maxBy { it.lastModified() }
        val dependencies = parsePomDependencies(pomFile)

        val hasDomain = dependencies.any { dep ->
            (dep.artifactId.contains("domain") || dep.artifactId.contains("core-domain")) &&
                (dep.groupId.contains("core") || dep.artifactId.contains("core") || dep.groupId == "LtiRomGui.core")
        }
        val hasModel = dependencies.any { dep ->
            (dep.artifactId.contains("model") || dep.artifactId.contains("core-model")) &&
                (dep.groupId.contains("core") || dep.artifactId.contains("core") || dep.groupId == "LtiRomGui.core")
        }

        assertTrue(
            hasDomain,
            "Published POM '${pomFile.name}' must list core-domain as a dependency. Found: $dependencies",
        )
        assertTrue(
            hasModel,
            "Published POM '${pomFile.name}' must list core-model as a dependency. Found: $dependencies",
        )

        val forbiddenPomTokens = listOf(
            "feature",
            "ui",
            "designsystem",
            "compose",
            "smali",
            "baksmali",
            "apktool",
            "dexlib2",
            "multidexlib2",
        )
        val forbiddenPomMatches = dependencies.filter { dep ->
            forbiddenPomTokens.any { token ->
                val inArtifact = dep.artifactId.contains(token, ignoreCase = true)
                val inGroup = dep.groupId.contains(token, ignoreCase = true) &&
                    !dep.groupId.contains("LtiRomGui", ignoreCase = true)
                inArtifact || inGroup
            }
        }
        assertTrue(
            forbiddenPomMatches.isEmpty(),
            "Published POM must not list any feature/UI/bytecode artifact. Violations: $forbiddenPomMatches",
        )
    }

    @Test
    fun testRecordSdkDependencyClosureAndVerifyClasspath() {
        val repoRoot = locateRepoRoot()
        val sdkDir = File(repoRoot, "sdk/patch-mod")
        val reportsDir = File(sdkDir, "build/reports")
        reportsDir.mkdirs()
        val closureFile = File(reportsDir, "sdk-dependency-closure.txt")

        val rawEntries = System.getProperty("java.class.path").split(File.pathSeparator)
        val entries = expandPathingJars(rawEntries)

        val forbidden = listOf(
            "revanced",
            "morphe",
            "multidexlib2",
            "dexlib2",
            "smali",
            "baksmali",
            "apktool",
            "compose-",
            "feature-",
            "lti-shared",
            "lti-desktop",
        )
        val offending = entries.filter { entry ->
            val name = File(entry).name
            forbidden.any { name.contains(it) }
        }
        assertTrue(
            offending.isEmpty(),
            "Classpath contains forbidden libraries:\n" + offending.joinToString("\n") { "  $it" },
        )

        val closureJarNames = entries
            .map { File(it).name }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        closureFile.writeText(closureJarNames.joinToString("\n") + "\n")
        assertTrue(closureFile.exists() && closureFile.length() > 0, "Closure file must be non-empty")
    }

    private fun parsePomDependencies(pomFile: File): List<PomDependency> {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(pomFile)
        val dependencyNodes = doc.getElementsByTagName("dependency")
        val result = mutableListOf<PomDependency>()
        for (i in 0 until dependencyNodes.length) {
            val element = dependencyNodes.item(i) as? Element ?: continue
            val parent = element.parentNode?.nodeName
            val grandParent = element.parentNode?.parentNode?.nodeName
            if (parent == "dependencies" && grandParent == "project") {
                val groupId = element.getElementsByTagName("groupId").item(0)?.textContent.orEmpty().trim()
                val artifactId = element.getElementsByTagName("artifactId").item(0)?.textContent.orEmpty().trim()
                val version = element.getElementsByTagName("version").item(0)?.textContent.orEmpty().trim()
                val scope = element.getElementsByTagName("scope").item(0)?.textContent.orEmpty().trim()
                result.add(PomDependency(groupId, artifactId, version, scope))
            }
        }
        return result
    }

    private fun expandPathingJars(entries: List<String>): List<String> = entries.flatMap { entry ->
        val file = File(entry)
        if (!file.isFile || !file.name.endsWith(".jar")) return@flatMap listOf(entry)
        val classPath = JarFile(file).use { it.manifest?.mainAttributes?.getValue("Class-Path") }
        if (classPath.isNullOrBlank()) {
            listOf(entry)
        } else {
            classPath.split(' ').filter { it.isNotBlank() }.map { File(file.parentFile, it).path }
        }
    }

    private fun locateRepoRoot(): File {
        var dir = File(System.getProperty("user.dir"))
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("Could not locate repo root (settings.gradle.kts not found)")
        }
        return dir
    }

    private data class PomDependency(
        val groupId: String,
        val artifactId: String,
        val version: String,
        val scope: String,
    )
}
