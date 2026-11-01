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
        sdk =35
        uses android.ext.shared
        ver 11.150.0101
        @ product/app/Gmail2/Gmail2.apk
        asks com.google.android.apps.maps
        asks com.google.android.apps.docs
        pkg com.google.android.gm
        sdk 0x23
        @ product/overlay/TeleService__auto_generated_rro.apk
        overlay com.android.phone
        pkg com.android.phone.auto_generated_rro
        @ system/system/framework/framework-res.apk
        home
        pkg android
    """.trimIndent()

    /** Lines as `AppManifests.SIGNERS_SCRIPT` printed them for the same PQ84P01 APKs. */
    private val signers = """
        @ system/system/framework/framework-res.apk
        signer db4e3ab7c2f3c77e 1.2.840.113549.1.9.1=#1612737570706f7274407a74652e636f6d2e636e,CN=ZTE,OU=Smartphone Software Dept.,O=ZTE,L=Shanghai,ST=Shanghai,C=CN
        @ system/system/priv-app/TeleService/TeleService.apk
        signer db4e3ab7c2f3c77e 1.2.840.113549.1.9.1=#1612737570706f7274407a74652e636f6d2e636e,CN=ZTE,OU=Smartphone Software Dept.,O=ZTE,L=Shanghai,ST=Shanghai,C=CN
        @ product/app/Maps/Maps.apk
        signer f0fd6c5b410f25cb CN=Android,OU=Android,O=Google Inc.,L=Mountain View,ST=California,C=US
    """.trimIndent()

    private val listing = AppManifests.parse(output).keys.joinToString("\n") { "1000\t$it" }
    private val inventory = DebloatInventory.parse(listing)
    private val signed = AppManifests.parse(signers)
    private val facts = DebloatRisk.byApp(
        inventory,
        AppManifests.parse(output).mapValues { (apk, f) -> signed[apk]?.let(f::plus) ?: f },
    )

    private fun risk(selector: String, picked: Set<String> = emptySet()) =
        DebloatRisk.assess(inventory, facts, picked).getValue(selector)

    @Test
    fun theImpactMapNamesTheAppsThatWouldNoticeAnAppGone() {
        val maps = facts.getValue("product:app/Maps")
        assertEquals(35, maps.targetSdk, "aapt2 2.19 prints `=35`")
        assertEquals(35, facts.getValue("product:app/Gmail2").targetSdk, "older aapt2 prints a typed 0x23")
        assertEquals("11.150.0101", maps.version)
        assertEquals(listOf("com.google.android.gm"), DebloatRisk.impact("product:app/Maps", facts).askedBy)
        val shared = DebloatRisk.impact("system:system/app/GoogleExtShared", facts)
        assertEquals(listOf("com.google.android.apps.maps"), shared.neededBy)
        assertEquals(
            listOf("com.android.phone.auto_generated_rro"),
            DebloatRisk.impact("system:system/priv-app/TeleService", facts).overlaidBy,
        )
        assertTrue(DebloatRisk.impact("product:app/Gmail2", facts).isEmpty)
    }

    @Test
    fun theMapAlsoShowsWhatAnAppReliesOnAndWhoSignedIt() {
        val maps = DebloatRisk.impact("product:app/Maps", facts)
        assertEquals(listOf("android.ext.shared (com.google.android.ext.shared)"), maps.uses)
        val phone = DebloatRisk.impact("system:system/priv-app/TeleService", facts)
        // A library no app of the ROM provides (it comes from /system/framework) is named on its own.
        assertEquals(listOf("com.qti.extphone.extphonelib"), phone.uses)
        assertEquals(
            listOf("com.android.phone"),
            DebloatRisk.impact("product:overlay/TeleService__auto_generated_rro.apk", facts).overlays,
        )
        val gmail = DebloatRisk.impact("product:app/Gmail2", facts)
        assertEquals(listOf("com.google.android.apps.maps"), gmail.looksFor)
        assertEquals(listOf("com.google.android.apps.docs"), gmail.looksForMissing)
        // TeleService has the framework's key; Maps is signed by Google; the framework itself is not "platform signed".
        assertTrue(phone.platformSigned)
        assertEquals(false, maps.platformSigned)
        assertEquals(false, DebloatRisk.impact("system:system/framework/framework-res.apk", facts).platformSigned)
        assertEquals("ZTE", facts.getValue("system:system/priv-app/TeleService").signerName)
        assertEquals("f0fd6c5b410f25cb", facts.getValue("product:app/Maps").signerDigest)
        assertEquals("Android", facts.getValue("product:app/Maps").signerName)
    }

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
    fun aSecondProviderOfTheLibraryKeepsItsUsersWorking() {
        val second = AppFacts(packageId = "com.example.shared", providedLibraries = setOf("android.ext.shared"))
        val withSecond = facts + ("product:app/SharedCopy" to second)
        val all = inventory + DebloatCandidate("product", "app/SharedCopy", 1000, null)
        val shared = DebloatRisk.assess(all, withSecond, emptySet()).getValue("system:system/app/GoogleExtShared")
        assertTrue(shared.blockers.isEmpty())
    }

    @Test
    fun anUnreadableManifestIsAnUnknownNotASafeApp() {
        val parsed = AppManifests.parse("@ product/app/Broken/Broken.apk\nunreadable")
        assertTrue(parsed.getValue("product/app/Broken/Broken.apk").unreadable)
        val broken = AppFacts(unreadable = true)
        val withBroken = facts + ("product:app/Broken" to broken)
        val all = inventory + DebloatCandidate("product", "app/Broken", 1000, null)
        assertTrue(DebloatRisk.assess(all, withBroken, emptySet()).getValue("product:app/Broken").blockers.isNotEmpty())
        // A kept app that could not be read may need a library a pick provides.
        val shared = DebloatRisk.assess(all, withBroken, setOf("product:app/Maps"))
            .getValue("system:system/app/GoogleExtShared")
        assertTrue(shared.blockers.single().contains("could not be read"))
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
