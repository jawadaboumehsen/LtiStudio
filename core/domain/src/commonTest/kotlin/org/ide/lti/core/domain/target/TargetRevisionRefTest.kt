/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.target

import org.ide.lti.core.model.target.DefaultTargetCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TargetRevisionRefTest {

    private val target = DefaultTargetCatalog.PQ84P01_DEFAULT

    @Test
    fun targetRevisionRefInvariantsEnforced() {
        assertFailsWith<IllegalArgumentException> {
            TargetRevisionRef(profileId = "PQ84P01", revision = 0, contentDigest = "sha256:abcdef")
        }
        assertFailsWith<IllegalArgumentException> {
            TargetRevisionRef(profileId = "PQ84P01", revision = 1, contentDigest = "md5:abcdef")
        }
        val valid = TargetRevisionRef(profileId = "PQ84P01", revision = 1, contentDigest = "sha256:123456")
        assertEquals("PQ84P01", valid.profileId)
        assertEquals(1, valid.revision)
    }

    @Test
    fun computeTargetDigestGeneratesSha256PrefixedHexDigest() {
        val digest = TargetRevisionRef.computeDigest(target)
        assertTrue(digest.startsWith("sha256:"), "Digest must start with sha256:")
        assertEquals(71, digest.length, "sha256 prefix + 64 hex characters should be 71 characters")

        val ref = TargetRevisionRef.of(target)
        assertEquals(target.id, ref.profileId)
        assertEquals(target.revision, ref.revision)
        assertEquals(digest, ref.contentDigest)
    }

    @Test
    fun diffTargetsReportsNoChangesForIdenticalProfiles() {
        val diff = diffTargets(target, target)
        assertFalse(diff.hasChanges)
        assertTrue(diff.changedFields.isEmpty())
        assertTrue(diff.fieldChanges.isEmpty())
    }

    @Test
    fun diffTargetsReportsActualChangedFields() {
        val modified = target.copy(name = "Updated Astra Tablet", filesystemType = "ext4")
        val diff = diffTargets(target, modified)

        assertTrue(diff.hasChanges)
        assertEquals(setOf("name", "filesystemType"), diff.changedFields)
        val nameChange = diff.fieldChanges.find { it.fieldName == "name" }
        assertEquals(target.name, nameChange?.oldValue)
        assertEquals("Updated Astra Tablet", nameChange?.newValue)

        val fsChange = diff.fieldChanges.find { it.fieldName == "filesystemType" }
        assertEquals("erofs", fsChange?.oldValue)
        assertEquals("ext4", fsChange?.newValue)
    }

    @Test
    fun diffTargetsDetectsRevisionAndPartitionGeometryChanges() {
        val modified = target.copy(
            revision = 2,
            superPartitionBytes = 20000000000L,
            bootPartitionBytes = 120000000L,
        )
        val diff = diffTargets(target, modified)

        assertTrue(diff.hasChanges)
        assertEquals(1, diff.oldRevision)
        assertEquals(2, diff.newRevision)
        assertEquals(setOf("revision", "superPartitionBytes", "bootPartitionBytes"), diff.changedFields)
    }
}
