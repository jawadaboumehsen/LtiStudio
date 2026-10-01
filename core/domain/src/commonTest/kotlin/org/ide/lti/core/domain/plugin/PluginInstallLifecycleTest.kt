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

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PluginInstallLifecycleTest {

    private class FakePluginInstallPort : PluginInstallPort {
        var quarantineResult: Path? = Paths.get("/fake/quarantine/pkg.lti-mod.zip")
        var inspectionResult: PackageInspection? = null
        var promoteResult: Boolean = true
        var promoteCalled: Boolean = false
        var removeCalled: Boolean = false

        val store = mutableListOf<InstalledRecord>()

        override suspend fun quarantine(sourceLabel: String): Path? = quarantineResult

        override suspend fun inspect(quarantined: Path): PackageInspection = inspectionResult ?: PackageInspection(
            identity = PackageIdentity(
                publisher = "org.mifos",
                id = "sample",
                version = "1.0.0",
                contentDigest = "sha256:1111111111111111111111111111111111111111111111111111111111111111",
            ),
            hasSignature = true,
            report = ValidationReport(),
        )

        override suspend fun promote(quarantined: Path, identity: PackageIdentity): Boolean {
            promoteCalled = true
            if (!promoteResult) return false
            val record = InstalledRecord(
                identity = identity,
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            )
            store.removeAll { it.identity == identity }
            store.add(record)
            return true
        }

        override suspend fun installed(): List<InstalledRecord> = store.toList()

        override suspend fun remove(identity: PackageIdentity): Boolean {
            removeCalled = true
            return store.removeAll { it.identity == identity }
        }

        override suspend fun updateRecord(record: InstalledRecord): Boolean {
            val idx = store.indexOfFirst { it.identity == record.identity }
            return if (idx >= 0) {
                store[idx] = record
                true
            } else {
                false
            }
        }
    }

    @Test
    fun testHappyInstallEmitsExpectedStatesInOrderAndRecordHasNoWorkspaces() = runTest {
        val fake = FakePluginInstallPort()
        val useCase = InstallPluginUseCase(fake)

        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.TRUSTED_SIGNATURE,
        )

        val states = useCase(request).toList()

        assertEquals(5, states.size)
        assertIs<InstallState.SourceSelected>(states[0])
        assertIs<InstallState.Downloading>(states[1])
        assertIs<InstallState.Quarantined>(states[2])
        assertIs<InstallState.Verified>(states[3])

        val installedState = assertIs<InstallState.Installed>(states[4])
        assertEquals("org.mifos", installedState.record.publisher)
        assertEquals("sample", installedState.record.id)

        val stored = fake.installed().firstOrNull { it.identity.id == "sample" }
        assertTrue(stored != null)
        assertTrue(stored.enabledInWorkspaces.isEmpty())
        assertTrue(fake.promoteCalled)
    }

    @Test
    fun testUntrustedInstallNeverPromotesAndEmitsInstallNotTrusted() = runTest {
        val fake = FakePluginInstallPort()
        val useCase = InstallPluginUseCase(fake)

        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.UNTRUSTED,
        )

        val states = useCase(request).toList()

        assertFalse(fake.promoteCalled)
        val failed = assertIs<InstallState.Failed>(states.last())
        assertTrue(failed.report.errors.any { it.code == InstallPluginUseCase.INSTALL_NOT_TRUSTED })
    }

    @Test
    fun testRejectedInspectionNeverPromotesAndEndsInFailed() = runTest {
        val fake = FakePluginInstallPort()
        fake.inspectionResult = PackageInspection(
            identity = null,
            hasSignature = false,
            report = ValidationReport(
                errors = listOf(
                    ValidationError(
                        stageId = StageId.MODULE_APPLICATION,
                        objectId = "pkg",
                        fieldPath = "manifest.json",
                        code = "CORRUPT",
                        severity = Severity.ERROR,
                        message = "Corrupt manifest",
                    ),
                ),
            ),
        )

        val useCase = InstallPluginUseCase(fake)
        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.TRUSTED_UNVERIFIED,
        )

        val states = useCase(request).toList()

        assertFalse(fake.promoteCalled)
        val failed = assertIs<InstallState.Failed>(states.last())
        assertTrue(failed.report.errors.any { it.code == "CORRUPT" })
    }

    @Test
    fun testDigestMismatchNeverPromotesAndEndsInFailed() = runTest {
        val fake = FakePluginInstallPort()
        val useCase = InstallPluginUseCase(fake)

        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = "sha256:different_digest",
            trust = TrustDecision.TRUSTED_SIGNATURE,
        )

        val states = useCase(request).toList()

        assertFalse(fake.promoteCalled)
        val failed = assertIs<InstallState.Failed>(states.last())
        assertTrue(failed.report.errors.any { it.code == InstallPluginUseCase.DIGEST_MISMATCH })
    }

    @Test
    fun testSameVersionDifferentContentNeverPromotesAndEndsInFailed() = runTest {
        val fake = FakePluginInstallPort()
        val existingRecord = InstalledRecord(
            identity = PackageIdentity(
                publisher = "org.mifos",
                id = "sample",
                version = "1.0.0",
                contentDigest = "sha256:existing_digest_99999999999999999999999999999999",
            ),
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        )
        fake.store.add(existingRecord)

        val useCase = InstallPluginUseCase(fake)
        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.TRUSTED_SIGNATURE,
        )

        val states = useCase(request).toList()

        assertFalse(fake.promoteCalled)
        val failed = assertIs<InstallState.Failed>(states.last())
        assertTrue(failed.report.errors.any { it.code == InstallPluginUseCase.SAME_VERSION_DIFFERENT_CONTENT })
    }

    @Test
    fun testRefusedPromoteEndsInFailed() = runTest {
        val fake = FakePluginInstallPort()
        fake.promoteResult = false

        val useCase = InstallPluginUseCase(fake)
        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.TRUSTED_SIGNATURE,
        )

        val states = useCase(request).toList()

        assertTrue(fake.promoteCalled)
        val failed = assertIs<InstallState.Failed>(states.last())
        assertTrue(failed.report.errors.any { it.code == InstallPluginUseCase.INSTALL_REFUSED })
    }

    @Test
    fun testQuarantineCancelledEmitsCancelled() = runTest {
        val fake = FakePluginInstallPort()
        fake.quarantineResult = null

        val useCase = InstallPluginUseCase(fake)
        val request = InstallRequest(
            sourceLabel = "/path/to/mod.zip",
            expectedDigest = null,
            trust = TrustDecision.TRUSTED_SIGNATURE,
        )

        val states = useCase(request).toList()

        assertEquals(3, states.size)
        assertIs<InstallState.SourceSelected>(states[0])
        assertIs<InstallState.Downloading>(states[1])
        assertIs<InstallState.Cancelled>(states[2])
    }

    @Test
    fun testUpdateInstallsAlongsideAndLeavesCurrentRecordPresent() = runTest {
        val fake = FakePluginInstallPort()
        val currentIdentity = PackageIdentity("org.mifos", "pkg-a", "1.0.0", "sha256:current")
        val incomingIdentity = PackageIdentity("org.mifos", "pkg-a", "1.1.0", "sha256:incoming")

        fake.store.add(
            InstalledRecord(
                identity = currentIdentity,
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
        )
        fake.store.add(
            InstalledRecord(
                identity = incomingIdentity,
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
        )

        val lifecycle = PluginLifecycleUseCase(fake)
        val outcome = lifecycle.update(currentIdentity, incomingIdentity)

        val applied = assertIs<LifecycleOutcome.Applied>(outcome)
        assertEquals("1.1.0", applied.record.identity.version)

        // Current record is still present in store!
        val allInstalled = fake.installed()
        assertEquals(2, allInstalled.size)
        assertTrue(allInstalled.any { it.identity == currentIdentity })
        assertTrue(allInstalled.any { it.identity == incomingIdentity })
    }

    @Test
    fun testUninstallWithReferencesIsRefusedWhileArchiveSucceeds() = runTest {
        val fake = FakePluginInstallPort()
        val identity = PackageIdentity("org.mifos", "tool", "1.0.0", "sha256:tool")
        val record = InstalledRecord(
            identity = identity,
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        )
        fake.store.add(record)

        val lifecycle = PluginLifecycleUseCase(fake)

        // Uninstall with references is refused
        val uninstallOutcome = lifecycle.uninstall(identity, referencedBy = listOf("org.mifos:dependent-pkg"))
        val refused = assertIs<LifecycleOutcome.Refused>(uninstallOutcome)
        assertTrue(refused.report.errors.any { it.code == PluginLifecycleUseCase.REFERENCED_PACKAGE })
        assertFalse(fake.removeCalled)

        // Archive succeeds and flags archived = true without removing bytes
        val archiveOutcome = lifecycle.archive(identity)
        val applied = assertIs<LifecycleOutcome.Applied>(archiveOutcome)
        assertTrue(applied.record.archived)
        assertEquals(1, fake.installed().size)
    }

    @Test
    fun testRevokedPackageFailsCanEnableAndIsStillPresent() = runTest {
        val fake = FakePluginInstallPort()
        val identity = PackageIdentity("org.mifos", "bad-pkg", "1.0.0", "sha256:bad")
        val record = InstalledRecord(
            identity = identity,
            trust = TrustDecision.TRUSTED_SIGNATURE,
            enabledInWorkspaces = emptyList(),
            revoked = false,
            archived = false,
            dependencies = emptyList(),
            conflicts = emptyList(),
        )
        fake.store.add(record)

        val lifecycle = PluginLifecycleUseCase(fake)
        val revokeOutcome = lifecycle.revoke(identity, "security flaw")
        val applied = assertIs<LifecycleOutcome.Applied>(revokeOutcome)
        assertTrue(applied.record.revoked)

        // canEnable returns false
        assertFalse(lifecycle.canEnable(applied.record))

        // Still present in installed store
        val stored = fake.installed().firstOrNull { it.identity == identity }
        assertTrue(stored != null)
        assertTrue(stored.revoked)

        // Cannot be uninstalled (evidence preserved)
        val uninstallOutcome = lifecycle.uninstall(identity, emptyList())
        val refused = assertIs<LifecycleOutcome.Refused>(uninstallOutcome)
        assertTrue(refused.report.errors.any { it.code == PluginLifecycleUseCase.REVOKED_PACKAGE })
    }
}
