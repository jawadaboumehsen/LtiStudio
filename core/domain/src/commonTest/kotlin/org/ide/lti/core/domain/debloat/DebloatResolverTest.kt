/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

import org.ide.lti.core.model.workspace.DebloatSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DebloatResolverTest {

    private val sampleInventory = listOf(
        DebloatCandidate(
            partition = "system",
            relativePath = "app/YouTube/YouTube.apk",
            sizeBytes = 50_000_000L,
            packageId = "com.google.android.youtube",
        ),
        DebloatCandidate(
            partition = "system",
            relativePath = "priv-app/Velvet/Velvet.apk",
            sizeBytes = 80_000_000L,
            packageId = "com.google.android.googlequicksearchbox",
        ),
        DebloatCandidate(
            partition = "product",
            relativePath = "app/Chrome/Chrome.apk",
            sizeBytes = 120_000_000L,
            packageId = "com.android.chrome",
        ),
        DebloatCandidate(
            partition = "system",
            relativePath = "etc/hosts",
            sizeBytes = 1024L,
            packageId = null,
        ),
    )

    private val targetPartitions = setOf("system", "product")

    @Test
    fun `disabled yields an empty decision even with selectors`() {
        val settings = DebloatSettings(
            enabled = false,
            removeSelectors = listOf("system:app/YouTube/YouTube.apk", "pkg:com.android.chrome"),
            keepSelectors = listOf("system:etc/hosts"),
        )
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = sampleInventory,
            presetSelectors = listOf("system:priv-app/Velvet/Velvet.apk"),
            protectedSelectors = listOf("system:etc/hosts"),
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Resolved)
        assertEquals(emptyList(), resolution.decision.removals)
        assertEquals(emptyList(), resolution.decision.kept)
        assertEquals(0L, resolution.decision.logicalBytesFreed)
    }

    @Test
    fun `exact path and pkg selectors resolve`() {
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:app/YouTube/YouTube.apk"),
        )
        val presetSelectors = listOf("pkg:com.android.chrome")
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = sampleInventory,
            presetSelectors = presetSelectors,
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Resolved)
        val decision = resolution.decision
        assertEquals(2, decision.removals.size)

        val yt = decision.removals.first { it.relativePath == "app/YouTube/YouTube.apk" }
        assertEquals("system", yt.partition)
        assertEquals(50_000_000L, yt.sizeBytes)
        assertEquals("com.google.android.youtube", yt.packageId)
        assertEquals(RemovalReason.EXPLICIT_SELECTOR, yt.reason)

        val chrome = decision.removals.first { it.packageId == "com.android.chrome" }
        assertEquals("product", chrome.partition)
        assertEquals(120_000_000L, chrome.sizeBytes)
        assertEquals(RemovalReason.PRESET, chrome.reason)
    }

    @Test
    fun `keep wins over remove`() {
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:app/YouTube/YouTube.apk"),
            keepSelectors = listOf("pkg:com.google.android.youtube"),
        )
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Resolved)
        val decision = resolution.decision
        assertEquals(0, decision.removals.size)
        assertEquals(1, decision.kept.size)
        val keptEntry = decision.kept.first()
        assertEquals("system", keptEntry.partition)
        assertEquals("app/YouTube/YouTube.apk", keptEntry.relativePath)
        assertEquals(KeepReason.KEEP_SELECTOR, keptEntry.reason)
    }

    @Test
    fun `a protected entry is blocking`() {
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:etc/hosts"),
        )
        val protectedSelectors = listOf("system:etc/hosts")
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = protectedSelectors,
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Rejected)
        val error = resolution.report.errors.firstOrNull { it.code == PROTECTED_ENTRY }
        assertTrue(error != null)

        // But if a keep selector excluded it, protected entry check does not block
        val settingsWithKeep = settings.copy(keepSelectors = listOf("system:etc/hosts"))
        val resolutionWithKeep = DebloatResolver.resolve(
            settings = settingsWithKeep,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = protectedSelectors,
            targetPartitions = targetPartitions,
        )
        assertTrue(resolutionWithKeep is DebloatResolution.Resolved)
        assertEquals(0, resolutionWithKeep.decision.removals.size)
        assertEquals(1, resolutionWithKeep.decision.kept.size)
    }

    @Test
    fun `unmatched selector malformed selector and partition-not-in-target each report their code`() {
        // Unmatched selector
        val unmatchedSettings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:app/NonExistent/NonExistent.apk"),
        )
        val unmatchedRes = DebloatResolver.resolve(
            settings = unmatchedSettings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )
        assertTrue(unmatchedRes is DebloatResolution.Rejected)
        assertTrue(unmatchedRes.report.errors.any { it.code == UNMATCHED_SELECTOR })

        // Malformed selectors (globs, shell characters, invalid syntax)
        val malformedGlobSettings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("pkg:com.google.*"),
        )
        val malformedGlobRes = DebloatResolver.resolve(
            settings = malformedGlobSettings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )
        assertTrue(malformedGlobRes is DebloatResolution.Rejected)
        assertTrue(malformedGlobRes.report.errors.any { it.code == MALFORMED_SELECTOR })

        val malformedShellSettings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("system:app/test;rm -rf"),
        )
        val malformedShellRes = DebloatResolver.resolve(
            settings = malformedShellSettings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )
        assertTrue(malformedShellRes is DebloatResolution.Rejected)
        assertTrue(malformedShellRes.report.errors.any { it.code == MALFORMED_SELECTOR })

        // Partition not in target
        val vendorCandidate = DebloatCandidate("vendor", "app/Test/Test.apk", 1000L, "com.test")
        val partitionNotInTargetSettings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("vendor:app/Test/Test.apk"),
        )
        val partitionRes = DebloatResolver.resolve(
            settings = partitionNotInTargetSettings,
            inventory = sampleInventory + vendorCandidate,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = setOf("system", "product"),
        )
        assertTrue(partitionRes is DebloatResolution.Rejected)
        assertTrue(partitionRes.report.errors.any { it.code == PARTITION_NOT_IN_TARGET })
    }

    @Test
    fun `logicalBytesFreed sums only removals`() {
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf(
                "system:app/YouTube/YouTube.apk",
                "system:priv-app/Velvet/Velvet.apk",
            ),
            keepSelectors = listOf("system:priv-app/Velvet/Velvet.apk"),
        )
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = sampleInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Resolved)
        val decision = resolution.decision
        // YouTube is 50_000_000, Velvet is kept (not removed)
        assertEquals(1, decision.removals.size)
        assertEquals(50_000_000L, decision.logicalBytesFreed)
    }

    @Test
    fun `ordering is deterministic`() {
        val unsortedInventory = listOf(
            DebloatCandidate("system", "priv-app/B/B.apk", 100L, "b"),
            DebloatCandidate("product", "app/Z/Z.apk", 200L, "z"),
            DebloatCandidate("product", "app/A/A.apk", 300L, "a"),
            DebloatCandidate("system", "app/A/A.apk", 400L, "a"),
        )
        val settings = DebloatSettings(
            enabled = true,
            removeSelectors = listOf("pkg:b", "pkg:z", "pkg:a"),
        )
        val resolution = DebloatResolver.resolve(
            settings = settings,
            inventory = unsortedInventory,
            presetSelectors = emptyList(),
            protectedSelectors = emptyList(),
            targetPartitions = targetPartitions,
        )

        assertTrue(resolution is DebloatResolution.Resolved)
        val decision = resolution.decision
        val partitions = decision.removals.map { it.partition }
        val paths = decision.removals.map { it.relativePath }

        // Expected sorted by partition ("product" then "system"), then relativePath
        assertEquals(listOf("product", "product", "system", "system"), partitions)
        assertEquals(
            listOf("app/A/A.apk", "app/Z/Z.apk", "app/A/A.apk", "priv-app/B/B.apk"),
            paths,
        )
    }
}
