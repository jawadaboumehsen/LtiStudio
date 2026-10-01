/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class StageCheckpointTest {

    @Test
    fun testCanonicalDigestIsOrderIndependent() {
        val entry1 = ManifestEntry(
            relPath = "bin/kernel.img",
            sizeBytes = 1024L,
            sha256 = "sha-kernel",
            kind = ManifestEntryKind.FILE,
        )
        val entry2 = ManifestEntry(
            relPath = "etc/hosts",
            sizeBytes = 256L,
            sha256 = "sha-hosts",
            kind = ManifestEntryKind.FILE,
        )
        val entry3 = ManifestEntry(
            relPath = "lib/modules.symlink",
            sizeBytes = 0L,
            sha256 = "sha-symlink",
            kind = ManifestEntryKind.SYMLINK,
        )
        val entry4 = ManifestEntry(
            relPath = "meta/sidecar.json",
            sizeBytes = 512L,
            sha256 = "sha-sidecar",
            kind = ManifestEntryKind.SIDECAR,
        )

        val manifestA = ContentManifest(listOf(entry1, entry2, entry3, entry4))
        val manifestB = ContentManifest(listOf(entry4, entry3, entry2, entry1))
        val manifestC = ContentManifest(listOf(entry2, entry4, entry1, entry3))

        val digestA = manifestA.canonicalDigest()
        val digestB = manifestB.canonicalDigest()
        val digestC = manifestC.canonicalDigest()

        assertEquals(digestA, digestB)
        assertEquals(digestA, digestC)
    }

    @Test
    fun testCanonicalDigestChangesWhenAnyFieldChanges() {
        val baseEntry = ManifestEntry(
            relPath = "system/app.apk",
            sizeBytes = 1000L,
            sha256 = "sha-app",
            kind = ManifestEntryKind.FILE,
        )
        val baseDigest = ContentManifest(listOf(baseEntry)).canonicalDigest()

        val changedPath = baseEntry.copy(relPath = "system/app2.apk")
        assertNotEquals(baseDigest, ContentManifest(listOf(changedPath)).canonicalDigest())

        val changedSize = baseEntry.copy(sizeBytes = 1001L)
        assertNotEquals(baseDigest, ContentManifest(listOf(changedSize)).canonicalDigest())

        val changedSha = baseEntry.copy(sha256 = "sha-app-different")
        assertNotEquals(baseDigest, ContentManifest(listOf(changedSha)).canonicalDigest())

        val changedKind = baseEntry.copy(kind = ManifestEntryKind.SIDECAR)
        assertNotEquals(baseDigest, ContentManifest(listOf(changedKind)).canonicalDigest())
    }

    @Test
    fun testStageCheckpointInitRejectsMismatchedOutputDigest() {
        val entry = ManifestEntry(relPath = "out.bin", sizeBytes = 10L, sha256 = "abc", kind = ManifestEntryKind.FILE)
        val manifest = ContentManifest(listOf(entry))
        val validDigest = manifest.canonicalDigest()

        // Valid digest succeeds
        val checkpoint = StageCheckpoint(
            stageId = StageId.BUILD_FLASHABLE_ZIP,
            stageVersion = 1,
            inputDigest = "input-digest-123",
            rootRelPath = "build/output",
            manifest = manifest,
            sidecarRelPaths = emptyList(),
            outputDigest = validDigest,
            verification = null,
        )
        assertEquals(validDigest, checkpoint.outputDigest)

        // Invalid digest throws IllegalArgumentException
        assertFailsWith<IllegalArgumentException> {
            StageCheckpoint(
                stageId = StageId.BUILD_FLASHABLE_ZIP,
                stageVersion = 1,
                inputDigest = "input-digest-123",
                rootRelPath = "build/output",
                manifest = manifest,
                sidecarRelPaths = emptyList(),
                outputDigest = "wrong-digest",
                verification = null,
            )
        }
    }
}
