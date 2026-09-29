/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.configuration

import org.ide.lti.core.model.run.StageId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ValidationErrorTest {

    @Test
    fun testValidationReportKeepsDeferredChecksSeparateFromErrors() {
        val error = ValidationError(
            stageId = StageId.FIRMWARE_EXTRACTION,
            objectId = "obj-1",
            fieldPath = "archive.path",
            code = "FILE_NOT_FOUND",
            severity = Severity.ERROR,
            message = "Archive does not exist",
            remediation = "Provide valid file",
        )
        val deferredCheck = DeferredArtifactCheck(
            stageId = StageId.DEBLOAT,
            description = "Ensure system partition image exists",
            requiredArtifactRelPath = "images/system.img",
        )

        val report = ValidationReport(
            errors = listOf(error),
            deferredArtifactChecks = listOf(deferredCheck),
        )

        assertEquals(1, report.errors.size)
        assertEquals(error, report.errors.first())

        assertEquals(1, report.deferredArtifactChecks.size)
        assertEquals(deferredCheck, report.deferredArtifactChecks.first())
    }

    @Test
    fun testSeverityOrderingAndBlockingErrorsHelper() {
        val infoError = ValidationError(
            stageId = null,
            objectId = null,
            fieldPath = "name",
            code = "INFO_CODE",
            severity = Severity.INFO,
            message = "Info message",
        )
        val warningError = ValidationError(
            stageId = null,
            objectId = null,
            fieldPath = "desc",
            code = "WARN_CODE",
            severity = Severity.WARNING,
            message = "Warning message",
        )
        val blockingError = ValidationError(
            stageId = null,
            objectId = null,
            fieldPath = "url",
            code = "BLOCKING_CODE",
            severity = Severity.ERROR,
            message = "Error message",
        )

        // Empty report has no blocking errors
        assertFalse(ValidationReport().hasBlockingErrors)
        assertFalse(ValidationReport().hasBlockingErrors())

        // INFO only -> not blocking
        assertFalse(ValidationReport(errors = listOf(infoError)).hasBlockingErrors)

        // WARNING only -> not blocking
        assertFalse(ValidationReport(errors = listOf(warningError)).hasBlockingErrors)

        // INFO + WARNING -> not blocking
        assertFalse(ValidationReport(errors = listOf(infoError, warningError)).hasBlockingErrors)

        // ERROR -> blocking
        assertTrue(ValidationReport(errors = listOf(blockingError)).hasBlockingErrors)

        // Combined with ERROR -> blocking
        assertTrue(ValidationReport(errors = listOf(infoError, warningError, blockingError)).hasBlockingErrors)

        // Severity enum ordering: ERROR is highest priority (ordinal 0)
        assertTrue(Severity.ERROR < Severity.WARNING)
        assertTrue(Severity.WARNING < Severity.INFO)
    }

    @Test
    fun testArtifactFreshnessIsDistinctEnum() {
        assertEquals(4, ArtifactFreshness.entries.size)
        assertTrue(ArtifactFreshness.entries.contains(ArtifactFreshness.FRESH))
        assertTrue(ArtifactFreshness.entries.contains(ArtifactFreshness.STALE))
        assertTrue(ArtifactFreshness.entries.contains(ArtifactFreshness.MISSING))
        assertTrue(ArtifactFreshness.entries.contains(ArtifactFreshness.UNVERIFIED))
    }
}
