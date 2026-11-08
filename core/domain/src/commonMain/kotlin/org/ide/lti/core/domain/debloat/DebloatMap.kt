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

/** What another app loses when the one it relies on is removed, worst first. */
public enum class Loss(public val words: String) {
    /** It needs a library the removed app provides (`<uses-library>`, required). */
    STOPS("stops starting"),

    /** It uses that library only when it is there (`required="false"`). */
    FEATURE("loses a feature"),

    /** It asks for a permission the removed app declares (`<permission>`): the system no longer grants it. */
    PERMISSION("loses a permission"),

    /** The removed app is a resource overlay for it: it keeps working, without that skin. */
    SKIN("loses a skin"),
}

/** One arrow of the map: [from] relies on [to] through [reasons], and loses [loss] without it. */
public data class Reliance(val from: String, val to: String, val loss: Loss, val reasons: List<String>)

/**
 * The map of one app: the apps that rely on it (they lose something when it goes) and those it relies on, from what
 * the ROM's manifests declare. Apps also reach each other while the device runs (intents, bound services, content
 * providers); no file in the firmware records that, so it is not on the map.
 */
public data class AppMap(val packageId: String, val reliedOnBy: List<Reliance>, val reliesOn: List<Reliance>) {
    /** The worst loss removing the app causes, or null when nothing declares a reliance on it. */
    public val worst: Loss? get() = reliedOnBy.minOfOrNull { it.loss }
}

public object DebloatMap {
    /** The framework: every app asks for its permissions, and it is never removed, so it is no arrow. */
    private const val FRAMEWORK = "android"

    /** The map of the app [selector] names among [facts] (one entry per app, as `DebloatRisk.byApp` gives). */
    public fun of(selector: String, facts: Map<String, AppFacts>): AppMap? {
        val pkg = facts[selector]?.packageId ?: return null
        val app = facts.getValue(selector)
        val all = facts.values.filter { it.packageId != null }
        val others = all.filter { it.packageId != pkg }
        return AppMap(
            packageId = pkg,
            reliedOnBy = others.mapNotNull { other -> reliance(other, app, all) }.sortedWith(ORDER),
            reliesOn = others.filter { it.packageId != FRAMEWORK }.mapNotNull { other -> reliance(app, other, all) }
                .sortedWith(ORDER),
        )
    }

    /**
     * How [user] relies on [provider], its worst loss first among the reasons; null when it does not. A library or
     * permission another app of [all] also provides is no reliance: that app still serves it (the framework redeclares
     * many permissions apps also declare).
     */
    private fun reliance(user: AppFacts, provider: AppFacts, all: List<AppFacts>): Reliance? {
        fun onlyFrom(name: String, of: (AppFacts) -> Set<String>) =
            all.none { it.packageId != provider.packageId && name in of(it) }
        val libraries = provider.providedLibraries.filter { onlyFrom(it, AppFacts::providedLibraries) }.toSet()
        val permissions = provider.definedPermissions.filter { onlyFrom(it, AppFacts::definedPermissions) }.toSet()
        val reasons = buildList {
            (user.requiredLibraries intersect libraries).sorted()
                .forEach { add(Loss.STOPS to "needs the library $it") }
            (user.optionalLibraries intersect libraries).sorted()
                .forEach { add(Loss.FEATURE to "uses the library $it when it is there") }
            (user.usedPermissions intersect permissions).sorted()
                .forEach { add(Loss.PERMISSION to "asks for $it") }
            if (user.packageId in provider.overlayTargets) {
                add(Loss.SKIN to "${provider.packageId} is an overlay for it")
            }
        }
        if (reasons.isEmpty()) return null
        return Reliance(
            from = user.packageId.orEmpty(),
            to = provider.packageId.orEmpty(),
            loss = reasons.minOf { it.first },
            reasons = reasons.map { it.second },
        )
    }

    private val ORDER = compareBy<Reliance>({ it.loss }, { it.from }, { it.to })
}
