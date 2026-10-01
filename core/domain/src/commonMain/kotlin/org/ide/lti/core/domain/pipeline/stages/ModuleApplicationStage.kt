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

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.ide.lti.core.domain.pipeline.CacheKeys
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.StageDefinition
import org.ide.lti.core.domain.plugin.ConcreteMutationPlan
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.EmptyPluginCatalog
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginPlanResolver
import org.ide.lti.core.domain.plugin.ResolutionInput
import org.ide.lti.core.domain.plugin.ResolutionResult
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PluginLockfileEntry

/**
 * Stage 4 — Module Application.
 *
 * Resolves enabled plugin templates against customization settings, target facts, and upstream digests,
 * and emits a planned mutation manifest and verified summary.
 *
 * The stage remains pure and plans only; the actual filesystem and bytecode mutations are performed
 * by the execution adapter wired to the "modules applied" Check step by the pipeline orchestrator or host.
 */
public class ModuleApplicationStage(private val catalog: PluginCatalogPort = EmptyPluginCatalog) : StageDefinition {
    override val id: StageId = StageId.MODULE_APPLICATION
    override val requiredToolIds: Set<String> = emptySet()

    override fun computeCacheKey(ctx: StageContext, previousStageCacheKey: String): String {
        val payload = ConfigurationSnapshot.canonicalJson.encodeToString(ctx.snapshot.customization)
        return CacheKeys.sha256(previousStageCacheKey + payload)
    }

    override fun outputs(ctx: StageContext): List<String> = listOf(APPLIED_PATH)

    override fun plan(ctx: StageContext): List<PipelineStep> {
        val enabledPackages = ctx.snapshot.customization.enabledPackages
        if (enabledPackages.isEmpty()) {
            return listOf(
                PipelineStep.WriteFile(
                    relPath = APPLIED_PATH,
                    content = EMPTY_APPLIED_JSON.encodeToByteArray(),
                ),
            )
        }

        val outcome = resolveAllPackages(ctx, enabledPackages)
        return outcome.failureStep?.let { listOf(it) }
            ?: buildPlanSteps(enabledPackages, outcome.resolvedEntries, outcome.resolvedPlans)
    }

    private sealed interface PackageOutcome {
        data class Success(val entry: PluginLockfileEntry, val plan: ConcreteMutationPlan) : PackageOutcome
        data class Failure(val step: PipelineStep.Check) : PackageOutcome
    }

    private data class ResolutionOutcome(
        val resolvedEntries: List<PluginLockfileEntry>,
        val resolvedPlans: List<ConcreteMutationPlan>,
        val failureStep: PipelineStep.Check?,
    )

    private fun resolveAllPackages(ctx: StageContext, enabledPackages: List<String>): ResolutionOutcome {
        val resolvedEntries = mutableListOf<PluginLockfileEntry>()
        val resolvedPlans = mutableListOf<ConcreteMutationPlan>()

        for (pkg in enabledPackages) {
            when (val outcome = resolveSinglePackage(ctx, pkg)) {
                is PackageOutcome.Failure -> return ResolutionOutcome(emptyList(), emptyList(), outcome.step)
                is PackageOutcome.Success -> {
                    resolvedEntries += outcome.entry
                    resolvedPlans += outcome.plan
                }
            }
        }

        return ResolutionOutcome(resolvedEntries, resolvedPlans, null)
    }

    private fun resolveSinglePackage(ctx: StageContext, pkg: String): PackageOutcome {
        val entry = ctx.snapshot.customization.lockfile.entries.firstOrNull { e ->
            "${e.publisher}:${e.id}" == pkg || e.id == pkg
        }
        val template = entry?.let { catalog.templateFor(it.publisher, it.id, it.version) }

        if (entry == null || template == null) {
            val failure = PipelineStep.Check(
                label = "Resolve plugin $pkg",
                verify = {
                    "UNRESOLVED_PLUGIN_CONTENT: Package '$pkg' has no resolved lockfile entry or catalog template"
                },
            )
            return PackageOutcome.Failure(failure)
        }

        return resolveTemplate(ctx, pkg, entry, template)
    }

    private fun resolveTemplate(
        ctx: StageContext,
        pkg: String,
        entry: PluginLockfileEntry,
        template: PluginPlanTemplate,
    ): PackageOutcome {
        val targetFacts = mapOf(
            "device.id" to ctx.target.id,
            "device.codename" to ctx.target.codename,
            "android.version" to ctx.snapshot.acquisition.firmware.androidVersion,
            "firmware.version" to ctx.snapshot.acquisition.firmware.version,
        )

        // previousStageCacheKey is not passed to plan(ctx), so upstreamCheckpointDigest falls back to
        // ctx.value("debloat.result.digest").orEmpty(), or "" if absent (e.g. debloat disabled or not yet run).
        val upstreamCheckpointDigest = ctx.value("debloat.result.digest").orEmpty()

        val settingValues = ctx.snapshot.customization.settings[pkg]
            ?: ctx.snapshot.customization.settings[entry.id]
            ?: ctx.snapshot.customization.settings["${entry.publisher}:${entry.id}"]
            ?: emptyMap()

        val contentDigest = entry.contentDigest.ifEmpty {
            catalog.contentDigestFor(entry.publisher, entry.id, entry.version).orEmpty()
        }

        val input = ResolutionInput(
            template = template,
            packageContentDigest = contentDigest,
            settingValues = settingValues,
            targetFacts = targetFacts,
            toolCapabilities = emptySet(),
            requiredCapabilities = emptyList(),
            upstreamCheckpointDigest = upstreamCheckpointDigest,
        )

        return when (val resolution = PluginPlanResolver.resolve(input)) {
            is ResolutionResult.Rejected -> {
                val firstError = resolution.report.errors.firstOrNull()
                val code = firstError?.code ?: "PLUGIN_RESOLUTION_FAILED"
                val message = firstError?.message ?: "Plugin plan resolution failed"
                PackageOutcome.Failure(
                    PipelineStep.Check(
                        label = "Resolve plan $pkg",
                        verify = { "$code: $message" },
                    ),
                )
            }
            is ResolutionResult.Resolved -> PackageOutcome.Success(entry, resolution.plan)
        }
    }

    private fun buildPlanSteps(
        enabledPackages: List<String>,
        resolvedEntries: List<PluginLockfileEntry>,
        resolvedPlans: List<ConcreteMutationPlan>,
    ): List<PipelineStep> {
        val allOperations = resolvedPlans.flatMap { it.operations }
        val appliedPlan = AppliedModulePlan(
            lockfileEntries = resolvedEntries,
            operationIds = allOperations.map { it.id },
            operations = allOperations.map { it.toSummary() },
            resolvedPlanDigests = resolvedPlans.map { it.digest },
        )

        val planJson = ConfigurationSnapshot.canonicalJson.encodeToString(appliedPlan)
        val summaryJson = ConfigurationSnapshot.canonicalJson.encodeToString(
            AppliedModuleSummary(
                enabled = enabledPackages,
                operations = allOperations.size,
            ),
        )

        return listOf(
            PipelineStep.WriteFile(
                relPath = PLAN_PATH,
                content = planJson.encodeToByteArray(),
            ),
            PipelineStep.Check(
                label = "modules applied",
                verify = { runtimeValues ->
                    val applyResult = runtimeValues["modules.apply.result"]
                    if (applyResult == "ok") {
                        null
                    } else {
                        "Module application did not succeed: ${applyResult ?: "no result recorded"}"
                    }
                },
            ),
            PipelineStep.WriteFile(
                relPath = APPLIED_PATH,
                content = summaryJson.encodeToByteArray(),
            ),
        )
    }

    private companion object {
        const val APPLIED_PATH = "work/modules/applied.json"
        const val PLAN_PATH = "work/modules/plan.json"
        const val EMPTY_APPLIED_JSON = """{"enabled":[],"operations":0}"""

        fun ConcreteOperation.toSummary(): AppliedOperationSummary = when (this) {
            is ConcreteOperation.Copy -> AppliedOperationSummary(
                id = id,
                kind = "Copy",
                target = "${destination.partition}/${destination.relativePath}",
            )
            is ConcreteOperation.Replace -> AppliedOperationSummary(
                id = id,
                kind = "Replace",
                target = "${destination.partition}/${destination.relativePath}",
            )
            is ConcreteOperation.Delete -> AppliedOperationSummary(
                id = id,
                kind = "Delete",
                target = "${target.partition}/${target.relativePath}",
            )
            is ConcreteOperation.PropertyPatch -> AppliedOperationSummary(
                id = id,
                kind = "PropertyPatch",
                target = "${target.partition}/${target.relativePath}",
            )
            is ConcreteOperation.TextPatch -> AppliedOperationSummary(
                id = id,
                kind = "TextPatch",
                target = "${target.partition}/${target.relativePath}",
            )
            is ConcreteOperation.ApkJarPatch -> AppliedOperationSummary(
                id = id,
                kind = "ApkJarPatch",
                target = "${target.partition}/${target.relativePath}",
            )
            is ConcreteOperation.CompiledClassMerge -> AppliedOperationSummary(
                id = id,
                kind = "CompiledClassMerge",
                target = "${target.partition}/${target.relativePath}",
            )
            is ConcreteOperation.HookInjection -> AppliedOperationSummary(
                id = id,
                kind = "HookInjection",
                target = "${target.partition}/${target.relativePath}",
            )
        }
    }
}

/**
 * Canonical record written to `work/modules/plan.json` containing the resolved operations and digests.
 */
@Serializable
public data class AppliedModulePlan(
    val lockfileEntries: List<PluginLockfileEntry>,
    val operationIds: List<String>,
    val operations: List<AppliedOperationSummary>,
    val resolvedPlanDigests: List<String>,
)

/**
 * Summary descriptor of a single applied operation within [AppliedModulePlan].
 */
@Serializable
public data class AppliedOperationSummary(val id: String, val kind: String, val target: String)

@Serializable
private data class AppliedModuleSummary(val enabled: List<String>, val operations: Int)
