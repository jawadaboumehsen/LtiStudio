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

import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.AbOtaSteps
import org.ide.lti.core.domain.pipeline.stages.BuildChecks
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.Presence
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.run.VerificationState
import org.ide.lti.core.model.workspace.PackageType

/** Collects output artifacts and persists run receipts on completion. */
internal class PipelineArtifactCollector(private val executionPort: PipelineExecutionPort) {
    suspend fun writeRecord(ctx: StageContext, runId: String, executedStages: Set<StageId>) {
        val signing = ctx.snapshot.build.signing.takeIf { StageId.BUILD_FLASHABLE_ZIP in executedStages }
        val args = listOf(
            "-c",
            RunRecord.WRITE_SCRIPT,
            "sh",
            ctx.abs("."),
            RunRecord.dir(runId),
            if (StageId.MODULE_APPLICATION in executedStages) "1" else "0",
            if (StageId.DEBLOAT in executedStages) "1" else "0",
            signing?.let { ctx.abs("${it.platformKeyRef}.x509.pem") }.orEmpty(),
            signing?.let { ctx.abs(it.avbKeyRef) }.orEmpty(),
        )
        runCatchingCancellable {
            executionPort.execute(ctx.workspace, StepCommand("sh", args, ctx.workspace.linuxPath))
        }
    }

    fun collectArtifacts(ctx: StageContext, plannedStages: List<StageId>): List<Artifact> {
        val zipRel = BuildFlashableZipStage.zipRelPath(ctx)
        val sha = ctx.value(RuntimeKeys.ZIP_SHA256)?.digestToken()
        // Only what this run built: a run stopped before Build & Sign has no package and no manifest.
        if (StageId.BUILD_FLASHABLE_ZIP !in plannedStages) return emptyList()
        val type = ctx.snapshot.build.packageType
        // With both packages the flashable zip is the main one and the OTA comes with it.
        val ota = listOfNotNull(
            Artifact(
                ArtifactKind.AB_OTA_ZIP,
                ctx.abs(BuildFlashableZipStage.otaRelPath(ctx)),
                ctx.longValue(AbOtaSteps.SIZE),
                ctx.value(AbOtaSteps.SHA256)?.digestToken(),
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ).takeIf { type == PackageType.BOTH },
        )
        val built = ota + listOf(
            Artifact(
                if (type == PackageType.AB_OTA) ArtifactKind.AB_OTA_ZIP else ArtifactKind.FLASHABLE_ZIP,
                ctx.abs(zipRel),
                ctx.longValue(RuntimeKeys.ZIP_SIZE),
                sha,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ),
            Artifact(
                ArtifactKind.CHECKSUM,
                ctx.abs("$zipRel.sha256"),
                null,
                sha,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            ),
        )
        val manifest = Artifact(
            ArtifactKind.OTA_MANIFEST,
            ctx.abs("out/manifest.json"),
            null,
            null,
            VerificationState.VERIFIED,
            Presence.PRESENT,
        )
        // Fastboot images zip and Odin AP: listed (and found missing) like the main package.
        val extras = BuildChecks.extraPackageFiles(ctx).map {
            Artifact(
                ArtifactKind.EXTRA_PACKAGE,
                ctx.abs(it),
                null,
                null,
                VerificationState.VERIFIED,
                Presence.PRESENT,
            )
        }
        return (if (StageId.GENERATE_OTA_MANIFEST in plannedStages) built + manifest else built) + extras
    }
}
