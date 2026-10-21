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

import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.debloat.DebloatCandidate
import org.ide.lti.core.domain.debloat.DebloatDecision
import org.ide.lti.core.domain.debloat.DebloatInventory
import org.ide.lti.core.domain.debloat.DebloatResolution
import org.ide.lti.core.domain.debloat.DebloatResolver
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.pipeline.stepCommand
import org.ide.lti.core.domain.pipeline.text.SidecarEditor
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/**
 * Stage 4. Lists the apps in the assembled work tree, resolves the user's picks against the protected list
 * ([DebloatResolver], [DebloatInventory]), deletes the picked app folders and prunes their fs_config /
 * file_context entries in one pass per partition, then records what it removed in `work/debloat/result.json`.
 * Disabled debloat still writes a no-removal result, so later stages read a verified artifact.
 */
public class DebloatStage : StageDefinition {
    override val id: StageId = StageId.DEBLOAT
    override val requiredToolIds: Set<String> = setOf("find", "rm")

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
        ) + ctx.value(INVENTORY_KEY)?.let { stepsFor(ctx, DebloatInventory.parse(it)) }.orEmpty()
    }

    /** Once the inventory is read: refuse a bad pick, or remove the picked apps. */
    private fun stepsFor(ctx: StageContext, inventory: List<DebloatCandidate>): List<PipelineStep> {
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
            val paths = decision.removals.map { ctx.abs("work/${it.partition}/${it.relativePath}") }
            steps += PipelineStep.Tool(stepCommand("rm", listOf("-rf", "--") + paths, ctx.wsPath), "remove apps")
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

        /** What a run removed, from [RESULT]; null for a disabled run's result or unreadable text. */
        public fun parseResult(text: String): DebloatDecision? =
            runCatching { ConfigurationSnapshot.canonicalJson.decodeFromString<DebloatDecision>(text) }.getOrNull()
    }
}
