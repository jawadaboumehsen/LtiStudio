/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import org.ide.lti.core.common.utils.runCatchingCancellable
import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.pipeline.stages.PreflightReport
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.plugin.ModLab
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.core.model.workspace.Workspace

/** One line of the upgrade report; [problem] lines need a decision before building. */
data class UpgradeLine(val text: String, val problem: Boolean = false)

/** A titled part of the upgrade report. */
data class UpgradeSection(val title: String, val lines: List<UpgradeLine>)

enum class BaselineVerdict(val label: String) {
    PASSES("Passes"),
    NEEDS_EVIDENCE("Needs evidence"),
    FAILS("Fails"),
}

data class BaselineMod(val key: String, val verdict: BaselineVerdict, val evidence: List<String>)

data class BaselineSku(val vendor: String? = null, val odm: String? = null)

data class LocalBaselineArchive(val workspace: Workspace, val archive: FirmwareArchive)

data class FirmwareRecommendations(
    val candidates: List<FirmwareBaseline>,
    val archivesNeedingPreparation: List<LocalBaselineArchive>,
    val archiveReadErrors: List<String> = emptyList(),
)

data class FirmwareBaseline(
    val workspace: Workspace,
    val firmware: String?,
    val verdict: BaselineVerdict,
    val evidence: List<String>,
    val mods: List<BaselineMod> = emptyList(),
)

/**
 * The firmware upgrade assistant: before the settings of [from] (a workspace of the same phone on its old firmware)
 * are taken into [to] (the new firmware), what changes between the two firmwares and what of those settings still
 * fits the new one: the firmware identity, the partition images, each debloat pick, each mod operation (against
 * the new assembled tree) and what stays behind. Reads both workspaces; writes nothing.
 */
class FirmwareUpgrade(
    private val runner: WorkbenchRunner,
    private val settings: WorkspaceSettingsStore,
    private val preflight: Preflight,
    private val archives: FirmwareArchives? = null,
    private val preparationEvidence: (suspend (String) -> List<String>)? = null,
) {
    /** Rank local workspaces with the current mod selection. No settings or firmware are changed. */
    suspend fun baselines(
        workspaceId: String,
        alternatives: List<Workspace>,
        sku: BaselineSku = BaselineSku(),
    ): FirmwareRecommendations {
        val source = runner.read(workspaceId) { ws }
        val selected = checkNotNull(settings.current(source.id)) { "Save the current firmware settings first." }
        val target = settings.device(source.id)?.codename
        val workspaces = (listOf(source) + alternatives).distinctBy { it.id }
        val unprepared = mutableListOf<LocalBaselineArchive>()
        val archiveErrors = mutableListOf<String>()
        val ranked = workspaces.map { candidate ->
            val snapshot = runCatchingCancellable { settings.current(candidate.id) }.getOrNull()
            runCatchingCancellable { archives?.list(candidate.id).orEmpty() }
                .onSuccess { local ->
                    unprepared += local.filterNot { it.ref == snapshot?.acquisition?.archiveRef }
                        .map { LocalBaselineArchive(candidate, it) }
                }
                .onFailure { archiveErrors += "${candidate.name}: ${it.message ?: "Could not list local archives"}" }
            runCatchingCancellable { baseline(candidate, snapshot, selected, target, sku) }.getOrElse { error ->
                FirmwareBaseline(
                    candidate,
                    snapshot?.acquisition?.firmware?.version,
                    BaselineVerdict.NEEDS_EVIDENCE,
                    listOf(error.message ?: "Could not read firmware evidence."),
                )
            }
        }.sortedWith(compareBy<FirmwareBaseline> { it.verdict.ordinal }.thenBy { it.workspace.name })
        return FirmwareRecommendations(ranked, unprepared, archiveErrors)
    }

    private suspend fun baseline(
        candidate: Workspace,
        saved: ConfigurationSnapshot?,
        selected: ConfigurationSnapshot,
        target: String?,
        sku: BaselineSku,
    ): FirmwareBaseline {
        val snapshot = checkNotNull(saved) { "This workspace has no saved firmware settings." }
        val firmware = snapshot.acquisition.firmware.version
        identityRefusal(candidate, snapshot, selected, target)?.let { (verdict, why) ->
            return FirmwareBaseline(candidate, firmware, verdict, listOf(why))
        }
        val prepared = Facts.parse(runner.read(candidate.id) { sh(FACTS_SCRIPT, abs(".")) })
        val preparation = preparation(candidate.id, prepared)
        val carried = snapshot.copy(customization = selected.customization)
        return if (preparation.isEmpty()) {
            assessed(candidate, firmware, snapshot, carried, sku, prepared)
        } else {
            val mods = selected.customization.enabledPackages.map {
                BaselineMod(it, BaselineVerdict.NEEDS_EVIDENCE, preparation)
            }
            FirmwareBaseline(candidate, firmware, BaselineVerdict.NEEDS_EVIDENCE, preparation, mods)
        }
    }

    /** [candidate] judged with the current mods ([carried]) on its prepared tree, and VINTF for [sku]. */
    private suspend fun assessed(
        candidate: Workspace,
        firmware: String,
        snapshot: ConfigurationSnapshot,
        carried: ConfigurationSnapshot,
        sku: BaselineSku,
        prepared: Facts,
    ): FirmwareBaseline {
        val enabled = carried.customization.enabledPackages
        val lab = if (enabled.isEmpty()) {
            ModLab(emptyList(), emptyList())
        } else {
            preflight.lab(candidate.id, carried, decode = true)
        }
        val mods = assessMods(enabled, lab)
        val report = preflight.baselineCompatibility(candidate.id, snapshot)
        val compatibility = baselineVintf(report.vintf, sku)
        val fingerprint = prepared.props[FINGERPRINT]
        val evidence = evidence(report, sku, compatibility, fingerprint)
        return FirmwareBaseline(candidate, firmware, verdict(compatibility, mods, fingerprint), evidence, mods)
    }

    /** Why [candidate] cannot stand in for the current phone and firmware region; null when it can. */
    private suspend fun identityRefusal(
        candidate: Workspace,
        snapshot: ConfigurationSnapshot,
        selected: ConfigurationSnapshot,
        target: String?,
    ): Pair<BaselineVerdict, String>? {
        val identity = settings.device(candidate.id)?.codename
        return when {
            target == null || identity == null -> BaselineVerdict.NEEDS_EVIDENCE to "Missing device configuration."
            identity != target || snapshot.acquisition.region != selected.acquisition.region ->
                BaselineVerdict.FAILS to "Device or firmware region differs from the current workspace."
            else -> null
        }
    }

    /** What must run in [workspaceId] before its partitions and mod operations can be judged. */
    private suspend fun preparation(workspaceId: String, prepared: Facts): List<String> =
        if (prepared.images.isEmpty()) {
            listOf("Extract and assemble this archive before checking its partitions and mod operations.")
        } else {
            preparationEvidence?.invoke(workspaceId).orEmpty()
        }

    /** The report for taking the settings of workspace [from] into workspace [to]. */
    suspend fun compare(from: String, to: String): List<UpgradeSection> {
        val old = checkNotNull(settings.current(from)) { "The old workspace has no settings" }
        val mine = checkNotNull(settings.current(to)) { "This workspace has no configuration yet" }
        val picks = old.debloat.removeSelectors
        val before = Facts.parse(runner.read(from) { sh(FACTS_SCRIPT, abs(".")) })
        val after = Facts.parse(runner.read(to) { sh(FACTS_SCRIPT, abs("."), *picks.toTypedArray()) })
        if (after.images.isEmpty()) {
            return listOf(
                UpgradeSection(
                    "New firmware",
                    listOf(UpgradeLine("Run the build through Extract here first: nothing is extracted yet.", true)),
                ),
            )
        }
        // An old workspace never extracted has no firmware to compare: only its settings are checked.
        val firmware = if (before.images.isEmpty()) {
            listOf(UpgradeSection("Firmware", listOf(UpgradeLine("The old workspace has no extracted firmware."))))
        } else {
            listOf(firmware(before, after), partitions(before, after))
        }
        return firmware + listOf(
            debloat(picks, after),
            mods(to, SamePhoneSettings.carried(old, mine)),
            leftBehind(old),
        )
    }

    /** The build fingerprint, release and patch level before and after. */
    private fun firmware(before: Facts, after: Facts) = UpgradeSection(
        "Firmware",
        PROPS.mapNotNull { (key, label) ->
            val old = before.props[key]
            val new = after.props[key]
            when {
                old == null && new == null -> null
                old == new -> UpgradeLine("$label: $new (unchanged)")
                else -> UpgradeLine("$label: ${old ?: "?"} → ${new ?: "?"}")
            }
        },
    )

    /** Each stock image added, gone or resized. */
    private fun partitions(before: Facts, after: Facts): UpgradeSection {
        val names = (before.images.keys + after.images.keys).sorted()
        val lines = names.mapNotNull { p ->
            val old = before.images[p]
            val new = after.images[p]
            when {
                old == null -> UpgradeLine("$p: new in this firmware (${mib(new)})", problem = true)
                new == null -> UpgradeLine("$p: gone from this firmware (was ${mib(old)})", problem = true)
                old == new -> null
                else -> UpgradeLine("$p: ${mib(old)} → ${mib(new)}")
            }
        }
        return UpgradeSection("Partition images", lines.ifEmpty { listOf(UpgradeLine("Same images, same sizes.")) })
    }

    /** Each debloat pick the new extracted tree no longer has. */
    private fun debloat(picks: List<String>, after: Facts): UpgradeSection {
        val missing = picks.filter { after.picks[it] == false }
        val lines = if (picks.isEmpty()) {
            listOf(UpgradeLine("No apps picked for removal."))
        } else {
            listOf(UpgradeLine("${picks.size - missing.size} of ${picks.size} picked apps are where they were.")) +
                missing.map {
                    UpgradeLine(
                        "$it: not in this firmware (moved or removed); the pick is still selected. " +
                            "Review or remove it before carrying settings.",
                        true,
                    )
                }
        }
        return UpgradeSection("Debloat picks", lines)
    }

    /** Each mod operation checked against the new assembled tree with [carried]'s mods. */
    private suspend fun mods(to: String, carried: ConfigurationSnapshot): UpgradeSection {
        if (carried.customization.enabledPackages.isEmpty()) {
            return UpgradeSection("Mods", listOf(UpgradeLine("No mods enabled.")))
        }
        val lab = preflight.lab(to, carried)
        val rows = lab.rows
        val refused = rows.filter { it.status == LabStatus.REFUSED }
        val lines = lab.problems.map { UpgradeLine(it, problem = true) } +
            listOfNotNull(
                rows.takeIf { it.isNotEmpty() }?.let {
                    UpgradeLine(
                        "${rows.count { it.status == LabStatus.CHECKED }} operations fit, ${refused.size} no " +
                            "longer do, ${rows.count { it.status == LabStatus.UNCHECKED }} operations are " +
                            "checked by the build.",
                    )
                },
            ) +
            refused.map { UpgradeLine("${it.mod.substringAfter(':')} ${it.operationId}: ${it.reason}", true) }
        return UpgradeSection("Mods", lines)
    }

    /** The settings tied to the old firmware that are not carried over. */
    private fun leftBehind(old: ConfigurationSnapshot): UpgradeSection {
        val lines = listOfNotNull(
            old.assembly.kernelOverrides.keys.takeIf { it.isNotEmpty() }
                ?.let { UpgradeLine("Kernel overrides (${it.joinToString()}): made from the old boot images") },
            old.assembly.treeEdits.takeIf { it.isNotEmpty() }
                ?.let { UpgradeLine("${it.size} image-browser edit(s): paths of the old firmware") },
            old.build.keepStock.takeIf { it.isNotEmpty() }
                ?.let { UpgradeLine("Kept as shipped (${it.joinToString()}): the old stock images") },
        )
        return UpgradeSection("Stays with the old firmware", lines.ifEmpty { listOf(UpgradeLine("Nothing.")) })
    }

    /** What [FACTS_SCRIPT] read from one workspace. */
    private class Facts(
        val images: Map<String, Long>,
        val props: Map<String, String>,
        val picks: Map<String, Boolean>,
    ) {
        companion object {
            fun parse(text: String): Facts {
                val rows = text.lines().map { it.split('\t') }
                return Facts(
                    images = rows.filter { it.size == 3 && it[0] == "img" }.associate {
                        it[1] to
                            (it[2].toLongOrNull() ?: 0L)
                    },
                    props = rows.filter { it.size == 2 && it[0] == "prop" }
                        .associate { it[1].substringBefore('=') to it[1].substringAfter('=') },
                    picks = rows.filter { it.size == 3 && it[0] == "pick" }.associate { it[1] to (it[2] == "yes") },
                )
            }
        }
    }

    companion object {
        internal fun baselineVintf(vintf: VintfAssessment, sku: BaselineSku): BaselineVerdict =
            if (sku == BaselineSku()) anySku(vintf) else forSku(vintf, sku)

        private fun anySku(vintf: VintfAssessment): BaselineVerdict = when {
            vintf.status == VintfStatus.INCOMPATIBLE -> BaselineVerdict.FAILS
            vintf.status != VintfStatus.COMPATIBLE || vintf.skuResults.any { it.provisional } ->
                BaselineVerdict.NEEDS_EVIDENCE
            else -> BaselineVerdict.PASSES
        }

        private fun forSku(vintf: VintfAssessment, sku: BaselineSku): BaselineVerdict {
            val matches = vintf.skuResults.filter {
                (sku.vendor == null || it.vendorSku == sku.vendor) && (sku.odm == null || it.odmSku == sku.odm)
            }
            return when {
                matches.any { !it.provisional && it.status == VintfStatus.INCOMPATIBLE } -> BaselineVerdict.FAILS
                matches.isEmpty() || matches.any { it.provisional || it.status != VintfStatus.COMPATIBLE } ->
                    BaselineVerdict.NEEDS_EVIDENCE
                vintf.missingEvidence.isNotEmpty() -> BaselineVerdict.NEEDS_EVIDENCE
                else -> BaselineVerdict.PASSES
            }
        }

        internal fun assessMods(keys: List<String>, lab: ModLab): List<BaselineMod> = keys.map { key ->
            val rows = lab.rows.filter { it.mod == key }
            val refused = rows.filter { it.status == LabStatus.REFUSED }
            val unchecked = rows.filter { it.status == LabStatus.UNCHECKED }
            val verdict = when {
                refused.isNotEmpty() -> BaselineVerdict.FAILS
                lab.problems.isNotEmpty() || unchecked.isNotEmpty() || rows.isEmpty() -> BaselineVerdict.NEEDS_EVIDENCE
                else -> BaselineVerdict.PASSES
            }
            val nothingChecked = rows.isEmpty() && lab.problems.isEmpty()
            val evidence = refused.map { "${it.operationId} · ${it.reason}" } +
                unchecked.map { "${it.operationId} · ${it.reason.ifBlank { NEEDS_DECODING }}" } +
                lab.problems +
                listOfNotNull("No operations were checked for this mod.".takeIf { nothingChecked })
            BaselineMod(key, verdict, evidence)
        }

        /** Fails on any failure; passes only with a fingerprint, a passing VINTF and every mod passing. */
        internal fun verdict(compatibility: BaselineVerdict, mods: List<BaselineMod>, fingerprint: String?) = when {
            compatibility == BaselineVerdict.FAILS || mods.any { it.verdict == BaselineVerdict.FAILS } ->
                BaselineVerdict.FAILS
            fingerprint == null ||
                compatibility != BaselineVerdict.PASSES ||
                mods.any { it.verdict == BaselineVerdict.NEEDS_EVIDENCE } -> BaselineVerdict.NEEDS_EVIDENCE
            else -> BaselineVerdict.PASSES
        }

        /** The lines shown with a baseline: the SKU asked for, VINTF per SKU, and the prepared fingerprint. */
        private fun evidence(
            report: PreflightReport,
            sku: BaselineSku,
            compatibility: BaselineVerdict,
            fingerprint: String?,
        ): List<String> = buildList {
            val vintf = report.vintf
            if (sku != BaselineSku()) {
                val asked = "vendor ${sku.vendor ?: "any"}, ODM ${sku.odm ?: "any"}"
                add("Selected SKU evidence: $asked (${compatibility.label})")
                if (compatibility == BaselineVerdict.NEEDS_EVIDENCE) {
                    add("The selected SKU has no complete, non-provisional VINTF result.")
                }
            }
            add("VINTF · ${vintf.status.label}")
            add(fingerprint?.let { "Prepared system fingerprint · $it" } ?: "Missing prepared system fingerprint.")
            addAll(vintf.missingEvidence)
            vintf.skuResults.forEach {
                val provisional = if (it.provisional) " (provisional)" else ""
                val skus = "Vendor ${it.vendorSku ?: "default"} / ODM ${it.odmSku ?: "default"}"
                add("$skus · ${it.status.label}$provisional")
            }
            addAll(report.findings.filter { it.code == "VINTF_INCOMPATIBLE" }.map { it.message })
        }

        private const val FINGERPRINT = "ro.system.build.fingerprint"
        private const val NEEDS_DECODING = "Requires package decoding or a build"

        private val PROPS = listOf(
            "ro.system.build.fingerprint" to "Build",
            "ro.build.version.release" to "Android",
            "ro.build.version.security_patch" to "Security patch",
        )

        /** [bytes] in KiB under one MiB, else in MiB. */
        fun mib(bytes: Long?): String {
            val b = bytes ?: 0L
            return if (b < MIB) "${b / KIB} KiB" else "${b / MIB} MiB"
        }

        const val KIB = 1024L
        const val MIB = KIB * KIB

        /**
         * `$1` the workspace; then debloat picks (`partition:path`). Lists the stock images and their sizes, the
         * system build.prop identity, and whether each pick exists in the extracted tree. Reads only.
         */
        const val FACTS_SCRIPT = """cd "$1" || exit 1
shift
for f in firmware/extracted/*.img; do
  [ -f "${'$'}f" ] || continue
  n=${'$'}{f##*/}; printf 'img\t%s\t%s\n' "${'$'}{n%.img}" "${'$'}(stat -c %s "${'$'}f")"
done
for p in firmware/extracted/system/system/build.prop firmware/extracted/system/build.prop; do
  [ -f "${'$'}p" ] || continue
  grep -E '^ro\.(system\.build\.fingerprint|build\.version\.(release|security_patch))=' "${'$'}p" |
    while IFS= read -r l; do printf 'prop\t%s\n' "${'$'}l"; done
  break
done
for s in "${'$'}@"; do
  if [ -e "firmware/extracted/${'$'}{s%%:*}/${'$'}{s#*:}" ]; then a=yes; else a=no; fi
  printf 'pick\t%s\t%s\n' "${'$'}s" "${'$'}a"
done
exit 0"""
    }
}
