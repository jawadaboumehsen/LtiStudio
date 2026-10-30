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
import kotlin.test.assertTrue

class DebloatRiskTest {

    /** Lines as `AppManifests.SCRIPT` printed them for the PQ84P01 firmware (trimmed to the apps used here). */
    private val output = """
        @ system/system/priv-app/TeleService/TeleService.apk
        boot
        optional androidx.window.extensions
        persistent
        pkg com.android.phone
        uid android.uid.phone
        uses androidx.window.extensions
        uses com.qti.extphone.extphonelib
        @ system_ext/priv-app/Launcher_MFV_tablet_abroad/Launcher_MFV_tablet_abroad.apk
        boot
        home
        pkg com.zte.mifavor.launcher
        @ product/app/LatinImeGoogle/LatinImeGoogle.apk
        ime
        pkg com.google.android.inputmethod.latin
        @ system/system/app/GoogleExtShared/GoogleExtShared.apk
        lib android.ext.shared
        pkg com.google.android.ext.shared
        @ product/app/Maps/Maps.apk
        pkg com.google.android.apps.maps
        uses android.ext.shared
        @ product/overlay/TeleService__auto_generated_rro.apk
        overlay com.android.phone
        pkg com.android.phone.auto_generated_rro
        @ system/system/framework/framework-res.apk
        home
        pkg android
    """.trimIndent()

    private val listing = AppManifests.parse(output).keys.joinToString("\n") { "1000\t$it" }
    private val inventory = DebloatInventory.parse(listing)
    private val facts = DebloatRisk.byApp(inventory, AppManifests.parse(output))

    private fun risk(selector: String, picked: Set<String> = emptySet()) =
        DebloatRisk.assess(inventory, facts, picked).getValue(selector)

    @Test
    fun parsesTheFactsOfEachApk() {
        val phone = AppManifests.parse(output).getValue("system/system/priv-app/TeleService/TeleService.apk")
        assertEquals("com.android.phone", phone.packageId)
        assertEquals("android.uid.phone", phone.sharedUserId)
        assertTrue(phone.persistent && phone.bootReceiver)
        // required="false" libraries are not needed to start, whichever order the lines come in.
        assertEquals(setOf("com.qti.extphone.extphonelib"), phone.requiredLibraries)
        // LABELS_SCRIPT's lines for the same APK merge in: a name may contain spaces.
        val named = AppManifests.parse(
            "@ system/system/priv-app/GameSpace/GameSpace.apk\nlabel Game Space\nicon 3d6d008c00a1b2c3.img",
        ).getValue("system/system/priv-app/GameSpace/GameSpace.apk")
        assertEquals("Game Space", named.label)
        assertEquals("3d6d008c00a1b2c3.img", named.icon)
    }

    @Test
    fun aLooseApkInAContainerFolderIsItsOwnEntryAndTheFrameworkIsNeverOffered() {
        val selectors = inventory.map { "${it.partition}:${it.relativePath}" }
        assertTrue("product:overlay/TeleService__auto_generated_rro.apk" in selectors)
        assertTrue("product:overlay" !in selectors)
        assertTrue(inventory.filter { "framework" in it.relativePath }.all(DebloatInventory::isProtected))
    }

    @Test
    fun anAppProvidingALibraryAKeptAppNeedsCannotBeRemoved() {
        val shared = "system:system/app/GoogleExtShared"
        assertTrue(risk(shared).blockers.single().contains("com.google.android.apps.maps"))
        // Once Maps goes too, nothing needs the library any more.
        assertTrue(risk(shared, picked = setOf("product:app/Maps")).blockers.isEmpty())
    }

    @Test
    fun theLastHomeScreenAndTheLastKeyboardCannotBeRemoved() {
        // framework-res's fallback home is not a home screen anyone can use.
        assertTrue(risk("system_ext:priv-app/Launcher_MFV_tablet_abroad").blockers.isNotEmpty())
        assertTrue(risk("product:app/LatinImeGoogle").blockers.isNotEmpty())
    }

    @Test
    fun systemAppsAreWarnedAboutAndOverlaysNamed() {
        val phone = risk("system:system/priv-app/TeleService")
        assertTrue(phone.blockers.isEmpty())
        assertTrue(phone.warnings.any { "persistent" in it } && phone.warnings.any { "android.uid.phone" in it })
        assertTrue(phone.notes.any { "1 overlay" in it } && phone.notes.any { "boot" in it })
        assertTrue(risk("system:system/app/GoogleExtShared").notes.isEmpty())
    }
}
