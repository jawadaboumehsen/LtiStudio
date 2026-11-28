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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.HostStepPort
import org.ide.lti.core.domain.pipeline.PipelineStep
import org.ide.lti.core.domain.pipeline.RuntimeKeys
import org.ide.lti.core.domain.pipeline.StageContext
import org.ide.lti.core.domain.pipeline.configuration.DeviceConfigReadiness
import org.ide.lti.core.domain.pipeline.configuration.Severity
import org.ide.lti.core.domain.pipeline.toolkit.BuildFilesystems
import org.ide.lti.core.domain.pipeline.toolkit.BuildWarningKind
import org.ide.lti.core.domain.pipeline.toolkit.PackageKind
import org.ide.lti.core.domain.plugin.ApplyModules
import org.ide.lti.core.domain.plugin.COMPONENT_EDITS_UNCHECKED
import org.ide.lti.core.domain.plugin.CheckModules
import org.ide.lti.core.domain.plugin.LabModules
import org.ide.lti.core.domain.plugin.LabRow
import org.ide.lti.core.domain.plugin.ModLab
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.workbench.LogLevel
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PackageType

/** Only an explicit zero-operation receipt proves that an existing receipt describes a clean tree. */
internal fun hasAppliedModules(receipt: String?): Boolean = receipt != null && runCatching {
    Json.parseToJsonElement(receipt).jsonObject["operations"]?.jsonPrimitive?.intOrNull
}.getOrNull() != 0

/** What fixes a finding: a settings change applied at once, or the screen where it is fixed. */
sealed interface PreflightFix {
    val label: String

    class Setting(override val label: String, val apply: (ConfigurationSnapshot) -> ConfigurationSnapshot) :
        PreflightFix

    data class Open(override val label: String, val stage: StageId, val objectId: String) : PreflightFix
}

internal fun acknowledgeVintfMismatch(id: String) = PreflightFix.Setting("Acknowledge this mismatch") {
    it.copy(build = it.build.copy(vintfMismatchAcknowledgements = it.build.vintfMismatchAcknowledgements + id))
}

/** One thing that would stop the next build ([blocks]) or that it would only warn about, with its fix. */
data class PreflightFinding(
    val code: String,
    val message: String,
    val blocks: Boolean = true,
    val fix: PreflightFix? = null,
)

data class PreflightReport(val findings: List<PreflightFinding>, val vintf: VintfAssessment)

/**
 * Will it build? Runs Build & Sign's own checks against the current settings and work/ without building: the
 * partitions left out, the filesystems, the permission and label coverage, broken links, the mount tables, what the
 * boot kernel mounts and decodes, unsigned APKs, kept partitions changed since extraction and the extra packages;
 * the vendor compatibility (VINTF); plus the device configuration's errors. Only the steps that read are run;
 * nothing in the workspace is written.
 */
class Preflight(
    private val runner: WorkbenchRunner,
    private val settings: WorkspaceSettingsStore,
    private val host: HostStepPort? = null,
    /** The installed plugins, for the mod check: each enabled mod's file anchors against the assembled tree. */
    private val catalog: PluginCatalogPort? = null,
) {
    suspend fun check(workspaceId: String): List<PreflightFinding> = report(workspaceId).findings

    suspend fun report(workspaceId: String): PreflightReport {
        val snapshot = checkNotNull(settings.current(workspaceId)) { "The workspace has no configuration yet" }
        val device = checkNotNull(settings.device(workspaceId)) { "The workspace has no device configuration" }
        val validation = DeviceConfigReadiness.check(device, snapshot).filter { it.severity == Severity.ERROR }.map {
            PreflightFinding(
                it.code,
                it.message,
                fix = it.stageId?.let { stage ->
                    val label = if (it.objectId == "integrity") "Open firmware integrity" else "Open ${it.fieldPath}"
                    PreflightFix.Open(label, stage, it.objectId.orEmpty())
                },
            )
        }
        val (found, vintf) = runner.read(workspaceId) {
            // The tools a check script needs by path (a build gets them from its pinned tools); missing: left out.
            val base = withVintfTools(StageContext(ws, snapshot, device))
            val values = mutableMapOf<String, String>()
            // After Patches, work/ holds the mods already: their anchors are checked against a tree only Assemble made.
            val patched = patched()
            val mods = when {
                catalog == null || snapshot.customization.enabledPackages.isEmpty() -> emptyList()
                patched -> listOf(MODS_NOT_CHECKED)
                else -> run(modCheck(catalog), base, values)
            }
            val checks = GENERATORS.flatMap { generator -> run(generator, base, values) }
            val output = values[BuildChecks.VINTF_KEY]
            val vintf = assessment(values)
            val acknowledged = output?.let {
                VintfCheck.acknowledgedMismatches(
                    it,
                    snapshot.build.vintfMismatchAcknowledgements,
                    VintfCheck.acknowledgementScope(snapshot),
                ).map { message -> "VINTF_ACCEPTED: Accepted mismatch. $message" }
            }.orEmpty()
            val messages = checks + mods + acknowledged
            report(LogLevel.INF, "Preflight: ${messages.size} finding(s). VINTF: ${vintf.status.label}")
            messages to vintf
        }
        return PreflightReport(validation + found.distinct().map { finding(it, snapshot) }, vintf)
    }

    /** Compatibility evidence for a prepared baseline, independent of the workspace's enabled mods. */
    suspend fun baselineCompatibility(workspaceId: String, snapshot: ConfigurationSnapshot): PreflightReport {
        val device = checkNotNull(settings.device(workspaceId)) { "The workspace has no device configuration" }
        return runner.read(workspaceId) {
            val values = mutableMapOf<String, String>()
            val messages = run(BuildChecks::vintfSteps, withVintfTools(StageContext(ws, snapshot, device)), values)
            PreflightReport(messages.distinct().map { finding(it, snapshot) }, assessment(values))
        }
    }

    private suspend fun WorkbenchRunner.Job.withVintfTools(ctx: StageContext): StageContext {
        val tools = PREFLIGHT_TOOLS.mapNotNull { id -> runCatchingCancellable { id to toolPath(id) }.getOrNull() }
        return WorkTreeAssemblyStage.afterFolding(ctx.copy(toolPaths = tools.toMap()))
    }

    private fun assessment(values: Map<String, String>): VintfAssessment {
        val exit = values[RuntimeKeys.exitOf(BuildChecks.VINTF_KEY)]?.toIntOrNull() ?: 0
        return VintfCheck.assess(values[BuildChecks.VINTF_KEY], exit)
    }

    /**
     * Plans [generator] again after each value it reads, as the build does, running only the steps that read
     * (a tool step with a result key, the coverage check); then every check's message.
     */
    private suspend fun WorkbenchRunner.Job.run(
        generator: (StageContext) -> List<PipelineStep>,
        base: StageContext,
        values: MutableMap<String, String>,
    ): List<String> {
        repeat(MAX_READS) {
            val steps = generator(base.copy(runtimeValues = values.toMap()))
            val next = steps.firstOrNull {
                (it is PipelineStep.Tool && it.resultKey != null && it.resultKey !in values) ||
                    (it is PipelineStep.Host && it.resultKey !in values)
            } ?: return steps.filterIsInstance<PipelineStep.Check>().mapNotNull { it.verify(values) }
            when (next) {
                is PipelineStep.Tool -> {
                    val key = checkNotNull(next.resultKey)
                    val result = tryRun(next.command.toolId, *next.command.args.toTypedArray())
                    values[key] = result.stdout.trim()
                    values[RuntimeKeys.exitOf(key)] = result.exitCode.toString()
                }

                is PipelineStep.Host -> values[next.resultKey] = host?.run(ws, next.request).orEmpty()

                else -> Unit
            }
        }
        return listOf("PREFLIGHT_INCOMPLETE: the checks kept asking for more facts; build to see the rest")
    }

    /**
     * The mod lab: every operation of the enabled mods, in the order they apply, checked against the assembled
     * work tree as the build would meet it; with [with], those settings instead of the saved ones (the upgrade
     * assistant checks another workspace's mods here). Nothing is written.
     */
    suspend fun lab(workspaceId: String, with: ConfigurationSnapshot? = null, decode: Boolean = false): ModLab {
        val plugins = checkNotNull(catalog) { "No plugins are installed in this app" }
        val snapshot = with ?: checkNotNull(settings.current(workspaceId)) { "The workspace has no configuration yet" }
        val device = checkNotNull(settings.device(workspaceId)) { "The workspace has no device configuration" }
        return runner.read(workspaceId) {
            if (patched()) {
                ModLab(emptyList(), listOf(MODS_NOT_CHECKED.substringAfter(": ")))
            } else {
                val values = mutableMapOf<String, String>()
                val base = WorkTreeAssemblyStage.afterFolding(StageContext(ws, snapshot, device))
                val problems = run(labPlan(plugins, decode), base, values)
                ModLab(LabRow.decode(values[LAB_KEY].orEmpty()), problems)
            }
        }
    }

    /** Assembly resets the receipt only after restoring the tree; an older stage stamp cannot override it. */
    private suspend fun WorkbenchRunner.Job.patched(): Boolean = hasAppliedModules(readText(APPLIED_PATH))

    /** Why a build stopped, from its failed stage's message (`<step>: CODE: text`), with the fix offered here. */
    fun diagnose(failure: String, snapshot: ConfigurationSnapshot): PreflightFinding? =
        CODE.find(failure)?.let { finding(failure.substring(it.range.first), snapshot) }

    private fun finding(message: String, snapshot: ConfigurationSnapshot): PreflightFinding {
        val code = message.substringBefore(':').trim()
        val rawText = message.substringAfter(':').trim()
        val (mismatchId, text) = if (code == "VINTF_INCOMPATIBLE") {
            VintfCheck.splitMismatchMessage(rawText)
        } else {
            null to rawText
        }
        val fix = mismatchId?.let(::acknowledgeVintfMismatch) ?: FIXES[code]?.invoke(text)
        // With the fill on, the build adds the missing lines itself after Patches: no longer a stop.
        val filled = code == COVERAGE && "hold a space" !in text && BuildChecks.UNREADABLE_COVERAGE !in text &&
            snapshot.customization.fillMissingSidecars
        val payloadOff = code == PAYLOAD && PackageKind.Payload.name !in snapshot.build.extraPackages
        val note = filled || payloadOff || code == "MODS_NOT_CHECKED" || code == "MODS_PARTLY_CHECKED" ||
            code == VintfCheck.NOT_CHECKED || code == "VINTF_ACCEPTED"
        return PreflightFinding(code, text, blocks = !note, fix = fix?.takeUnless { filled })
    }

    private companion object {
        const val MAX_READS = 64

        /** The tools the check scripts take by path. */
        val PREFLIGHT_TOOLS = listOf("checkvintf", "unpack_bootimg", "fsck.erofs")

        /** A refusal code: capitals and digits in two or more parts joined by `_`, then a colon. */
        val CODE = Regex("""\b[A-Z][A-Z0-9]*(?:_[A-Z0-9]+)+:""")
        const val COVERAGE = "COVERAGE_REFUSAL"
        const val PAYLOAD = "PAYLOAD_NEEDS_AB_OTA"

        fun accept(kind: BuildWarningKind, label: String) = PreflightFix.Setting(label) {
            it.copy(build = it.build.copy(acknowledgedWarnings = it.build.acknowledgedWarnings + kind.name))
        }

        fun open(label: String, stage: StageId, objectId: String) = PreflightFix.Open(label, stage, objectId)

        /** Each refusal code's fix, from its message text. */
        val FIXES: Map<String, (String) -> PreflightFix> = mapOf(
            COVERAGE to { text ->
                if ("hold a space" in text) {
                    open("Rename or delete them", StageId.WORK_TREE_ASSEMBLY, "image-browser")
                } else {
                    PreflightFix.Setting("Add the missing lines like their folder (next build)") {
                        it.copy(customization = it.customization.copy(fillMissingSidecars = true))
                    }
                }
            },
            "BROKEN_LINKS" to { _ -> accept(BuildWarningKind.BrokenLinks, "Accept the broken links") },
            "UNSIGNED_APK" to { _ -> accept(BuildWarningKind.UnsignedApks, "Accept the unsigned APKs") },
            "VINTF_INCOMPATIBLE" to { _ ->
                open("Review firmware baseline", StageId.FIRMWARE_ACQUISITION, "firmware-baseline")
            },
            "VINTF_PARTITION_MISSING" to { _ ->
                open("Open Assemble work trees", StageId.WORK_TREE_ASSEMBLY, "work-trees")
            },
            "VINTF_SEPOLICY" to { _ ->
                open("Review firmware baseline", StageId.FIRMWARE_ACQUISITION, "firmware-baseline")
            },
            "SUPER_OVERLAP" to { _ -> accept(BuildWarningKind.SuperOverlap, "Accept: they are in super.img too") },
            "KEPT_PARTITION_CHANGED" to { text ->
                val p = text.substringBefore(' ')
                PreflightFix.Setting("Build $p instead of keeping it") {
                    it.copy(build = it.build.copy(keepStock = it.build.keepStock - p))
                }
            },
            PAYLOAD to { _ ->
                PreflightFix.Setting("Build the A/B OTA too") {
                    it.copy(build = it.build.copy(packageType = PackageType.BOTH))
                }
            },
            "UNSUPPORTED_FILESYSTEM" to { _ ->
                PreflightFix.Setting("Build them as ${BuildFilesystems.EROFS}") {
                    val supported = it.build.partitionFilesystem.filterValues { fs -> fs in BuildFilesystems.SUPPORTED }
                    it.copy(build = it.build.copy(partitionFilesystem = supported))
                }
            },
            "KERNEL_FILESYSTEM" to { _ -> open("Pick another filesystem", StageId.BUILD_FLASHABLE_ZIP, "compression") },
            "MOUNT_TABLE" to { _ -> open("Add the mount lines", StageId.WORK_TREE_ASSEMBLY, "mount-table") },
            "PARTITIONS_LEFT_OUT" to { _ ->
                open("Select them again", StageId.FIRMWARE_EXTRACTION, "dynamic-partitions")
            },
            "MODS_DO_NOT_FIT" to { _ -> open("Open the mods", StageId.MODULE_APPLICATION, "enabled-ordered-modules") },
            "MODS_NOT_CHECKED" to { _ -> open("Run through Assemble", StageId.WORK_TREE_ASSEMBLY, "work-trees") },
        )
        const val MODS_KEY = "preflight.modules"
        const val LAB_KEY = "preflight.lab"

        /**
         * The Patches plan without the check that follows its apply step (it reads the apply step's result, which a
         * check that replaces that step never records).
         */
        fun withoutApplyResult(steps: List<PipelineStep>): List<PipelineStep> = steps.filterIndexed { i, step ->
            val after = steps.getOrNull(i - 1) as? PipelineStep.Host
            step !is PipelineStep.Check || after?.request !is ApplyModules
        }

        /** The Patches stage's plan with its apply step checking each operation instead. */
        fun labPlan(catalog: PluginCatalogPort, decode: Boolean): (StageContext) -> List<PipelineStep> = { ctx ->
            withoutApplyResult(ModuleApplicationStage(catalog).plan(ctx)).mapNotNull { step ->
                val apply = (step as? PipelineStep.Host)?.request as? ApplyModules
                when {
                    apply != null -> PipelineStep.Host("check each operation", LAB_KEY, LabModules(apply, decode))
                    step is PipelineStep.Check -> step
                    else -> null
                }
            }
        }

        /** What the Patches stage writes into work/ (`ModuleApplicationStage.APPLIED_PATH`). */
        const val APPLIED_PATH = "work/modules/applied.json"
        const val MODS_NOT_CHECKED = "MODS_NOT_CHECKED: work/ already holds the mods from the last Patches run, so " +
            "their anchors cannot be checked against it. Run the build through Assemble, then check again."

        /** The Patches stage's plan with its apply step checking instead, and a finding for each mod refused. */
        fun modCheck(catalog: PluginCatalogPort): (StageContext) -> List<PipelineStep> = { ctx ->
            withoutApplyResult(ModuleApplicationStage(catalog).plan(ctx)).flatMap { step ->
                val apply = (step as? PipelineStep.Host)?.request as? ApplyModules
                when {
                    apply != null -> listOf(
                        PipelineStep.Host("check modules", MODS_KEY, CheckModules(apply)),
                        PipelineStep.Check("modules fit") { values ->
                            values[MODS_KEY]?.lines()?.filterNot { it.startsWith(COMPONENT_EDITS_UNCHECKED) }
                                ?.joinToString("\n")?.takeIf { it != "ok" && it.isNotBlank() }
                                ?.let { "MODS_DO_NOT_FIT: these mods would be refused by this firmware: $it" }
                        },
                        PipelineStep.Check("mods partly checked") { values ->
                            values[MODS_KEY]?.lines()?.firstOrNull { it.startsWith(COMPONENT_EDITS_UNCHECKED) }?.let {
                                "MODS_PARTLY_CHECKED: ${it.removePrefix(
                                    COMPONENT_EDITS_UNCHECKED,
                                )} also edit APKs or " +
                                    "JARs; those edits are checked when the build decodes the packages."
                            }
                        },
                    )

                    step is PipelineStep.Check -> listOf(step)

                    else -> emptyList()
                }
            }
        }

        /** Build & Sign's checks, as functions of the context: each re-planned as its facts arrive. */
        val GENERATORS: List<(StageContext) -> List<PipelineStep>> = listOf(
            { ctx ->
                listOfNotNull(
                    BuildFlashableZipStage.leftOutPartitions(ctx),
                    BuildFlashableZipStage.filesystemCheck(ctx),
                )
            },
            { ctx ->
                listOfNotNull(
                    PipelineStep.Host(
                        "check coverage",
                        BuildFlashableZipStage.COVERAGE_RESULT_KEY,
                        org.ide.lti.core.domain.pipeline.toolkit.CoverageCheckRequest(ctx.target.dynamicPartitions),
                    ),
                    BuildChecks.coverageRefusal(ctx),
                )
            },
            BuildChecks::linkSteps,
            BuildChecks::mountTableSteps,
            BuildChecks::kernelFilesystemSteps,
            BuildChecks::apkSignatureSteps,
            BuildChecks::vintfSteps,
            { ctx ->
                buildList { ctx.snapshot.build.keepStock.forEach { p -> KeptPartition.steps(ctx, p, this) } }
                    .filter { it !is PipelineStep.Tool || it.resultKey != null }
            },
            { ctx -> BuildChecks.extraPackageSteps(ctx).filterIsInstance<PipelineStep.Check>() },
        )
    }
}
