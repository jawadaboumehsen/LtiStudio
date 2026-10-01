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

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.configuration.ValidationError
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.run.StageId
import java.nio.file.Path

@Serializable
enum class TrustDecision {
    TRUSTED_SIGNATURE,
    TRUSTED_UNVERIFIED,
    UNTRUSTED,
}

data class InstallRequest(val sourceLabel: String, val expectedDigest: String?, val trust: TrustDecision)

sealed interface InstallState {
    data object SourceSelected : InstallState
    data object Downloading : InstallState
    data object Quarantined : InstallState
    data object Verified : InstallState
    data class Installed(val record: InstalledPackage) : InstallState
    data class Failed(val report: ValidationReport) : InstallState
    data object Cancelled : InstallState
}

@Serializable
data class PackageIdentity(val publisher: String, val id: String, val version: String, val contentDigest: String)

val PackageIdentity.key: String
    get() = "$publisher:$id"

data class PackageInspection(
    val identity: PackageIdentity?,
    val hasSignature: Boolean,
    val report: ValidationReport,
    val dependencies: List<DependencyRef> = emptyList(),
    val conflicts: List<DependencyRef> = emptyList(),
)

@Serializable
data class InstalledRecord(
    val identity: PackageIdentity,
    val trust: TrustDecision,
    val enabledInWorkspaces: List<String>,
    val revoked: Boolean,
    val archived: Boolean,
    val dependencies: List<DependencyRef>,
    val conflicts: List<DependencyRef>,
)

val InstalledRecord.key: String
    get() = "${identity.publisher}:${identity.id}"

fun InstalledRecord.toInstalledPackage(): InstalledPackage = InstalledPackage(
    publisher = identity.publisher,
    id = identity.id,
    version = identity.version,
    contentDigest = identity.contentDigest,
    dependencies = dependencies,
    conflicts = conflicts,
)

interface PluginInstallPort {
    suspend fun quarantine(sourceLabel: String): Path?
    suspend fun inspect(quarantined: Path): PackageInspection
    suspend fun promote(quarantined: Path, identity: PackageIdentity): Boolean
    suspend fun installed(): List<InstalledRecord>
    suspend fun remove(identity: PackageIdentity): Boolean
    suspend fun updateRecord(record: InstalledRecord): Boolean = true
}

class InstallPluginUseCase(private val port: PluginInstallPort) {
    companion object {
        const val INSTALL_NOT_TRUSTED: String = "INSTALL_NOT_TRUSTED"
        const val DIGEST_MISMATCH: String = "DIGEST_MISMATCH"
        const val SAME_VERSION_DIFFERENT_CONTENT: String = "SAME_VERSION_DIFFERENT_CONTENT"
        const val INSTALL_REFUSED: String = "INSTALL_REFUSED"
    }

    suspend operator fun invoke(request: InstallRequest): Flow<InstallState> = flow {
        emit(InstallState.SourceSelected)

        if (request.trust == TrustDecision.UNTRUSTED) {
            emit(InstallState.Failed(singleError(request.sourceLabel, "trust", INSTALL_NOT_TRUSTED, "Untrusted")))
            return@flow
        }

        emit(InstallState.Downloading)
        val quarantined = port.quarantine(request.sourceLabel)
        if (quarantined == null) {
            emit(InstallState.Cancelled)
            return@flow
        }

        emit(InstallState.Quarantined)
        val inspection = port.inspect(quarantined)
        if (inspection.report.hasBlockingErrors()) {
            emit(InstallState.Failed(inspection.report))
            return@flow
        }

        val identity = inspection.identity ?: run {
            emit(InstallState.Failed(inspection.report))
            return@flow
        }

        if (request.expectedDigest != null && request.expectedDigest != identity.contentDigest) {
            val msg = "Package digest mismatch: expected '${request.expectedDigest}', got '${identity.contentDigest}'"
            emit(InstallState.Failed(singleError(identity.key, "expectedDigest", DIGEST_MISMATCH, msg)))
            return@flow
        }

        val existing = port.installed()
        val sameVersionDiffDigest = existing.firstOrNull {
            it.identity.publisher == identity.publisher &&
                it.identity.id == identity.id &&
                it.identity.version == identity.version &&
                it.identity.contentDigest != identity.contentDigest
        }
        if (sameVersionDiffDigest != null) {
            val msg = "Package '${identity.key}' version '${identity.version}' already installed with different digest"
            emit(InstallState.Failed(singleError(identity.key, "contentDigest", SAME_VERSION_DIFFERENT_CONTENT, msg)))
            return@flow
        }

        emit(InstallState.Verified)
        val promoted = port.promote(quarantined, identity)
        if (!promoted) {
            val msg = "Store refused package promotion for '${identity.key}'"
            emit(InstallState.Failed(singleError(identity.key, "promote", INSTALL_REFUSED, msg)))
            return@flow
        }

        val record = InstalledPackage(
            publisher = identity.publisher,
            id = identity.id,
            version = identity.version,
            contentDigest = identity.contentDigest,
            dependencies = inspection.dependencies,
            conflicts = inspection.conflicts,
        )
        emit(InstallState.Installed(record))
    }

    private fun singleError(objectId: String, fieldPath: String, code: String, message: String): ValidationReport =
        ValidationReport(
            errors = listOf(
                ValidationError(
                    stageId = StageId.MODULE_APPLICATION,
                    objectId = objectId,
                    fieldPath = fieldPath,
                    code = code,
                    severity = Severity.ERROR,
                    message = message,
                ),
            ),
        )
}
