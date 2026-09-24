/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import org.ide.lti.core.domain.pipeline.configuration.StageSettingsValidator
import org.ide.lti.core.domain.pipeline.configuration.ValidationReport
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.target.TargetDevice
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings

/** In-memory, user-editable settings above the immutable persisted snapshot. */
public data class WorkspacePipelineDraft(
    val workspaceId: String,
    val draftRevision: Long,
    val baseSnapshotId: String?,
    val baseSnapshotDigest: String?,
    val acquisition: AcquisitionSettings,
    val extraction: ExtractionSettings,
    val assembly: AssemblySettings,
    val debloat: DebloatSettings,
    val customization: CustomizationSettings,
    val build: BuildSettings,
    val release: ReleaseSettings,
    val publish: PublishSettings,
    val dirtyPaths: Set<String>,
    val validationDiagnostics: ValidationReport,
    private val dirtyPathRevisions: Map<String, Long> = emptyMap(),
    private val baseSnapshot: ConfigurationSnapshot? = null,
) {
    /** Signing is persisted as part of the build settings, so it has no separate snapshot field. */
    public val signing get() = build.signing

    public fun withAcquisition(value: AcquisitionSettings): WorkspacePipelineDraft = withSettings("acquisition") {
        copy(acquisition = value)
    }

    public fun withExtraction(value: ExtractionSettings): WorkspacePipelineDraft = withSettings("extraction") {
        copy(extraction = value)
    }

    public fun withAssembly(value: AssemblySettings): WorkspacePipelineDraft = withSettings("assembly") {
        copy(assembly = value)
    }

    public fun withDebloat(value: DebloatSettings): WorkspacePipelineDraft = withSettings("debloat") {
        copy(debloat = value)
    }

    public fun withCustomization(value: CustomizationSettings): WorkspacePipelineDraft = withSettings("customization") {
        copy(customization = value)
    }

    public fun withBuild(value: BuildSettings): WorkspacePipelineDraft = withSettings("build") {
        copy(build = value)
    }

    public fun withSigning(value: org.ide.lti.core.model.workspace.SigningPolicy): WorkspacePipelineDraft =
        withBuild(build.copy(signing = value))

    public fun withRelease(value: ReleaseSettings): WorkspacePipelineDraft = withSettings("release") {
        copy(release = value)
    }

    public fun withPublish(value: PublishSettings): WorkspacePipelineDraft = withSettings("publish") {
        copy(publish = value)
    }

    /** Reconciles an asynchronous save without replacing edits made after that save started. */
    public fun reconcileAfterSave(
        committedSnapshot: ConfigurationSnapshot,
        savedAsOfRevision: Long,
    ): WorkspacePipelineDraft {
        require(committedSnapshot.workspaceId == workspaceId) {
            "Snapshot workspace ${committedSnapshot.workspaceId} does not match draft workspace $workspaceId"
        }
        val isNewer = draftRevision > savedAsOfRevision
        val reconciled = if (!isNewer) {
            copyFromSnapshot(committedSnapshot)
        } else {
            copy(
                acquisition = reconcile(acquisition, committedSnapshot.acquisition, "acquisition", savedAsOfRevision),
                extraction = reconcile(extraction, committedSnapshot.extraction, "extraction", savedAsOfRevision),
                assembly = reconcile(assembly, committedSnapshot.assembly, "assembly", savedAsOfRevision),
                debloat = reconcile(debloat, committedSnapshot.debloat, "debloat", savedAsOfRevision),
                customization = reconcile(
                    customization,
                    committedSnapshot.customization,
                    "customization",
                    savedAsOfRevision,
                ),
                build = reconcile(build, committedSnapshot.build, "build", savedAsOfRevision),
                release = reconcile(release, committedSnapshot.release, "release", savedAsOfRevision),
                publish = reconcile(publish, committedSnapshot.publish, "publish", savedAsOfRevision),
                dirtyPaths = dirtyPaths.filterTo(mutableSetOf()) { dirtyPathRevisions[it] ?: 0L > savedAsOfRevision },
                dirtyPathRevisions = dirtyPathRevisions.filterValues { it > savedAsOfRevision },
            )
        }
        return reconciled.copy(
            baseSnapshotId = committedSnapshot.id,
            baseSnapshotDigest = committedSnapshot.digest,
            baseSnapshot = committedSnapshot,
        ).revalidate()
    }

    internal fun revalidateFor(target: TargetDevice?): WorkspacePipelineDraft =
        copy(validationDiagnostics = diagnostics(this, target))

    internal fun resetToBase(): WorkspacePipelineDraft = baseSnapshot?.let { copyFromSnapshot(it) }
        ?: copyFromDefaults()

    private fun <T> reconcile(draftValue: T, committedValue: T, path: String, savedAsOfRevision: Long): T =
        if ((dirtyPathRevisions[path] ?: 0L) <= savedAsOfRevision) committedValue else draftValue

    private fun withSettings(path: String, update: WorkspacePipelineDraft.() -> WorkspacePipelineDraft) = update().copy(
        dirtyPaths = dirtyPaths + path,
        dirtyPathRevisions = dirtyPathRevisions + (path to draftRevision),
    )

    private fun copyFromSnapshot(snapshot: ConfigurationSnapshot): WorkspacePipelineDraft = copy(
        acquisition = snapshot.acquisition,
        extraction = snapshot.extraction,
        assembly = snapshot.assembly,
        debloat = snapshot.debloat,
        customization = snapshot.customization,
        build = snapshot.build,
        release = snapshot.release,
        publish = snapshot.publish,
        dirtyPaths = emptySet(),
        dirtyPathRevisions = emptyMap(),
        baseSnapshot = snapshot,
    )

    internal fun copyFromDefaults(): WorkspacePipelineDraft = copy(
        acquisition = defaultAcquisition(),
        extraction = ExtractionSettings(),
        assembly = AssemblySettings(),
        debloat = DebloatSettings(),
        customization = CustomizationSettings(),
        build = BuildSettings(),
        release = ReleaseSettings(),
        publish = PublishSettings(),
        baseSnapshotId = null,
        baseSnapshotDigest = null,
        dirtyPaths = emptySet(),
        dirtyPathRevisions = emptyMap(),
        baseSnapshot = null,
    )

    private fun revalidate(): WorkspacePipelineDraft = revalidateFor(null)

    public companion object {
        fun diagnostics(draft: WorkspacePipelineDraft, target: TargetDevice?): ValidationReport {
            val reports = listOf(
                "acquisition" to StageSettingsValidator.validateAcquisition(draft.acquisition, target),
                "extraction" to StageSettingsValidator.validateExtraction(draft.extraction, target),
                "assembly" to StageSettingsValidator.validateAssembly(draft.assembly, target),
                "debloat" to StageSettingsValidator.validateDebloat(draft.debloat),
                "customization" to StageSettingsValidator.validateCustomization(draft.customization),
                "build" to StageSettingsValidator.validateBuild(draft.build, target),
                "release" to StageSettingsValidator.validateRelease(draft.release, target),
                "publish" to StageSettingsValidator.validatePublish(draft.publish),
            )
            return ValidationReport(
                errors = reports.flatMap { (prefix, report) ->
                    report.errors.map { error -> error.copy(fieldPath = "$prefix.${error.fieldPath}") }
                },
            )
        }

        fun defaultAcquisition(): AcquisitionSettings = AcquisitionSettings(
            region = DefaultTargetCatalog.PQ84P01_DEFAULT.availableRegions.first(),
            firmware = DefaultTargetCatalog.PQ84P01_GLOBAL_FIRMWARES.first(),
        )

        public fun create(
            workspaceId: String,
            baseSnapshot: ConfigurationSnapshot?,
            target: TargetDevice? = null,
        ): WorkspacePipelineDraft {
            val snapshot = baseSnapshot
            require(snapshot == null || snapshot.workspaceId == workspaceId) {
                "Snapshot workspace ${snapshot?.workspaceId} does not match draft workspace $workspaceId"
            }
            val draft = if (snapshot == null) {
                WorkspacePipelineDraft(
                    workspaceId = workspaceId,
                    draftRevision = 0L,
                    baseSnapshotId = null,
                    baseSnapshotDigest = null,
                    acquisition = defaultAcquisition(),
                    extraction = ExtractionSettings(),
                    assembly = AssemblySettings(),
                    debloat = DebloatSettings(),
                    customization = CustomizationSettings(),
                    build = BuildSettings(),
                    release = ReleaseSettings(),
                    publish = PublishSettings(),
                    dirtyPaths = emptySet(),
                    validationDiagnostics = ValidationReport(),
                    baseSnapshot = null,
                )
            } else {
                WorkspacePipelineDraft(
                    workspaceId = snapshot.workspaceId,
                    draftRevision = 0L,
                    baseSnapshotId = snapshot.id,
                    baseSnapshotDigest = snapshot.digest,
                    acquisition = snapshot.acquisition,
                    extraction = snapshot.extraction,
                    assembly = snapshot.assembly,
                    debloat = snapshot.debloat,
                    customization = snapshot.customization,
                    build = snapshot.build,
                    release = snapshot.release,
                    publish = snapshot.publish,
                    dirtyPaths = emptySet(),
                    validationDiagnostics = ValidationReport(),
                    baseSnapshot = snapshot,
                )
            }
            return draft.copy(validationDiagnostics = diagnostics(draft, target))
        }
    }
}
