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

import kotlin.test.Test
import kotlin.test.assertEquals

class DebloatGoalTest {
    private fun app(pkg: String, label: String?, mib: Long) = DebloatSuggestion(
        DebloatCandidate("product", "app/$pkg", mib * MIB, packageId = pkg),
        pkg,
        label,
        emptyList(),
    )

    private val apps = listOf(
        app("com.facebook.katana", "Facebook", 200),
        app("com.bank.mobile", "My Bank", 300),
        app("com.android.updater", "System update", 150),
        app("com.netflix", "Netflix", 120),
        app("com.games", null, 40),
    )

    @Test
    fun largestFirstUntilTheGoalWithoutTheAppsAskedToKeep() {
        val plan = DebloatGoal.plan(apps, 250 * MIB, listOf(" bank", "UPDATE ", ""))
        // My Bank and System update are kept; Facebook (200) then Netflix (120) reach 250.
        assertEquals(listOf("com.facebook.katana", "com.netflix"), plan.picks.map { it.packageId })
        assertEquals(320 * MIB, plan.freedBytes)
        assertEquals(0L, plan.shortBytes)
        assertEquals(listOf("com.bank.mobile", "com.android.updater"), plan.kept.map { it.packageId })
    }

    @Test
    fun aGoalTheAppsCannotReachSaysHowFarShort() {
        val plan = DebloatGoal.plan(apps, 2000 * MIB, emptyList())
        assertEquals(5, plan.picks.size)
        assertEquals((2000L - 810L) * MIB, plan.shortBytes)
    }

    private companion object {
        const val MIB = 1024L * 1024L
    }
}
