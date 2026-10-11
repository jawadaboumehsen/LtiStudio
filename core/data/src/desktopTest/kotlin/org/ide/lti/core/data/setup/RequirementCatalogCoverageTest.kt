/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RequirementCatalogCoverageTest {
    @Test
    fun `every tool any build recipe invokes maps to a catalog row`() {
        val buildTools = listOf("cmake", "make", "clang", "protoc", "pkg-config", "git")

        for (tool in buildTools) {
            val found =
                RequirementCatalog.REQUIREMENTS.any { req ->
                    val check = req.check
                    req.id == tool ||
                        req.aptPackages.contains(tool) ||
                        (check is org.ide.lti.core.model.setup.RequirementCheck.Command && check.name == tool)
                }
            assertTrue(found, "Build tool '$tool' is not represented in RequirementCatalog!")
        }
    }

    @Test
    fun `all requirements have valid IDs and non-empty packages`() {
        for (req in RequirementCatalog.REQUIREMENTS) {
            assertTrue(req.id.isNotBlank(), "Requirement id must not be blank")
            assertTrue(req.aptPackages.isNotEmpty(), "Requirement ${req.id} must have aptPackages")
            for (pkg in req.aptPackages) {
                assertTrue(pkg.isNotBlank())
                assertFalse(pkg.contains(" "))
            }
        }
    }

    @Test
    fun `native dev packages and host libraries are populated`() {
        assertTrue(RequirementCatalog.NATIVE_DEV_PACKAGES.isNotEmpty())
        assertTrue(RequirementCatalog.HOST_LIBRARY_PACKAGES.containsAll(RequirementCatalog.NATIVE_DEV_PACKAGES))
        assertTrue(RequirementCatalog.HOST_LIBRARY_PACKAGES.contains("pkg-config"))
        assertTrue(RequirementCatalog.HOST_LIBRARY_PACKAGES.contains("protobuf-compiler"))
    }

    @Test
    fun `no requirement has id java-server or openjdk-21-jdk and jdk-17 remains`() {
        assertFalse(
            RequirementCatalog.REQUIREMENTS.any { it.id == "java-server" || it.aptPackages.contains("openjdk-21-jdk") },
            "RequirementCatalog must not contain java-server or openjdk-21-jdk",
        )
        assertTrue(
            RequirementCatalog.REQUIREMENTS.any { it.id == "jdk-17" && it.aptPackages.contains("openjdk-17-jdk") },
            "jdk-17 (openjdk-17-jdk) must remain in RequirementCatalog",
        )
    }

    @Test
    fun `missing JDK 21 never fails packages check`() {
        val packages = RequirementCatalog.REQUIREMENTS.flatMap { it.aptPackages }
        assertFalse(packages.contains("openjdk-21-jdk"), "Packages to install must not include openjdk-21-jdk")
    }
}
