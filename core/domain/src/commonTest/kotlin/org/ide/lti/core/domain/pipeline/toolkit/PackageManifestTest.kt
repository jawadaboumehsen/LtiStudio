/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PackageManifestTest {
    @Test
    fun validPackageManifestRoundTripsViaJson() {
        val entry1 = PartitionEntry(name = "system", sizeBytes = 1048576L, sourceMember = "system.img")
        val entry2 = PartitionEntry(name = "vendor", sizeBytes = 524288L, sourceMember = "vendor.img")
        val manifest = PackageManifest(
            format = PackageFormat.AndroidPayload,
            declaredChecksum = "a1b2c3d4",
            partitions = listOf(entry1, entry2),
            refusal = null,
        )

        val encoded = PackageManifest.encode(manifest)
        val decoded = PackageManifest.decode(encoded)

        assertEquals(manifest, decoded)
        assertNull(decoded.refusal)
        assertNull(decoded.refusal)
        assertEquals(2, decoded.partitions.size)
    }

    @Test
    fun incrementalRefusalSetsStandardRefusal() {
        val manifest = PackageManifest.incrementalRefusal(item = "payload.bin", details = "source_version present")
        assertNotNull(manifest.refusal)
        val refusal = manifest.refusal
        assertNotNull(refusal)
        assertEquals("The package is an incremental update.", refusal.cause)
        assertEquals("payload.bin", refusal.item)
        assertEquals("Import the full update instead.", refusal.nextStep)
        assertEquals("source_version present", refusal.details)
    }

    @Test
    fun unknownRefusalSetsStandardRefusal() {
        val manifest = PackageManifest.unknownRefusal(item = "unknown.bin")
        assertNotNull(manifest.refusal)
        assertEquals(PackageFormat.Unknown, manifest.format)
        val refusal = manifest.refusal
        assertNotNull(refusal)
        assertEquals("The package format was not recognized.", refusal.cause)
        assertEquals("unknown.bin", refusal.item)
        assertEquals(
            "Use a supported ZIP, 7z, TAR, payload.bin, block-OTA, or Odin archive.",
            refusal.nextStep,
        )
    }

    @Test
    fun partitionEntryRejectsInvalidValues() {
        assertFailsWith<IllegalArgumentException> {
            PartitionEntry(name = "", sizeBytes = 100L, sourceMember = "file")
        }
        assertFailsWith<IllegalArgumentException> {
            PartitionEntry(name = "boot", sizeBytes = -1L, sourceMember = "boot.img")
        }
        assertFailsWith<IllegalArgumentException> {
            PartitionEntry(name = "boot", sizeBytes = 100L, sourceMember = "  ")
        }
    }
}
