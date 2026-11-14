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

import org.ide.lti.core.domain.pipeline.stages.Preflight
import org.ide.lti.core.domain.plugin.LabStatus
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.workspace.ConfigurationSnapshot

/** One line of the upgrade report; [problem] lines need a decision before building. */
public data class UpgradeLine(val text: String, val problem: Boolean = false)

public data class UpgradeSection(val title: String, val lines: List<UpgradeLine>)

/**
 * The firmware upgrade assistant: before the settings of [from] (a workspace of the same phone on its old firmware)
 * are taken into [to] (the new firmware), what changes between the two firmwares and what of those settings still
 * fits the new one: the firmware identity, the partition images, each debloat pick, each mod operation (against
 * the new assembled tree) and what stays behind. Reads both workspaces; writes nothing.
 */
public class FirmwareUpgrade(
    private val runner: WorkbenchRunner,
    private val settings: WorkspaceSettingsStore,
    private val preflight: Preflight,
) {
    public suspend fun compare(from: String, to: String): List<UpgradeSection> {
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

    private fun debloat(picks: List<String>, after: Facts): UpgradeSection {
        val missing = picks.filter { after.picks[it] == false }
        val lines = if (picks.isEmpty()) {
            listOf(UpgradeLine("No apps picked for removal."))
        } else {
            listOf(UpgradeLine("${picks.size - missing.size} of ${picks.size} picked apps are where they were.")) +
                missing.map { UpgradeLine("$it: not in this firmware (moved or removed); the pick is dropped", true) }
        }
        return UpgradeSection("Debloat picks", lines)
    }

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
                            "longer do, ${rows.count { it.status == LabStatus.UNCHECKED }} APK/JAR edits are " +
                            "checked by the build.",
                    )
                },
            ) +
            refused.map { UpgradeLine("${it.mod.substringAfter(':')} ${it.operationId}: ${it.reason}", true) }
        return UpgradeSection("Mods", lines)
    }

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

    private companion object {
        val PROPS = listOf(
            "ro.system.build.fingerprint" to "Build",
            "ro.build.version.release" to "Android",
            "ro.build.version.security_patch" to "Security patch",
        )

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
