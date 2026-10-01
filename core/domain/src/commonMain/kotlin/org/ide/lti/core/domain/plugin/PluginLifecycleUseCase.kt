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

import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.run.StageId

sealed interface LifecycleOutcome {
    data class Applied(val record: InstalledRecord) : LifecycleOutcome
    data class Refused(val report: ValidationReport) : LifecycleOutcome
}

class PluginLifecycleUseCase(private val port: PluginInstallPort) {
    companion object {
        const val REFERENCED_PACKAGE: String = "REFERENCED_PACKAGE"
        const val REVOKED_PACKAGE: String = "REVOKED_PACKAGE"
        const val SAME_VERSION_DIFFERENT_CONTENT: String = "SAME_VERSION_DIFFERENT_CONTENT"
        const val UNKNOWN_PACKAGE: String = "UNKNOWN_PACKAGE"

        fun canEnable(record: InstalledRecord): Boolean = !record.revoked && !record.archived
    }

    suspend fun update(current: PackageIdentity, incoming: PackageIdentity): LifecycleOutcome {
        val records = port.installed()
        val currentRecord = records.firstOrNull { it.identity == current }
        val incomingRecord = records.firstOrNull { it.identity == incoming }

        val failure = when {
            currentRecord == null ->
                ValidationFailure(current.key, "current", UNKNOWN_PACKAGE, "Current package is not installed")
            isSameVersionDiffDigest(current, incoming) ->
                ValidationFailure(incoming.key, "contentDigest", SAME_VERSION_DIFFERENT_CONTENT, "Same version diff")
            incomingRecord == null ->
                ValidationFailure(incoming.key, "incoming", UNKNOWN_PACKAGE, "Incoming package is not installed")
            incomingRecord.revoked ->
                ValidationFailure(incoming.key, "incoming", REVOKED_PACKAGE, "Incoming package is revoked")
            else -> null
        }

        return if (failure != null) {
            refuse(failure)
        } else {
            LifecycleOutcome.Applied(incomingRecord!!)
        }
    }

    suspend fun uninstall(identity: PackageIdentity, referencedBy: List<String>): LifecycleOutcome {
        val records = port.installed()
        val record = records.firstOrNull { it.identity == identity }

        val failure = when {
            record == null ->
                ValidationFailure(identity.key, "identity", UNKNOWN_PACKAGE, "Package not installed")
            record.revoked ->
                ValidationFailure(identity.key, "identity", REVOKED_PACKAGE, "Revoked package cannot be deleted")
            referencedBy.isNotEmpty() ->
                ValidationFailure(identity.key, "referencedBy", REFERENCED_PACKAGE, "Referenced package")
            !port.remove(identity) ->
                ValidationFailure(identity.key, "remove", UNKNOWN_PACKAGE, "Failed to remove package")
            else -> null
        }

        return if (failure != null) {
            refuse(failure)
        } else {
            LifecycleOutcome.Applied(record!!)
        }
    }

    suspend fun archive(identity: PackageIdentity): LifecycleOutcome {
        val records = port.installed()
        val record = records.firstOrNull { it.identity == identity }
        if (record == null) {
            return refuse(ValidationFailure(identity.key, "identity", UNKNOWN_PACKAGE, "Not installed"))
        }

        val updated = record.copy(archived = true)
        port.updateRecord(updated)
        return LifecycleOutcome.Applied(updated)
    }

    suspend fun revoke(identity: PackageIdentity, reason: String): LifecycleOutcome {
        require(reason.isNotEmpty()) { "Revocation reason must not be empty" }
        val records = port.installed()
        val record = records.firstOrNull { it.identity == identity }
        if (record == null) {
            return refuse(ValidationFailure(identity.key, "identity", UNKNOWN_PACKAGE, "Not installed"))
        }

        val updated = record.copy(revoked = true)
        port.updateRecord(updated)
        return LifecycleOutcome.Applied(updated)
    }

    fun canEnable(record: InstalledRecord): Boolean = Companion.canEnable(record)

    private fun isSameVersionDiffDigest(current: PackageIdentity, incoming: PackageIdentity): Boolean =
        incoming.publisher == current.publisher &&
            incoming.id == current.id &&
            incoming.version == current.version &&
            incoming.contentDigest != current.contentDigest

    private data class ValidationFailure(
        val objectId: String,
        val fieldPath: String,
        val code: String,
        val message: String,
    )

    private fun refuse(failure: ValidationFailure): LifecycleOutcome = LifecycleOutcome.Refused(
        ValidationReport(
            errors = listOf(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = failure.objectId,
                    fieldPath = failure.fieldPath,
                    code = failure.code,
                    severity = Severity.ERROR,
                    message = failure.message,
                ),
            ),
        ),
    )
}
