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
import kotlin.test.assertNull

/** Apps and declarations as PQ84P01's manifests have them (AppManifests.SCRIPT over its work tree). */
class DebloatMapTest {
    private val framework = AppFacts(
        packageId = "android",
        definedPermissions = setOf("android.permission.QUERY_ALL_PACKAGES", "android.permission.INTERNET"),
    )
    private val gms = AppFacts(
        packageId = "com.google.android.gms",
        definedPermissions = setOf(
            "com.google.android.gms.permission.AD_ID",
            "com.google.android.providers.gsf.permission.READ_GSERVICES",
        ),
    )
    private val gsf = AppFacts(
        packageId = "com.google.android.gsf",
        definedPermissions = setOf("com.google.android.providers.gsf.permission.READ_GSERVICES"),
    )
    private val zoem = AppFacts(
        packageId = "com.zoem.rt",
        definedPermissions = setOf("android.permission.QUERY_ALL_PACKAGES"),
    )
    private val vending = AppFacts(
        packageId = "com.android.vending",
        usedPermissions = setOf(
            "com.google.android.gms.permission.AD_ID",
            "com.google.android.providers.gsf.permission.READ_GSERVICES",
            "android.permission.QUERY_ALL_PACKAGES",
            "android.permission.INTERNET",
        ),
    )
    private val callSettings = AppFacts(packageId = "com.mifavor.callsetting")
    private val shape = AppFacts(
        packageId = "com.mifavor.callsettingoverLayZTEShape",
        overlayTargets = setOf("com.mifavor.callsetting"),
    )
    private val facts = mapOf(
        "system:framework/framework-res.apk" to framework,
        "product:priv-app/GmsCore" to gms,
        "system_ext:priv-app/GoogleServicesFramework" to gsf,
        "system:app/zoem" to zoem,
        "product:priv-app/Phonesky" to vending,
        "system:app/CallSettings" to callSettings,
        "product:overlay/CallSettingsShape" to shape,
    )

    @Test
    fun aPermissionOnlyThisAppDeclaresIsLostAndOneAnotherAppDeclaresIsNot() {
        val map = DebloatMap.of("product:priv-app/GmsCore", facts)!!
        val vendingLoses = map.reliedOnBy.single()
        assertEquals("com.android.vending", vendingLoses.from)
        assertEquals(Loss.PERMISSION, vendingLoses.loss)
        // READ_GSERVICES is also gsf's: Play keeps it when GmsCore goes.
        assertEquals(listOf("asks for com.google.android.gms.permission.AD_ID"), vendingLoses.reasons)
        // The framework redeclares QUERY_ALL_PACKAGES: removing com.zoem.rt costs nothing.
        assertNull(DebloatMap.of("system:app/zoem", facts)!!.worst)
    }

    @Test
    fun anOverlayCostsItsTargetOnlyItsSkinAndTheFrameworkIsNoArrow() {
        val overlay = DebloatMap.of("product:overlay/CallSettingsShape", facts)!!
        assertEquals(Loss.SKIN, overlay.worst)
        assertEquals("com.mifavor.callsetting", overlay.reliedOnBy.single().from)
        // Play asks for framework permissions too, but the framework is never an arrow.
        assertEquals(
            listOf("com.google.android.gms"),
            DebloatMap.of("product:priv-app/Phonesky", facts)!!.reliesOn.map { it.to },
        )
    }
}
