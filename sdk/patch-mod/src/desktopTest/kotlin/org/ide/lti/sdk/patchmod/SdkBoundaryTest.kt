/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.sdk.patchmod

import java.io.File
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.fail

private const val MIN_EXPECTED_CLASSPATH_ENTRIES = 5

class SdkBoundaryTest {

    @Test
    fun testRuntimeClasspathHasNoForbiddenLibraries() {
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
        val entries = expandPathingJars(System.getProperty("java.class.path").split(File.pathSeparator))
        // A single pathing jar would make the scan below match nothing and pass vacuously.
        if (entries.size < MIN_EXPECTED_CLASSPATH_ENTRIES) {
            fail(
                "Classpath scan saw only ${entries.size} entries; expected a real classpath:\n" +
                    entries.joinToString("\n"),
            )
        }
        val offending = entries.filter { entry ->
            val name = File(entry).name
            forbidden.any { name.contains(it) }
        }
        if (offending.isNotEmpty()) {
            fail(
                "Classpath contains forbidden libraries:\n" +
                    offending.joinToString("\n") { "  $it" },
            )
        }
    }

    @Test
    fun testNoRuntimeModuleDependsOnSdk() {
        val repoRoot = locateRepoRoot()
        val runtimeDirs = listOf("core", "feature", "lti-shared", "lti-desktop")
        val offenders = mutableListOf<String>()

        for (dir in runtimeDirs) {
            val root = File(repoRoot, dir)
            if (!root.exists()) continue
            root.walkTopDown()
                .filter {
                    it.name == "build.gradle.kts" &&
                        "build" !in it.relativeTo(repoRoot).path.split(File.separator)
                }
                .forEach { file ->
                    val pattern = Regex("""projects\.sdk\.patchMod\b|":sdk:patch-mod"""")
                    val hasCliDep = pattern.containsMatchIn(file.readText())
                    if (hasCliDep) {
                        offenders.add(file.relativeTo(repoRoot).path)
                    }
                }
        }

        if (offenders.isNotEmpty()) {
            fail(
                "Runtime modules must not depend on :sdk:patch-mod. Offenders:\n" +
                    offenders.joinToString("\n") { "  $it" },
            )
        }
    }

    @Test
    fun testFeaturePluginsHasNoSiblingFeatureDependency() {
        val repoRoot = locateRepoRoot()
        val buildFile = File(repoRoot, "feature/plugins/build.gradle.kts")
        val text = buildFile.readText()

        val violations = mutableListOf<String>()
        if (text.contains("projects.feature.")) {
            violations.add("contains 'projects.feature.' reference")
        }
        if (text.contains(":feature:")) {
            violations.add("contains ':feature:' string")
        }
        if (violations.isNotEmpty()) {
            fail(
                "feature/plugins/build.gradle.kts must not depend on sibling feature modules:\n" +
                    violations.joinToString("\n") { "  $it" },
            )
        }
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
}
