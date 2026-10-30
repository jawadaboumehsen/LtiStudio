/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.debloat

/**
 * What removing one app would do: [blockers] stop the device from working and refuse the pick, [warnings] are system
 * apps to remove only on purpose, [notes] are plain facts.
 */
public data class AppRisk(
    val blockers: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** The risk of removing each app, from what the manifests of all the apps say (not from a list of names). */
public object DebloatRisk {
    /** The framework's own HOME activity only says "no home screen" ([AppFacts.home] there is not a launcher). */
    private const val FRAMEWORK_PACKAGE = "android"
    private const val NAMES_SHOWN = 3
    private val SYSTEM_UIDS = setOf(
        "android.uid.system",
        "android.uid.phone",
        "android.uid.bluetooth",
        "android.uid.nfc",
        "android.uid.networkstack",
        "android.uid.shell",
        "android.uid.se",
    )

    /** `partition:path` of [candidate], as picks and [assess] name it. */
    public fun selectorOf(candidate: DebloatCandidate): String = "${candidate.partition}:${candidate.relativePath}"

    /** [apks] (by APK path inside `work/`) merged per app of [inventory]: an app folder can hold split APKs. */
    public fun byApp(inventory: List<DebloatCandidate>, apks: Map<String, AppFacts>): Map<String, AppFacts> {
        val byPath = inventory.associateBy { "${it.partition}/${it.relativePath}" }
        return apks.entries
            .mapNotNull { (apk, facts) ->
                val app = byPath[apk] ?: byPath[apk.substringBeforeLast('/')] ?: return@mapNotNull null
                selectorOf(app) to facts
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, all) -> all.reduce(AppFacts::plus) }
    }

    /** The risk of removing each app of [inventory] while the apps in [picked] go too. */
    public fun assess(
        inventory: List<DebloatCandidate>,
        facts: Map<String, AppFacts>,
        picked: Set<String>,
    ): Map<String, AppRisk> = inventory.map(::selectorOf).associateWith { selector ->
        val app = facts[selector] ?: return@associateWith AppRisk()
        val kept = facts.filterKeys { it != selector && it !in picked }.values
        AppRisk(blockers(app, kept), warnings(app), notes(app, kept))
    }

    private fun blockers(app: AppFacts, kept: Collection<AppFacts>): List<String> = buildList {
        app.providedLibraries.forEach { lib ->
            val needers = kept.filter { lib in it.requiredLibraries }
            if (needers.isNotEmpty()) add("It provides $lib, which ${names(needers)} cannot start without.")
        }
        if (app.home && app.packageId != FRAMEWORK_PACKAGE &&
            kept.none { it.home && it.packageId != FRAMEWORK_PACKAGE }
        ) {
            add("It is the last home screen: after boot the device would have nothing to show.")
        }
        if (app.inputMethod && kept.none { it.inputMethod }) {
            add("It is the last keyboard: nothing could be typed, not even the screen-lock PIN.")
        }
    }

    private fun warnings(app: AppFacts): List<String> = buildList {
        if (app.persistent) add("Android keeps it running all the time (persistent).")
        app.sharedUserId?.takeIf { it in SYSTEM_UIDS }?.let { add("It runs as $it, a part of the system.") }
    }

    private fun notes(app: AppFacts, kept: Collection<AppFacts>): List<String> = buildList {
        val overlays = kept.count { app.packageId != null && app.packageId in it.overlayTargets }
        if (overlays > 0) add("$overlays overlay(s) change it; they would do nothing without it.")
        if (app.bootReceiver) add("It starts by itself after every boot.")
        app.overlayTargets.forEach { add("An overlay for $it.") }
    }

    private fun names(apps: List<AppFacts>): String {
        val names = apps.map { it.packageId ?: "an app" }
        val shown = names.take(NAMES_SHOWN).joinToString()
        return if (names.size > NAMES_SHOWN) "$shown and ${names.size - NAMES_SHOWN} more" else shown
    }
}
