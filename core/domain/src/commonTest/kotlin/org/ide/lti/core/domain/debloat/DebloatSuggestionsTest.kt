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

/** Apps as PQ84P01 has them: YouTube stands alone, GmsCore declares AD_ID that Play asks for. */
class DebloatSuggestionsTest {
    private val youtube = DebloatCandidate("product", "app/YouTube", 115_306_441, "com.google.android.youtube")
    private val gms = DebloatCandidate("product", "priv-app/GmsCore", 200_000_000, "com.google.android.gms")
    private val vending = DebloatCandidate("product", "priv-app/Phonesky", 60_000_000, "com.android.vending")
    private val zdm = DebloatCandidate("system", "system/app/ZteZdm", 9_000_000, "com.zte.zdmdaemon")
    private val calculator = DebloatCandidate(
        "product",
        "app/CalculatorGoogle",
        6_501_171,
        "com.google.android.calculator",
    )
    private val apps = mapOf(
        "product:app/YouTube" to AppFacts(packageId = "com.google.android.youtube"),
        "product:priv-app/GmsCore" to AppFacts(
            packageId = "com.google.android.gms",
            definedPermissions = setOf("com.google.android.gms.permission.AD_ID"),
        ),
        "product:priv-app/Phonesky" to AppFacts(
            packageId = "com.android.vending",
            usedPermissions = setOf("com.google.android.gms.permission.AD_ID"),
        ),
        "system:system/app/ZteZdm" to AppFacts(packageId = "com.zte.zdmdaemon", bootReceiver = true),
        "product:app/CalculatorGoogle" to AppFacts(packageId = "com.google.android.calculator"),
    )

    @Test
    fun anAppNothingReliesOnIsSuggestedTheLargestFirst() {
        val suggested = DebloatSuggestions.of(listOf(youtube, gms, vending, zdm, calculator), apps, picked = emptySet())
        // GmsCore: Play asks for its permission. Play: protected. ZteZdm: starts at boot.
        assertEquals(
            listOf("com.google.android.youtube", "com.google.android.calculator"),
            suggested.map { it.packageId },
        )
        val afterPick = DebloatSuggestions.of(
            listOf(youtube, gms, vending, zdm, calculator),
            apps,
            picked = setOf("product:app/YouTube"),
        )
        assertEquals(listOf("com.google.android.calculator"), afterPick.map { it.packageId })
    }
}
