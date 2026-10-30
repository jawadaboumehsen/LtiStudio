/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.stages

import kotlinx.datetime.Instant
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.debloat.AppFacts
import org.ide.lti.core.domain.debloat.AppManifests
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.debloat.DebloatResolution
import org.ide.lti.core.domain.debloat.DebloatResolver
import org.ide.lti.core.domain.debloat.DebloatRisk
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.domain.pipeline.toolkit.RestoreIndex
import org.ide.lti.core.domain.pipeline.toolkit.RestorePoint
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Stage 4. Lists the apps in the assembled work tree and reads their manifests ([AppManifests]), refuses a pick
 * whose removal would break the device ([DebloatRisk]) or that is on the protected list ([DebloatResolver],
 * [DebloatInventory]), deletes the picked app folders and prunes their fs_config /
 * file_context entries in one pass per partition, then records what it removed in `work/debloat/result.json`.
 * Disabled debloat still writes a no-removal result, so later stages read a verified artifact.
 */
public class DebloatStage : StageDefinition {
    override val id: StageId = StageId.DEBLOAT
    override val requiredToolIds: Set<String> = setOf("find", "rm", "mkdir", "cp", "touch")

    // The inventory is the previous stage's output (covered by its key); the picks are the settings.
    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String = CacheKeys.sha256(
        previousStageCacheKey + ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.debloat),
    )

    override fun outputs(ctx: StageContext): List<String> = listOf(RESULT)

    override fun plan(ctx: StageContext): List<PipelineStep> = when {
        !ctx.snapshot.debloat.enabled ->
            listOf(PipelineStep.WriteFile(RESULT, """{"enabled":false,"removed":[]}""".encodeToByteArray()))

        else -> listOf(
            PipelineStep.Tool(
                command = stepCommand("find", DebloatInventory.listingArgs(ctx.abs("work")), ctx.wsPath),
                label = "list apps",
                resultKey = INVENTORY_KEY,
            ),
        ) + ctx.value(INVENTORY_KEY)?.let { listing ->
            listOf(
                PipelineStep.Tool(
                    command = stepCommand("sh", listOf("-c", AppManifests.SCRIPT, "sh", ctx.abs("work")), ctx.wsPath),
                    label = "read app manifests",
                    resultKey = APPS_KEY,
                ),
            ) + ctx.value(APPS_KEY)?.let { stepsFor(ctx, DebloatInventory.parse(listing), AppManifests.parse(it)) }
                .orEmpty()
        }.orEmpty()
    }

    /** Once the inventory and the manifests are read: refuse a bad pick, or remove the picked apps. */
    private fun stepsFor(
        ctx: StageContext,
        inventory: List<DebloatCandidate>,
        apps: Map<String, AppFacts>,
    ): List<PipelineStep> {
        val picks = ctx.snapshot.debloat.removeSelectors.toSet()
        val blocked = DebloatRisk.assess(inventory, DebloatRisk.byApp(inventory, apps), picks)
            .filter { (selector, risk) -> selector in picks && risk.blockers.isNotEmpty() }
        if (blocked.isNotEmpty()) {
            return listOf(
                PipelineStep.Check("debloat risk") {
                    "DEBLOAT_BREAKS_DEVICE: " + blocked.entries.joinToString("; ") { (selector, risk) ->
                        "$selector: ${risk.blockers.joinToString(" ")}"
                    } + " Remove these picks in Debloat."
                },
            )
        }
        val resolution = DebloatResolver.resolve(
            settings = ctx.snapshot.debloat,
            inventory = inventory,
            presetSelectors = emptyList(),
            protectedSelectors = DebloatInventory.protectedSelectors(inventory),
            targetPartitions = inventory.map { it.partition }.toSet(),
        )
        return when (resolution) {
            is DebloatResolution.Rejected -> listOf(
                PipelineStep.Check("debloat selection") {
                    resolution.report.errors.joinToString("; ") { "${it.code}: ${it.message}" }
                },
            )

            is DebloatResolution.Resolved -> removalSteps(ctx, resolution.decision)
        }
    }

    private fun removalSteps(ctx: StageContext, decision: DebloatDecision): List<PipelineStep> {
        val steps = mutableListOf<PipelineStep>()
        val byPartition = decision.removals.groupBy { it.partition }
        if (decision.removals.isNotEmpty()) {
            // Back up before removal (FR-027, FR-038). Only the latest run's backups are kept: work/ is rebuilt
            // from the firmware on every run, so an older copy restores nothing a new run would not remove again.
            steps += PipelineStep.Tool(
                stepCommand("rm", listOf("-rf", ctx.abs(BACKUP_DIR)), ctx.wsPath),
                "clear backups",
            )
            // The sidecars too, before they are pruned: a restore puts the item's lines back from these copies.
            steps += PipelineStep.Tool(
                stepCommand("mkdir", listOf("-p", ctx.abs(SIDECAR_BACKUP_DIR)), ctx.wsPath),
                "mkdir sidecar backups",
            )
            byPartition.keys.forEach { partition ->
                steps += PipelineStep.Tool(
                    stepCommand(
                        "cp",
                        listOf(
                            "-a",
                            ctx.abs("work/configs/fs_config-$partition"),
                            ctx.abs("work/configs/file_context-$partition"),
                            ctx.abs(SIDECAR_BACKUP_DIR),
                        ),
                        ctx.wsPath,
                    ),
                    "backup sidecars $partition",
                )
            }
            for (removal in decision.removals) {
                val src = ctx.abs("work/${removal.partition}/${removal.relativePath}")
                val dest = ctx.abs(backupPath(removal.partition, removal.relativePath))
                steps += PipelineStep.Tool(
                    stepCommand("mkdir", listOf("-p", dest.substringBeforeLast('/')), ctx.wsPath),
                    "mkdir backup ${removal.partition}:${removal.relativePath}",
                )
                steps += PipelineStep.Tool(
                    stepCommand("cp", listOf("-a", "-T", src, dest), ctx.wsPath),
                    "backup ${removal.partition}:${removal.relativePath}",
                )
            }
            val paths = decision.removals.map { ctx.abs("work/${it.partition}/${it.relativePath}") }
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-rf", "--") + paths, ctx.wsPath), "remove apps")

            val createdAt = ctx.longValue(RuntimeKeys.RUN_TIMESTAMP)?.let { Instant.fromEpochSeconds(it).toString() }
                ?: "unknown"
            val points = decision.removals.map { removal ->
                RestorePoint(
                    id = "debloat:${removal.partition}:${removal.relativePath}",
                    tool = TOOL,
                    itemPath = "${removal.partition}/${removal.relativePath}",
                    createdAt = createdAt,
                    backupPath = backupPath(removal.partition, removal.relativePath),
                )
            }
            steps += PipelineStep.Tool(
                stepCommand("touch", listOf(ctx.abs(RestoreIndex.FILE)), ctx.wsPath),
                "ensure restore index",
            )
            // The workspace has one restore index (FR-038): this run's debloat points replace the previous run's.
            steps += PipelineStep.EditFile(
                RestoreIndex.FILE,
                { text -> RestoreIndex.parseOrEmpty(text).withTool(TOOL, points).toJson() },
                "record restore points",
            )
        }
        byPartition.forEach { (partition, removals) ->
            val paths = removals.map { it.relativePath }
            steps += PipelineStep.EditFile(
                "work/configs/fs_config-$partition",
                { SidecarEditor.withoutFsConfig(it, partition, paths) },
                "prune fs_config $partition",
            )
            steps += PipelineStep.EditFile(
                "work/configs/file_context-$partition",
                { SidecarEditor.withoutFileContext(it, partition, paths) },
                "prune file_context $partition",
            )
        }
        steps += PipelineStep.WriteFile(
            RESULT,
            ConfigurationSnapshot.canonicalJson.encodeToString(decision).encodeToByteArray(),
        )
        return steps
    }

    public companion object {
        public const val RESULT: String = "work/debloat/result.json"
        public const val INVENTORY_KEY: String = "debloat.inventory"
        public const val APPS_KEY: String = "debloat.apps"
        public const val TOOL: String = "debloat"
        private const val BACKUP_DIR = "restore/debloat"

        /** Copies of `work/configs/{fs_config,file_context}-<p>` taken before the last debloat pruned them. */
        public const val SIDECAR_BACKUP_DIR: String = "$BACKUP_DIR/configs"

        public fun backupPath(partition: String, relativePath: String): String = "$BACKUP_DIR/$partition/$relativePath"

        /** What a run removed, from [RESULT]; null for a disabled run's result or unreadable text. */
        public fun parseResult(text: String): DebloatDecision? =
            runCatching { ConfigurationSnapshot.canonicalJson.decodeFromString<DebloatDecision>(text) }.getOrNull()
    }
}
