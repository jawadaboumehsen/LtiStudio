/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.domain.plugin.PluginDependencyResolver.lock
import org.ide.lti.core.model.plugin.DependencyRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginDependencyResolverTest {

    private fun samplePackage(
        publisher: String = "org.mifos",
        id: String,
        version: String = "1.0.0",
        digest: String = "sha256:d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1d1",
        dependencies: List<DependencyRef> = emptyList(),
        conflicts: List<DependencyRef> = emptyList(),
    ) = InstalledPackage(
        publisher = publisher,
        id = id,
        version = version,
        contentDigest = digest,
        dependencies = dependencies,
        conflicts = conflicts,
    )

    @Test
    fun testHappyPathTransitiveDependencyOrderedBeforeDependent() {
        val pkgC = samplePackage(id = "pkg-c")
        val pkgB = samplePackage(
            id = "pkg-b",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-c", "1.0.0")),
        )
        val pkgA = samplePackage(
            id = "pkg-a",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-b", "[1.0.0,2.0.0)")),
        )

        val installed = listOf(pkgA, pkgB, pkgC)
        val result = lock(listOf("org.mifos:pkg-a"), installed, emptyList())

        val locked = assertIs<LockResult.Locked>(result)
        val keys = locked.lockfile.entries.map { "${it.publisher}:${it.id}" }
        assertEquals(listOf("org.mifos:pkg-c", "org.mifos:pkg-b", "org.mifos:pkg-a"), keys)
        assertEquals(
            listOf("org.mifos:pkg-a->org.mifos:pkg-b", "org.mifos:pkg-b->org.mifos:pkg-c"),
            locked.lockfile.edges,
        )
    }

    @Test
    fun testRequestedOrderHonouredWhenCompatible() {
        val pkgX = samplePackage(id = "pkg-x")
        val pkgY = samplePackage(id = "pkg-y")
        val pkgZ = samplePackage(id = "pkg-z")

        val installed = listOf(pkgX, pkgY, pkgZ)
        val requested = listOf("org.mifos:pkg-z", "org.mifos:pkg-x", "org.mifos:pkg-y")
        val result = lock(
            listOf("org.mifos:pkg-x", "org.mifos:pkg-y", "org.mifos:pkg-z"),
            installed,
            requested,
        )

        val locked = assertIs<LockResult.Locked>(result)
        val orderedKeys = locked.lockfile.entries.map { "${it.publisher}:${it.id}" }
        assertEquals(requested, orderedKeys)
    }

    @Test
    fun testRequestedOrderViolatesDependency() {
        val pkgDep = samplePackage(id = "dep")
        val pkgRoot = samplePackage(
            id = "root",
            dependencies = listOf(DependencyRef("org.mifos", "dep", "1.0.0")),
        )

        val installed = listOf(pkgRoot, pkgDep)
        // Root requested before its dependency dep
        val requested = listOf("org.mifos:root", "org.mifos:dep")
        val result = lock(listOf("org.mifos:root"), installed, requested)

        val rejected = assertIs<LockResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginDependencyResolver.ORDER_VIOLATES_DEPENDENCY })
    }

    @Test
    fun testUnknownDependency() {
        val pkgA = samplePackage(
            id = "pkg-a",
            dependencies = listOf(DependencyRef("org.mifos", "non-existent", "1.0.0")),
        )
        val result = lock(listOf("org.mifos:pkg-a"), listOf(pkgA), emptyList())

        val rejected = assertIs<LockResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginDependencyResolver.UNKNOWN_DEPENDENCY })
    }

    @Test
    fun testVersionOutOfRangeExactAndInterval() {
        val pkgB = samplePackage(id = "pkg-b", version = "2.0.0")

        // Exact form mismatch
        val pkgExact = samplePackage(
            id = "pkg-exact",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-b", "1.0.0")),
        )
        val resExact = lock(listOf("org.mifos:pkg-exact"), listOf(pkgExact, pkgB), emptyList())
        val rejExact = assertIs<LockResult.Rejected>(resExact)
        assertTrue(rejExact.report.errors.any { it.code == PluginDependencyResolver.VERSION_OUT_OF_RANGE })

        // Interval form mismatch: [1.0.0, 2.0.0) excludes 2.0.0
        val pkgInterval = samplePackage(
            id = "pkg-interval",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-b", "[1.0.0,2.0.0)")),
        )
        val resInterval = lock(listOf("org.mifos:pkg-interval"), listOf(pkgInterval, pkgB), emptyList())
        val rejInterval = assertIs<LockResult.Rejected>(resInterval)
        assertTrue(rejInterval.report.errors.any { it.code == PluginDependencyResolver.VERSION_OUT_OF_RANGE })
    }

    @Test
    fun testMalformedVersionRangeAndMalformedVersion() {
        val pkgBadRange = samplePackage(
            id = "pkg-bad-range",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-b", "^1.0.0")),
        )
        val pkgB = samplePackage(id = "pkg-b", version = "1.5.0")
        val resBadRange = lock(listOf("org.mifos:pkg-bad-range"), listOf(pkgBadRange, pkgB), emptyList())
        val rejBadRange = assertIs<LockResult.Rejected>(resBadRange)
        assertTrue(rejBadRange.report.errors.any { it.code == PluginDependencyResolver.MALFORMED_VERSION_RANGE })

        val unclosed = samplePackage(
            id = "pkg-unclosed",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-b", "[1.0.0,2.0.0")),
        )
        val resUnclosed = lock(listOf("org.mifos:pkg-unclosed"), listOf(unclosed, pkgB), emptyList())
        assertTrue(
            assertIs<LockResult.Rejected>(resUnclosed).report.errors
                .any { it.code == PluginDependencyResolver.MALFORMED_VERSION_RANGE },
        )

        val pkgBadVersion = samplePackage(id = "pkg-bad-ver", version = "1.0.0-beta")
        val resBadVersion = lock(listOf("org.mifos:pkg-bad-ver"), listOf(pkgBadVersion), emptyList())
        val rejBadVersion = assertIs<LockResult.Rejected>(resBadVersion)
        assertTrue(rejBadVersion.report.errors.any { it.code == PluginDependencyResolver.MALFORMED_VERSION })
    }

    @Test
    fun testIntervalBoundInclusivity() {
        // "[" / "]" are inclusive and "(" / ")" exclusive, independently on each end.
        val exactUpper = samplePackage(id = "pkg-upper", version = "2.0.0")
        fun dependent(range: String) = samplePackage(
            id = "pkg-dep-${range.filter(Char::isLetterOrDigit)}",
            dependencies = listOf(DependencyRef("org.mifos", "pkg-upper", range)),
        )

        val inclusive = dependent("[1.0.0,2.0.0]")
        assertIs<LockResult.Locked>(lock(listOf(inclusive.key), listOf(inclusive, exactUpper), emptyList()))

        val exclusive = dependent("[1.0.0,2.0.0)")
        assertTrue(
            assertIs<LockResult.Rejected>(lock(listOf(exclusive.key), listOf(exclusive, exactUpper), emptyList()))
                .report.errors.any { it.code == PluginDependencyResolver.VERSION_OUT_OF_RANGE },
        )

        val exclusiveLower = dependent("(2.0.0,3.0.0]")
        assertTrue(
            assertIs<LockResult.Rejected>(
                lock(listOf(exclusiveLower.key), listOf(exclusiveLower, exactUpper), emptyList()),
            ).report.errors.any { it.code == PluginDependencyResolver.VERSION_OUT_OF_RANGE },
        )
    }

    @Test
    fun testThreeNodeCycleNamingPath() {
        val node1 = samplePackage(
            id = "node1",
            dependencies = listOf(DependencyRef("org.mifos", "node2", "1.0.0")),
        )
        val node2 = samplePackage(
            id = "node2",
            dependencies = listOf(DependencyRef("org.mifos", "node3", "1.0.0")),
        )
        val node3 = samplePackage(
            id = "node3",
            dependencies = listOf(DependencyRef("org.mifos", "node1", "1.0.0")),
        )

        val result = lock(listOf("org.mifos:node1"), listOf(node1, node2, node3), emptyList())
        val rejected = assertIs<LockResult.Rejected>(result)
        val cycleError = rejected.report.errors.firstOrNull { it.code == PluginDependencyResolver.DEPENDENCY_CYCLE }
        assertTrue(cycleError != null)
        assertTrue(
            cycleError.message.contains("node1") &&
                cycleError.message.contains("node2") &&
                cycleError.message.contains("node3"),
        )
    }

    @Test
    fun testDeclaredConflictBetweenEnabledPackages() {
        val pkgA = samplePackage(
            id = "pkg-a",
            conflicts = listOf(DependencyRef("org.mifos", "pkg-b", "[1.0.0,2.0.0)")),
        )
        val pkgB = samplePackage(id = "pkg-b", version = "1.2.0")

        val result = lock(listOf("org.mifos:pkg-a", "org.mifos:pkg-b"), listOf(pkgA, pkgB), emptyList())
        val rejected = assertIs<LockResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginDependencyResolver.CONFLICTING_PACKAGES })
    }

    @Test
    fun testDuplicateEnabledId() {
        val pkgA = samplePackage(id = "pkg-a")
        val result = lock(listOf("org.mifos:pkg-a", "org.mifos:pkg-a"), listOf(pkgA), emptyList())
        val rejected = assertIs<LockResult.Rejected>(result)
        assertTrue(rejected.report.errors.any { it.code == PluginDependencyResolver.DUPLICATE_ENABLED_ID })
    }

    @Test
    fun testDeterminismSameInputsYieldIdenticalOutput() {
        val pkgD = samplePackage(id = "pkg-d")
        val pkgC = samplePackage(id = "pkg-c", dependencies = listOf(DependencyRef("org.mifos", "pkg-d", "1.0.0")))
        val pkgB = samplePackage(id = "pkg-b")
        val pkgA = samplePackage(
            id = "pkg-a",
            dependencies = listOf(
                DependencyRef("org.mifos", "pkg-b", "1.0.0"),
                DependencyRef("org.mifos", "pkg-c", "1.0.0"),
            ),
        )

        val installed = listOf(pkgD, pkgC, pkgB, pkgA)
        val enabled = listOf("org.mifos:pkg-a")

        val run1 = lock(enabled, installed, emptyList())
        val run2 = lock(enabled, installed, emptyList())

        val locked1 = assertIs<LockResult.Locked>(run1)
        val locked2 = assertIs<LockResult.Locked>(run2)

        assertEquals(locked1.lockfile.entries, locked2.lockfile.entries)
        assertEquals(locked1.lockfile.edges, locked2.lockfile.edges)
    }
}
