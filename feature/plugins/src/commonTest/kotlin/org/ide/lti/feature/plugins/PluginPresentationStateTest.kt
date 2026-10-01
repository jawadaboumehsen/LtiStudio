/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.plugins

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.domain.plugin.AuthorDiagnostic
import org.ide.lti.core.domain.plugin.AuthorTaskRefusal
import org.ide.lti.core.domain.plugin.AuthorTaskResult
import org.ide.lti.core.domain.plugin.IndexEntry
import org.ide.lti.core.domain.plugin.IndexFetchResult
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.MarketplaceIndexDocument
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PackageInspection
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.plugin.key
import org.ide.lti.core.model.plugin.DependencyRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Presentation-state tests verifying UI truthfulness, state-derived inspections,
 * truthful author tools output, and selection stability across filters.
 */
class PluginPresentationStateTest {

    @Test
    fun testUninspectedImportStateDisablesInstallAndRequiresInspection() {
        val inspection: PackageInspection? = null

        assertFalse(canInstallInspectedPackage(inspection, acknowledged = false))
    }

    @Test
    fun testInspectedImportStateWithBlockingErrorsDisablesInstall() {
        val error = ValidationError(
            stageId = null,
            objectId = null,
            fieldPath = "manifest.json",
            code = "ERR01",
            severity = Severity.ERROR,
            message = "Corrupt manifest signature",
        )
        val inspection = PackageInspection(
            identity = PackageIdentity("com.example", "mod1", "1.0.0", "sha256:abc"),
            hasSignature = true,
            report = ValidationReport(errors = listOf(error)),
        )

        assertFalse(canInstallInspectedPackage(inspection, acknowledged = false))
        assertTrue(inspection.report.hasBlockingErrors())
        assertEquals(1, inspection.report.errors.size)
    }

    @Test
    fun testInspectedSignedPackageEnablesInstallWithoutAcknowledgement() {
        val inspection = PackageInspection(
            identity = PackageIdentity("com.example", "mod1", "1.0.0", "sha256:abc"),
            hasSignature = true,
            report = ValidationReport(),
            dependencies = listOf(DependencyRef("com.example", "core-lib", "^1.0.0")),
        )

        assertTrue(canInstallInspectedPackage(inspection, acknowledged = false))
        assertEquals("com.example", inspection.identity?.publisher)
        assertEquals("mod1", inspection.identity?.id)
        assertEquals(1, inspection.dependencies.size)
        assertEquals("core-lib", inspection.dependencies.first().id)
    }

    @Test
    fun testInspectedUnsignedPackageRequiresExplicitAcknowledgement() {
        val inspection = PackageInspection(
            identity = PackageIdentity("com.example", "mod1", "1.0.0", "sha256:abc"),
            hasSignature = false,
            report = ValidationReport(),
        )

        assertFalse(canInstallInspectedPackage(inspection, acknowledged = false))
        assertTrue(canInstallInspectedPackage(inspection, acknowledged = true))
    }

    @Test
    fun testMarketplaceInstallRequiresFreshSourceVerification() {
        val document = MarketplaceIndexDocument()

        assertFalse(marketplaceSourceIsVerified(null))
        assertFalse(marketplaceSourceIsVerified(IndexFetchResult.Cached(document, 1L)))
        assertTrue(marketplaceSourceIsVerified(IndexFetchResult.Fetched(document, 1L)))
    }

    @Test
    fun testAuthorToolsInitialStateReflectsNoRunAndNoPackageReceipt() {
        val taskResult: AuthorTaskResult? = null
        val isRunning = false

        // Before run, no diagnostics and no package receipt exist
        val diagnostics = when (taskResult) {
            is AuthorTaskResult.Success -> taskResult.diagnostics
            is AuthorTaskResult.Failed -> taskResult.diagnostics
            else -> emptyList()
        }
        assertTrue(diagnostics.isEmpty())

        assertEquals("Not run", authorTaskStatusLabel(selected = true, running = false, result = taskResult))
        assertFalse(packageTaskSucceeded(PACKAGE_AUTHOR_TASK, taskResult))
        assertNull(taskResult)
        assertFalse(isRunning)
    }

    @Test
    fun testAuthorToolsExecutionStatesTruthfullyMapResults() {
        val diag = AuthorDiagnostic(severity = "error", code = "COMP01", message = "Syntax error at line 14")
        val failedResult: AuthorTaskResult = AuthorTaskResult.Failed(
            exitCode = 1,
            diagnostics = listOf(diag),
            stdout = "",
            stderr = "Compilation failed",
        )

        val failedDiagnostics = when (failedResult) {
            is AuthorTaskResult.Success -> failedResult.diagnostics
            is AuthorTaskResult.Failed -> failedResult.diagnostics
            else -> emptyList()
        }
        assertEquals(1, failedDiagnostics.size)
        assertEquals("COMP01", failedDiagnostics.first().code)
        assertEquals(1, (failedResult as AuthorTaskResult.Failed).exitCode)

        val successResult: AuthorTaskResult = AuthorTaskResult.Success(
            stdout = "BUILD SUCCESSFUL",
            stderr = "",
            diagnostics = emptyList(),
        )
        assertTrue(successResult is AuthorTaskResult.Success)
        assertEquals("Completed", authorTaskStatusLabel(selected = true, running = false, result = successResult))
        assertFalse(packageTaskSucceeded("testMod", successResult))
        assertTrue(packageTaskSucceeded(PACKAGE_AUTHOR_TASK, successResult))

        val timedOutResult = AuthorTaskResult.TimedOut(afterMs = 60000L)
        assertEquals(60000L, timedOutResult.afterMs)

        val refusedResult = AuthorTaskResult.Refused(AuthorTaskRefusal.NOT_TRUSTED)
        assertEquals(AuthorTaskRefusal.NOT_TRUSTED, refusedResult.reason)
    }

    @Test
    fun testSelectionPreservedWhenFilterMatchesSelectedItem() {
        val records = listOf(
            InstalledRecord(
                identity = PackageIdentity("pubA", "modA", "1.0", "sha1"),
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
            InstalledRecord(
                identity = PackageIdentity("pubB", "modB", "1.0", "sha2"),
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
        )

        // User selected item B
        val selectedKey = "pubB:modB"

        // Filter for "mod" (matches both) -> selection remains item B
        val query = "mod"
        val filtered = records.filter { it.identity.id.contains(query) }
        val resolved = resolveInstalledSelection(filtered, selectedKey)

        assertEquals("pubB:modB", resolved?.identity?.key)
    }

    @Test
    fun testSelectionFallsBackWhenFilterExcludesSelectedItem() {
        val records = listOf(
            InstalledRecord(
                identity = PackageIdentity("pubA", "modA", "1.0", "sha1"),
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
            InstalledRecord(
                identity = PackageIdentity("pubB", "modB", "1.0", "sha2"),
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
        )

        val selectedKey = "pubB:modB"

        // Filter for "modA" (excludes item B) -> selection falls back to first matching (item A)
        val query = "modA"
        val filtered = records.filter { it.identity.id.contains(query) }
        val resolved = resolveInstalledSelection(filtered, selectedKey)

        assertEquals("pubA:modA", resolved?.identity?.key)
    }

    @Test
    fun testSelectionResolvesToNullWhenFilterMatchesNothing() {
        val records = listOf(
            InstalledRecord(
                identity = PackageIdentity("pubA", "modA", "1.0", "sha1"),
                trust = TrustDecision.TRUSTED_SIGNATURE,
                enabledInWorkspaces = emptyList(),
                revoked = false,
                archived = false,
                dependencies = emptyList(),
                conflicts = emptyList(),
            ),
        )

        val query = "nonexistent"
        val filtered = records.filter { it.identity.id.contains(query) }
        val resolved = resolveInstalledSelection(filtered, "pubA:modA")

        assertNull(resolved)
    }

    @Test
    fun testMarketplaceSelectionStabilityAcrossFilterChanges() {
        val entries = listOf(
            IndexEntry(
                publisher = "pubA",
                id = "pkgA",
                version = "1.0",
                sdkApiRange = "v4",
                targetSummary = "target1",
                packageUrl = "https://example.com/a.zip",
                contentDigest = "shaA",
                signatureIdentity = null,
            ),
            IndexEntry(
                publisher = "pubB",
                id = "pkgB",
                version = "1.0",
                sdkApiRange = "v4",
                targetSummary = "target2",
                packageUrl = "https://example.com/b.zip",
                contentDigest = "shaB",
                signatureIdentity = null,
            ),
        )

        val selectedEntryId = "pkgB"

        // Filter includes pkgB -> stays pkgB
        val filter1 = entries.filter { it.id.contains("pkg") }
        val resolved1 = resolveMarketplaceSelection(filter1, selectedEntryId)
        assertEquals("pkgB", resolved1?.id)

        // Filter excludes pkgB -> falls back to pkgA
        val filter2 = entries.filter { it.id.contains("pkgA") }
        val resolved2 = resolveMarketplaceSelection(filter2, selectedEntryId)
        assertEquals("pkgA", resolved2?.id)

        // Filter matches none -> resolves to null
        val filter3 = entries.filter { it.id.contains("none") }
        val resolved3 = resolveMarketplaceSelection(filter3, selectedEntryId)
        assertNull(resolved3)
    }
}
