/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.provenance

import kotlinx.datetime.Instant
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatResolvedRemoval
import org.ide.lti.core.domain.debloat.RemovalReason
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.SimulatedExecutionPort
import org.ide.lti.core.domain.pipeline.stages.AppliedModulePlan
import org.ide.lti.core.domain.pipeline.stages.AppliedOperationSummary
import org.ide.lti.core.domain.pipeline.stages.BuildChecks
import org.ide.lti.core.domain.pipeline.stages.BuildFlashableZipStage
import org.ide.lti.core.domain.pipeline.toolkit.AbOtaPackage
import org.ide.lti.core.domain.pipeline.toolkit.CoverageResult
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.domain.workspace.RunRecord
import org.ide.lti.core.model.run.Artifact
import org.ide.lti.core.model.run.ArtifactKind
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.RunState
import org.ide.lti.core.model.workspace.PluginLockfileEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildProvenanceTest {

    private val fx = PipelineFixture()

    @Test
    fun packagingStepsEnforceReproducibleEpochAndDeterministicArchiveFlags() {
        val ctx = fx.globalOtaContext().let { base ->
            val values = base.runtimeValues.toMutableMap().apply {
                put(BuildFlashableZipStage.COVERAGE_RESULT_KEY, CoverageResult.encode(CoverageResult()))
                put(BuildChecks.KERNEL_KEY, "version 5.15.0\nnoconfig")
                put(BuildFlashableZipStage.PACKAGE_BUILD_PROP, SimulatedExecutionPort.STOCK_BUILD_PROP)
                put(RuntimeKeys.RUN_TIMESTAMP, "1726056000")
                put(RuntimeKeys.RUN_DATE, "20260914")
                put(RuntimeKeys.AVB_PUBKEY_SHA1, "avb123  pubkey")
                put(BuildFlashableZipStage.PLATFORM_CERT_SHA1, "plat123  cert")
                put(RuntimeKeys.ZIP_SHA256, "zipsha256  rom.zip")
                put(RuntimeKeys.ZIP_SIZE, "1000000")
                base.target.dynamicPartitions.forEach { partition ->
                    put(BuildFlashableZipStage.footeredSize(partition), "101000000")
                    put(RuntimeKeys.imageSha256(partition), "sha_$partition")
                    put(RuntimeKeys.imageSize(partition), "100000000")
                    put(RuntimeKeys.partitionSize(partition), "101000000")
                }
            }
            base.copy(runtimeValues = values)
        }

        val steps = BuildFlashableZipStage().plan(ctx).filterIsInstance<PipelineStep.Tool>()
        val touchStep = steps.firstOrNull { it.label == "normalize staging timestamps" }
        assertTrue(touchStep != null, "Expected reproducible timestamp normalization step before zip")
        assertTrue(touchStep.command.args.any { it.contains(BuildFlashableZipStage.REPRODUCIBLE_EPOCH) })

        val zipSteps = steps.filter { it.command.toolId == "zip" }
        assertTrue(zipSteps.isNotEmpty())
        assertTrue(zipSteps.all { "-X" in it.command.args })

        assertTrue(AbOtaPackage.SCRIPT.contains("write_zip_entry"))
        assertTrue(AbOtaPackage.SCRIPT.contains("2022, 1, 1, 0, 0, 0"))
        assertTrue(AbOtaPackage.SCRIPT.contains("sorted("))

        val odinContext = ctx.copy(
            snapshot = ctx.snapshot.copy(
                build = ctx.snapshot.build.copy(extraPackages = setOf(PackageKind.OdinTar.name)),
            ),
        )
        val odinTools = BuildChecks.extraPackageSteps(odinContext).filterIsInstance<PipelineStep.Tool>()
        val tarStep = odinTools.firstOrNull { it.command.toolId == "tar" }
        assertTrue(tarStep != null)
        assertTrue("--sort=name" in tarStep.command.args)
        assertTrue("--numeric-owner" in tarStep.command.args)
    }

    @Test
    fun generatorLinksAllProvenanceInputsAndVerifierDetectsVerifiedAndDriftedStates() {
        val t0 = Instant.fromEpochSeconds(1726056000L)
        val run = BuildRun(
            id = "run_sbom_1",
            workspaceId = fx.workspace.id,
            snapshotId = fx.snapshotGlobalOta.id,
            state = RunState.SUCCEEDED,
            startedAt = t0,
            endedAt = Instant.fromEpochSeconds(1726056120L),
            artifacts = listOf(
                Artifact(
                    kind = ArtifactKind.FLASHABLE_ZIP,
                    linuxPath = "${fx.workspace.linuxPath}/out/ltirom.zip",
                    sizeBytes = 8192L,
                    sha256 = "zipdigest1234",
                ),
            ),
            toolVersions = mapOf("mkfs.erofs" to "1.8.2", "avbtool" to "1.3.0"),
        )
        val record = RunRecord(
            plan = AppliedModulePlan(
                lockfileEntries = listOf(
                    PluginLockfileEntry(
                        publisher = "lti",
                        id = "mod.thermal",
                        version = "1.2.0",
                        contentDigest = "modsha256",
                    ),
                ),
                operationIds = listOf("op_1"),
                operations = listOf(
                    AppliedOperationSummary(
                        id = "op_1",
                        kind = "REPLACE_FILE",
                        target = "/vendor/etc/thermal.conf",
                        // As ModuleApplicationStage tags it: publisher:id.
                        mod = "lti:mod.thermal",
                    ),
                ),
                resolvedPlanDigests = listOf("plan_digest_1"),
            ),
            debloat = DebloatDecision(
                removals = listOf(
                    DebloatResolvedRemoval(
                        partition = "system",
                        relativePath = "app/Bloat",
                        sizeBytes = 1,
                        packageId = "com.example.bloat",
                        reason = RemovalReason.EXPLICIT_SELECTOR,
                        sidecarEntries = emptyList(),
                    ),
                ),
                kept = emptyList(),
                logicalBytesFreed = 1,
            ),
            certificate = "certsha256",
            avbKey = "avbsha256",
            present = true,
        )
        val runtimeValues = mapOf(
            RuntimeKeys.ARCHIVE_SHA256 to "fwarchivesha  stock.zip",
            // The run's own image hash is taken before the AVB footer: the SBOM must not use it.
            RuntimeKeys.imageSha256("system") to "prefootersha  system.img",
            RuntimeKeys.imageSize("system") to "1024",
        )

        val manifest = BuildProvenanceGenerator.generate(
            run = run,
            workspaceName = fx.workspace.name,
            snapshot = fx.snapshotGlobalOta,
            target = fx.targetGlobal,
            record = record,
            runtimeValues = runtimeValues,
            artifacts = run.artifacts,
            images = BuildProvenanceGenerator.parseMeasured("system\t4096\t${"a".repeat(64)}\nbroken line\n"),
        )

        assertTrue(manifest.hasValidSelfDigest())
        val system = manifest.outputs.single { it.relPath == "out/images/system.img" }
        assertEquals(4096L to "a".repeat(64), system.sizeBytes to system.sha256)
        assertEquals(1, manifest.appliedMods.single().changedFilesCount)
        assertEquals("fwarchivesha", manifest.firmwareInput.measuredArchiveSha256)
        assertEquals("avbsha256", manifest.signing.avbKeySha256Der)
        assertEquals("certsha256", manifest.signing.platformCertSha256)
        assertEquals(listOf("com.example.bloat"), manifest.removedPackages)
        assertEquals("1.8.2", manifest.toolVersions["mkfs.erofs"])

        val roundTrip = BuildProvenance.fromJson(manifest.toCanonicalJson())
        assertTrue(roundTrip.hasValidSelfDigest())

        val ok = ProvenanceVerifier.verifyFromSnapshot(
            manifest = roundTrip,
            measuredOutputs = mapOf(
                "out/images/system.img" to (4096L to "a".repeat(64)),
                "out/ltirom.zip" to (8192L to "zipdigest1234"),
            ),
            currentAvbSha256 = "avbsha256",
            currentCertSha256 = "certsha256",
            currentFirmwareSha256 = "fwarchivesha",
        )
        assertTrue(ok.passed, "Expected full SBOM verification pass, got problems=${ok.problems}")

        val drifted = ProvenanceVerifier.verifyFromSnapshot(
            manifest = roundTrip,
            measuredOutputs = mapOf(
                "out/images/system.img" to (4096L to "tampered"),
            ),
            currentAvbSha256 = "avbsha256",
            currentCertSha256 = "othercert",
            currentFirmwareSha256 = "fwarchivesha",
        )
        assertFalse(drifted.passed)
        assertTrue(drifted.problems.any { it.contains("system.img") })
        assertTrue(drifted.problems.any { it.contains("ltirom.zip") })
        assertTrue(drifted.problems.any { it.contains("Platform certificate") })

        val corruptedManifest = roundTrip.copy(workspaceName = "tampered-name")
        assertFalse(corruptedManifest.hasValidSelfDigest())
    }
}
