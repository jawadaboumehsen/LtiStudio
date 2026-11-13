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

/**
 * One app in both directions, by package name. Who would notice it gone: those that cannot start
 * without a library it provides, the overlays that change it, those that look for it by name, those sharing its user
 * ID. What it relies on: the libraries it needs ([uses], with the app providing each when one does), the apps it
 * overlays, the apps of the ROM it looks for, and those it looks for that the ROM does not have ([looksForMissing]).
 */
public data class AppImpact(
    val neededBy: List<String> = emptyList(),
    val overlaidBy: List<String> = emptyList(),
    val askedBy: List<String> = emptyList(),
    val sharesUidWith: List<String> = emptyList(),
    val uses: List<String> = emptyList(),
    val overlays: List<String> = emptyList(),
    val looksFor: List<String> = emptyList(),
    val looksForMissing: List<String> = emptyList(),
    /** Signed with the framework's key (the platform key): it holds the system's signature permissions. */
    val platformSigned: Boolean = false,
) {
    public val isEmpty: Boolean get() = neededBy.isEmpty() && overlaidBy.isEmpty() && askedBy.isEmpty() &&
        sharesUidWith.isEmpty()
}

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

    /** What every other app of [facts] says about the app [selector] names ([AppImpact]). */
    public fun impact(selector: String, facts: Map<String, AppFacts>): AppImpact {
        val app = facts[selector] ?: return AppImpact()
        val others = facts.filterKeys { it != selector }.values
        val pkg = app.packageId
        fun List<AppFacts>.names() = mapNotNull { it.packageId }.distinct().sorted()
        val packages = others.mapNotNull { it.packageId }.toSet()
        val platform = facts.values.firstOrNull { it.packageId == FRAMEWORK_PACKAGE }?.signerDigest
        return AppImpact(
            neededBy = others.filter { o -> app.providedLibraries.any { it in o.requiredLibraries } }.names(),
            overlaidBy = others.filter { pkg != null && pkg in it.overlayTargets }.names(),
            askedBy = others.filter { pkg != null && pkg in it.queries }.names(),
            sharesUidWith = others.filter { app.sharedUserId != null && it.sharedUserId == app.sharedUserId }.names(),
            uses = app.requiredLibraries.sorted().map { lib ->
                others.firstOrNull { lib in it.providedLibraries }?.packageId?.let { "$lib ($it)" } ?: lib
            },
            overlays = app.overlayTargets.sorted(),
            looksFor = app.queries.filter { it in packages }.sorted(),
            looksForMissing = app.queries.filter { it !in packages && it != pkg }.sorted(),
            platformSigned = platform != null && app.signerDigest == platform && pkg != FRAMEWORK_PACKAGE,
        )
    }

    private fun blockers(app: AppFacts, kept: Collection<AppFacts>): List<String> = buildList {
        // Without its manifest nothing here can be judged: no app is removed on an unknown.
        if (app.unreadable) add("Its manifest could not be read, so what relies on it is unknown.")
        app.providedLibraries.forEach { lib ->
            val needers = kept.filter { lib in it.requiredLibraries }
            // Another kept app providing the same library still serves them.
            if (needers.isNotEmpty() && kept.none { lib in it.providedLibraries }) {
                add("It provides $lib, which ${names(needers)} cannot start without.")
            }
            kept.filter { it.unreadable }.takeIf { it.isNotEmpty() && kept.none { lib in it.providedLibraries } }
                ?.let { add("It provides $lib, and ${names(it)} could not be read: they may need it.") }
        }
        if (app.home && app.packageId != FRAMEWORK_PACKAGE &&
            kept.none { it.home && it.packageId != FRAMEWORK_PACKAGE }
        ) {
            add("It is the last home screen: after boot the device would have nothing to show.")
        }
        if (app.inputMethod && kept.none { it.inputMethod }) {
            add("It is the last keyboard: nothing could be typed, not even the screen-lock PIN.")
        }
        if (app.camera && kept.none { it.camera }) {
            add("It is the last camera: no app could take a photo, and others that ask for one would fail.")
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
