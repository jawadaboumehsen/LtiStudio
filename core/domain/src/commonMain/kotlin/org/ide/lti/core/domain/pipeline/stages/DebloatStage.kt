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
import org.ide.lti.core.domain.debloat.PackageMentions
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
                PipelineStep.Tool(
                    command = stepCommand(
                        "sh",
                        listOf("-c", PackageMentions.SCRIPT, "sh", ctx.abs("work")),
                        ctx.wsPath,
                    ),
                    label = "read package mentions",
                    resultKey = MENTIONS_KEY,
                ),
            ) + ctx.value(APPS_KEY)?.let { apps ->
                ctx.value(MENTIONS_KEY)?.let { mentions ->
                    stepsFor(
                        ctx,
                        DebloatInventory.parse(listing),
                        AppManifests.parse(apps),
                        PackageMentions.parse(mentions),
                    )
                }
            }.orEmpty()
        }.orEmpty()
    }

    /** Once the inventory and the manifests are read: refuse a bad pick, or remove the picked apps. */
    private fun stepsFor(
        ctx: StageContext,
        inventory: List<DebloatCandidate>,
        apps: Map<String, AppFacts>,
        mentions: Map<String, PackageMentions.Mentions>,
    ): List<PipelineStep> {
        val byApp = DebloatRisk.byApp(inventory, apps)
        // Package names from the manifests, so a `pkg:` selector finds its app.
        val named = inventory.map { it.copy(packageId = it.packageId ?: byApp[DebloatRisk.selectorOf(it)]?.packageId) }
        val resolution = DebloatResolver.resolve(
            settings = ctx.snapshot.debloat,
            inventory = named,
            presetSelectors = emptyList(),
            protectedSelectors = DebloatInventory.protectedSelectors(named),
            targetPartitions = named.map { it.partition }.toSet(),
        )
        // The risk of what is really removed: after keep rules, not of the raw picks.
        val removed = (resolution as? DebloatResolution.Resolved)?.decision?.removals
            ?.map { "${it.partition}:${it.relativePath}" }?.toSet().orEmpty()
        val blocked = DebloatRisk.assess(named, byApp, removed)
            .filter { (selector, risk) -> selector in removed && risk.blockers.isNotEmpty() }
        if (blocked.isNotEmpty()) {
            return listOf(
                PipelineStep.Check("debloat risk") {
                    "DEBLOAT_BREAKS_DEVICE: " + blocked.entries.joinToString("; ") { (selector, risk) ->
                        "$selector: ${risk.blockers.joinToString(" ")}"
                    } + " Remove these picks in Debloat."
                },
            )
        }
        return when (resolution) {
            is DebloatResolution.Rejected -> listOf(
                PipelineStep.Check("debloat selection") {
                    resolution.report.errors.joinToString("; ") { "${it.code}: ${it.message}" }
                },
            )

            is DebloatResolution.Resolved -> {
                // Config files that only name the removed apps (privapp-permissions-x.xml, …) go with them.
                val removedPackages = resolution.decision.removals
                    .mapNotNull { byApp["${it.partition}:${it.relativePath}"]?.packageId }.toSet()
                val orphans = PackageMentions.orphaned(mentions, removedPackages)
                    .map { it.substringBefore('/') to it.substringAfter('/') }
                removalSteps(ctx, resolution.decision, orphans)
            }
        }
    }

    private fun removalSteps(
        ctx: StageContext,
        decision: DebloatDecision,
        orphans: List<Pair<String, String>>,
    ): List<PipelineStep> {
        val steps = mutableListOf<PipelineStep>()
        // (partition, path inside it) of every app and config file the run removes.
        val items = decision.removals.map { it.partition to it.relativePath } + orphans
        val byPartition = items.groupBy({ it.first }, { it.second })
        if (items.isNotEmpty()) {
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
            for ((partition, path) in items) {
                val src = ctx.abs("work/$partition/$path")
                val dest = ctx.abs(backupPath(partition, path))
                steps += PipelineStep.Tool(
                    stepCommand("mkdir", listOf("-p", dest.substringBeforeLast('/')), ctx.wsPath),
                    "mkdir backup $partition:$path",
                )
                steps += PipelineStep.Tool(
                    stepCommand("cp", listOf("-a", "-T", src, dest), ctx.wsPath),
                    "backup $partition:$path",
                )
            }
            val paths = items.map { (partition, path) -> ctx.abs("work/$partition/$path") }
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-rf", "--") + paths, ctx.wsPath), "remove apps")

            val createdAt = ctx.longValue(RuntimeKeys.RUN_TIMESTAMP)?.let { Instant.fromEpochSeconds(it).toString() }
                ?: "unknown"
            val points = items.map { (partition, path) ->
                RestorePoint(
                    id = "debloat:$partition:$path",
                    tool = TOOL,
                    itemPath = "$partition/$path",
                    createdAt = createdAt,
                    backupPath = backupPath(partition, path),
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
        byPartition.forEach { (partition, paths) ->
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
        public const val MENTIONS_KEY: String = "debloat.mentions"
        public const val TOOL: String = "debloat"
        public const val BACKUP_DIR: String = "restore/debloat"

        /** Copies of `work/configs/{fs_config,file_context}-<p>` taken before the last debloat pruned them. */
        public const val SIDECAR_BACKUP_DIR: String = "$BACKUP_DIR/configs"

        public fun backupPath(partition: String, relativePath: String): String = "$BACKUP_DIR/$partition/$relativePath"

        /** What a run removed, from [RESULT]; null for a disabled run's result or unreadable text. */
        public fun parseResult(text: String): DebloatDecision? =
            runCatching { ConfigurationSnapshot.canonicalJson.decodeFromString<DebloatDecision>(text) }.getOrNull()
    }
}
