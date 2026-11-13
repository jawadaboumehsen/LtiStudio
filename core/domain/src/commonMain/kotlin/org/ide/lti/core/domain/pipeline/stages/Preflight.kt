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
import org.ide.lti.core.domain.plugin.CheckModules
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.workbench.LogLevel
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.PackageType

/** What fixes a finding: a settings change applied at once, or the screen where it is fixed. */
public sealed interface PreflightFix {
    public val label: String

    public class Setting(
        override val label: String,
        public val apply: (ConfigurationSnapshot) -> ConfigurationSnapshot,
    ) : PreflightFix

    public data class Open(override val label: String, val stage: StageId, val objectId: String) : PreflightFix
}

/** One thing that would stop the next build ([blocks]) or that it would only warn about, with its fix. */
public data class PreflightFinding(
    val code: String,
    val message: String,
    val blocks: Boolean = true,
    val fix: PreflightFix? = null,
)

/**
 * Will it build? Runs Build & Sign's own checks against the current settings and work/ without building: the
 * partitions left out, the filesystems, the permission and label coverage, broken links, the mount tables, what the
 * boot kernel mounts and decodes, unsigned APKs, kept partitions changed since extraction and the extra packages;
 * plus the device configuration's errors. Only the steps that read are run; nothing in the workspace is written.
 */
public class Preflight(
    private val runner: WorkbenchRunner,
    private val settings: WorkspaceSettingsStore,
    private val host: HostStepPort? = null,
    /** The installed plugins, for the mod check: each enabled mod's file anchors against the assembled tree. */
    private val catalog: PluginCatalogPort? = null,
) {
    public suspend fun check(workspaceId: String): List<PreflightFinding> {
        val snapshot = checkNotNull(settings.current(workspaceId)) { "The workspace has no configuration yet" }
        val device = checkNotNull(settings.device(workspaceId)) { "The workspace has no device configuration" }
        val validation = DeviceConfigReadiness.check(device, snapshot).filter { it.severity == Severity.ERROR }.map {
            PreflightFinding(
                it.code,
                it.message,
                fix = it.stageId?.let { stage -> PreflightFix.Open("Open ${it.fieldPath}", stage, it.objectId ?: "") },
            )
        }
        val found = runner.read(workspaceId) {
            val base = WorkTreeAssemblyStage.afterFolding(StageContext(ws, snapshot, device))
            val values = mutableMapOf<String, String>()
            // After Patches, work/ holds the mods already: their anchors are checked against a tree only Assemble made.
            val patched = readText(APPLIED_PATH)?.contains("\"operations\":0") == false
            val mods = when {
                catalog == null || snapshot.customization.enabledPackages.isEmpty() -> emptyList()
                patched -> listOf(MODS_NOT_CHECKED)
                else -> run(modCheck(catalog), base, values)
            }
            val messages = GENERATORS.flatMap { generator -> run(generator, base, values) } + mods
            report(LogLevel.INF, "Preflight: ${messages.size} finding(s) from Build & Sign's checks")
            messages
        }
        return validation + found.distinct().map { finding(it, snapshot) }
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

    private fun finding(message: String, snapshot: ConfigurationSnapshot): PreflightFinding {
        val code = message.substringBefore(':').trim()
        val text = message.substringAfter(':').trim()
        // With the fill on, the build adds the missing lines itself after Patches: no longer a stop.
        val filled = code == COVERAGE && "hold a space" !in text && snapshot.customization.fillMissingSidecars
        val payloadOff = code == PAYLOAD && PackageKind.Payload.name !in snapshot.build.extraPackages
        val note = filled || payloadOff || code == "MODS_NOT_CHECKED"
        return PreflightFinding(code, text, blocks = !note, fix = FIXES[code]?.invoke(text)?.takeUnless { filled })
    }

    private companion object {
        const val MAX_READS = 64
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

        /** What the Patches stage writes into work/ (`ModuleApplicationStage.APPLIED_PATH`). */
        const val APPLIED_PATH = "work/modules/applied.json"
        const val MODS_NOT_CHECKED = "MODS_NOT_CHECKED: work/ already holds the mods from the last Patches run, so " +
            "their anchors cannot be checked against it. Run the build through Assemble, then check again."

        /** The Patches stage's plan with its apply step checking instead, and a finding for each mod refused. */
        fun modCheck(catalog: PluginCatalogPort): (StageContext) -> List<PipelineStep> = { ctx ->
            ModuleApplicationStage(catalog).plan(ctx).flatMap { step ->
                val apply = (step as? PipelineStep.Host)?.request as? ApplyModules
                when {
                    apply != null -> listOf(
                        PipelineStep.Host("check modules", MODS_KEY, CheckModules(apply)),
                        PipelineStep.Check("modules fit") { values ->
                            values[MODS_KEY]?.takeIf { it != "ok" && it.isNotBlank() }
                                ?.let { "MODS_DO_NOT_FIT: these mods would be refused by this firmware: $it" }
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
            { ctx ->
                buildList { ctx.snapshot.build.keepStock.forEach { p -> KeptPartition.steps(ctx, p, this) } }
                    .filter { it !is PipelineStep.Tool || it.resultKey != null }
            },
            { ctx -> BuildChecks.extraPackageSteps(ctx).filterIsInstance<PipelineStep.Check>() },
        )
    }
}
