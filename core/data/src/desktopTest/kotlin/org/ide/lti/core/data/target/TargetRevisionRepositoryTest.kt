/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.target

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.target.TargetRevisionDeleteOutcome
import org.ide.lti.core.model.target.DefaultTargetCatalog
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TargetRevisionRepositoryTest {

    private lateinit var tempDir: File
    private lateinit var repo: TargetRevisionRepositoryImpl
    private val target = DefaultTargetCatalog.PQ84P01_DEFAULT

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("target-revisions-test").toFile()
        repo = TargetRevisionRepositoryImpl(baseDir = tempDir)
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun archiveThenGetExactRoundTrip() = runTest {
        val ref = repo.archive(target)
        assertEquals(target.id, ref.profileId)
        assertEquals(1, ref.revision)
        assertTrue(ref.contentDigest.startsWith("sha256:"))

        val retrieved = repo.get(target.id, 1)
        assertNotNull(retrieved)
        assertEquals(target.id, retrieved.id)
        assertEquals(1, retrieved.revision)
        assertEquals(target.name, retrieved.name)
        assertEquals(target.filesystemType, retrieved.filesystemType)
    }

    @Test
    fun getForNonexistentRevisionReturnsNullNeverClosest() = runTest {
        repo.archive(target) // revision 1
        repo.archive(target.copy(name = "Rev 2")) // revision 2
        repo.archive(target.copy(name = "Rev 3")) // revision 3

        assertNull(repo.get(target.id, 0))
        assertNull(repo.get(target.id, 4))
        assertNull(repo.get(target.id, 99))
        assertNull(repo.get("non-existent-device", 1))
    }

    @Test
    fun listRevisionsReturnsAllArchivedRevisionsInOrder() = runTest {
        repo.archive(target.copy(name = "First"))
        repo.archive(target.copy(name = "Second"))
        repo.archive(target.copy(name = "Third"))

        val revisions = repo.listRevisions(target.id)
        assertEquals(3, revisions.size)
        assertEquals(listOf(1, 2, 3), revisions.map { it.revision })
        assertTrue(revisions.all { it.profileId == target.id })
        assertTrue(revisions.all { it.contentDigest.startsWith("sha256:") })
    }

    @Test
    fun deleteRefusesWhenReferencedAndSucceedsWhenNot() = runTest {
        repo.archive(target) // revision 1

        val refused = repo.delete(target.id, 1, referencedBy = listOf("workspace-alpha"))
        assertTrue(refused is TargetRevisionDeleteOutcome.Refused)
        assertFalse(refused.isDeleted)
        assertEquals(1, refused.report.errors.size)
        assertEquals(TargetRevisionRepositoryImpl.REFERENCED_TARGET_REVISION, refused.report.errors.first().code)

        // Revision is still retrievable
        assertNotNull(repo.get(target.id, 1))

        val deleted = repo.delete(target.id, 1, referencedBy = emptyList())
        assertTrue(deleted is TargetRevisionDeleteOutcome.Deleted)
        assertTrue(deleted.isDeleted)
        assertEquals(1, deleted.revision)

        // Now revision is gone
        assertNull(repo.get(target.id, 1))
    }

    @Test
    fun deleteWithActiveBindingsProviderRefusesReferencedRevision() = runTest {
        val providerRepo = TargetRevisionRepositoryImpl(
            baseDir = tempDir,
            activeBindingsProvider = { profileId, revision ->
                if (profileId == target.id && revision == 1) listOf("pinned-ws") else emptyList()
            },
        )
        providerRepo.archive(target) // revision 1
        providerRepo.archive(target) // revision 2

        val refused = providerRepo.delete(target.id, 1)
        assertTrue(refused is TargetRevisionDeleteOutcome.Refused)
        assertFalse(refused.isDeleted)

        val deleted = providerRepo.delete(target.id, 2)
        assertTrue(deleted is TargetRevisionDeleteOutcome.Deleted)
        assertTrue(deleted.isDeleted)
    }

    @Test
    fun deleteForNonexistentRevisionReturnsNotFound() = runTest {
        val outcome = repo.delete("UNKNOWN_PROFILE", 42)
        assertTrue(outcome is TargetRevisionDeleteOutcome.NotFound)
        assertFalse(outcome.isDeleted)
    }
}
