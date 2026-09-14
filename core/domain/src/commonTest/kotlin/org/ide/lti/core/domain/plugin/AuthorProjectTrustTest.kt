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

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class AuthorProjectTrustTest {

    private class FakeAuthorProjectTrustPort : AuthorProjectTrustPort {
        val fingerprints = mutableMapOf<String, String?>()
        val approvals = mutableMapOf<String, TrustApproval>()

        override suspend fun fingerprint(projectPath: String): String? = fingerprints[projectPath]

        override suspend fun approval(projectPath: String): TrustApproval? = approvals[projectPath]

        override suspend fun approve(project: AuthorProject): TrustApproval {
            val approval = TrustApproval(
                projectPath = project.path,
                fingerprint = project.fingerprint,
                approvedAtEpochMs = 123456789L,
            )
            approvals[project.path] = approval
            return approval
        }

        override suspend fun revoke(projectPath: String) {
            approvals.remove(projectPath)
        }
    }

    @Test
    fun testNotApprovedWhenNoApprovalStored() = runTest {
        val port = FakeAuthorProjectTrustPort()
        val useCase = AuthorProjectTrustUseCase(port)
        val projectPath = "/path/to/mod-project"
        port.fingerprints[projectPath] = "abc123fingerprint"

        val check = useCase.check(projectPath)

        assertIs<TrustCheck.NotApproved>(check)
    }

    @Test
    fun testNotApprovedWhenFingerprintUnreadable() = runTest {
        val port = FakeAuthorProjectTrustPort()
        val useCase = AuthorProjectTrustUseCase(port)
        val projectPath = "/path/to/missing-project"
        port.fingerprints[projectPath] = null

        val check = useCase.check(projectPath)

        assertIs<TrustCheck.NotApproved>(check)
    }

    @Test
    fun testApprovedWhenStoredFingerprintMatchesCurrent() = runTest {
        val port = FakeAuthorProjectTrustPort()
        val useCase = AuthorProjectTrustUseCase(port)
        val projectPath = "/path/to/mod-project"
        val fingerprint = "sha256-initial"
        port.fingerprints[projectPath] = fingerprint

        val project = AuthorProject(
            path = projectPath,
            fingerprint = fingerprint,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )
        val approval = useCase.approve(project)
        assertEquals(projectPath, approval.projectPath)
        assertEquals(fingerprint, approval.fingerprint)

        val check = useCase.check(projectPath)

        assertIs<TrustCheck.Approved>(check)
        assertEquals(approval, check.approval)
    }

    @Test
    fun testFingerprintChangedAfterApproval() = runTest {
        val port = FakeAuthorProjectTrustPort()
        val useCase = AuthorProjectTrustUseCase(port)
        val projectPath = "/path/to/mod-project"
        val originalFingerprint = "sha256-original"
        port.fingerprints[projectPath] = originalFingerprint

        val project = AuthorProject(
            path = projectPath,
            fingerprint = originalFingerprint,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )
        useCase.approve(project)

        // Project files change, producing a new fingerprint
        val modifiedFingerprint = "sha256-modified"
        port.fingerprints[projectPath] = modifiedFingerprint

        val check = useCase.check(projectPath)

        assertIs<TrustCheck.FingerprintChanged>(check)
        assertEquals(originalFingerprint, check.previous)
        assertEquals(modifiedFingerprint, check.current)
    }

    @Test
    fun testRevocationInvalidatesApproval() = runTest {
        val port = FakeAuthorProjectTrustPort()
        val useCase = AuthorProjectTrustUseCase(port)
        val projectPath = "/path/to/mod-project"
        val fingerprint = "sha256-valid"
        port.fingerprints[projectPath] = fingerprint

        val project = AuthorProject(
            path = projectPath,
            fingerprint = fingerprint,
            sdkVersion = "1.0.0",
            generatorEntry = "main",
        )
        useCase.approve(project)
        useCase.revoke(projectPath)

        assertNull(port.approval(projectPath))
        val check = useCase.check(projectPath)
        assertIs<TrustCheck.NotApproved>(check)
    }
}
