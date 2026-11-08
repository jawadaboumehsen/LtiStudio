/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.pipeline.toolkit

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RemovalListMatcherTest {

    @Test
    fun matchesKnownItemsAndIdentifiesUnknownEntries() {
        val available = listOf(
            RemovalItem(id = "com.facebook.katana", name = "Facebook", path = "system/app/Facebook/Facebook.apk"),
            RemovalItem(id = "com.microsoft.skydrive", name = "OneDrive", path = "product/app/OneDrive/OneDrive.apk"),
            RemovalItem(id = "com.google.android.youtube", name = "YouTube", path = "product/app/YouTube/YouTube.apk"),
        )

        val list = RemovalList(
            name = "Basic Bloat",
            entries = listOf(
                "com.facebook.katana",
                "product/app/OneDrive/OneDrive.apk",
                "com.samsung.android.bixby", // unknown on this ROM
                "com.netflix.mediaclient", // unknown on this ROM
            ),
        )

        val result = RemovalListMatcher.match(list, available)

        assertEquals(2, result.matched.size)
        assertEquals(listOf("com.facebook.katana", "com.microsoft.skydrive"), result.matched.map { it.id })

        assertEquals(2, result.unknown.size)
        assertEquals(listOf("com.samsung.android.bixby", "com.netflix.mediaclient"), result.unknown)

        assertTrue(result.blockedProtected.isEmpty())
        assertEquals(2, result.effectiveRemovals.size)
    }

    @Test
    fun blocksProtectedItemsFromRemoval() {
        val available = listOf(
            RemovalItem(
                id = "com.android.settings",
                name = "Settings",
                path = "system/priv-app/Settings/Settings.apk",
                isProtected = true,
            ),
            RemovalItem(
                id = "com.android.systemui",
                name = "SystemUI",
                path = "system/priv-app/SystemUI/SystemUI.apk",
                isProtected = true,
            ),
            RemovalItem(
                id = "com.google.android.apps.tachyon",
                name = "Google Duo",
                path = "product/app/Duo/Duo.apk",
                isProtected = false,
            ),
        )

        val list = RemovalList(
            name = "Extreme Bloat",
            entries = listOf(
                "com.android.settings",
                "system/priv-app/SystemUI/SystemUI.apk",
                "com.google.android.apps.tachyon",
            ),
        )

        val result = RemovalListMatcher.match(list, available)

        assertEquals(3, result.matched.size)
        assertEquals(2, result.blockedProtected.size)
        assertTrue(result.blockedProtected.any { it.id == "com.android.settings" })
        assertTrue(result.blockedProtected.any { it.id == "com.android.systemui" })

        // Only Google Duo is effectively removable
        assertEquals(1, result.effectiveRemovals.size)
        assertEquals("com.google.android.apps.tachyon", result.effectiveRemovals.first().id)
    }

    @Test
    fun externalProtectedIdsBlockItemsEvenIfNotMarkedInItem() {
        val available = listOf(
            RemovalItem(
                id = "com.google.android.gms",
                name = "Play Services",
                path = "system/priv-app/GmsCore/GmsCore.apk",
            ),
            RemovalItem(id = "com.google.android.youtube", name = "YouTube", path = "product/app/YouTube/YouTube.apk"),
        )

        val list = RemovalList(
            name = "Test List",
            entries = listOf("com.google.android.gms", "com.google.android.youtube"),
        )

        val protectedSet = setOf("com.google.android.gms")
        val result = RemovalListMatcher.match(list, available, protectedIds = protectedSet)

        assertEquals(1, result.blockedProtected.size)
        assertEquals("com.google.android.gms", result.blockedProtected.first().id)
        assertEquals(1, result.effectiveRemovals.size)
        assertEquals("com.google.android.youtube", result.effectiveRemovals.first().id)
    }

    @Test
    fun serializesAndDeserializesRoundTrip() {
        val list = RemovalList(
            name = "Social",
            entries = listOf("com.facebook.katana", "com.twitter.android"),
        )
        val json = RemovalList.toJson(list)
        val decoded = RemovalList.fromJson(json)
        assertEquals(list, decoded)
    }

    @Test
    fun aPlainTextListIsOnePackageOrPathPerLineAndAJsonListStaysJson() {
        val text = "# my bloat\ncom.facebook.katana\n\n  product:app/OneDrive  \n"
        assertEquals(
            RemovalList("mine", listOf("com.facebook.katana", "product:app/OneDrive")),
            RemovalList.parse("mine", text),
        )
        val json = RemovalList.toJson(RemovalList("Saved", listOf("com.x")))
        assertEquals(RemovalList("Saved", listOf("com.x")), RemovalList.parse("ignored", json))
    }

    @Test
    fun aUniversalAndroidDebloaterListGivesItsRecommendedPackages() {
        // uad_lists.json (UAD-ng) is a map; the older UAD list an array with "id".
        val map = """{"com.facebook.appmanager": {"list": "Oem", "removal": "Recommended"},
            "com.android.systemui": {"list": "Aosp", "removal": "Unsafe"},
            "com.google.android.youtube": {"list": "Google", "removal": "Advanced"}}"""
        assertEquals(listOf("com.facebook.appmanager"), RemovalList.parse("uad_lists", map).entries)
        val array = """[{"id": "com.a", "removal": "Recommended"}, {"id": "com.b", "removal": "Expert"}]"""
        assertEquals(listOf("com.a"), RemovalList.parse("old", array).entries)
        // A plain package map rates nothing: every package is taken.
        assertEquals(listOf("com.x", "com.y"), RemovalList.parse("m", """{"com.x": "bloat", "com.y": 1}""").entries)
    }

    @Test
    fun aCommunityListKeepsEveryRatingAndNoteAsItsOpinion() {
        val map = """{"com.facebook.appmanager": {"list": "Oem", "description": "Facebook updater.\n", "removal": "Recommended"},
            "com.android.systemui": {"list": "Aosp", "removal": "Unsafe"}}"""
        val list = RemovalList.parse("uad", map)
        assertEquals(listOf("com.facebook.appmanager"), list.entries)
        assertEquals(CommunityOpinion("Recommended", "Facebook updater."), list.opinions["com.facebook.appmanager"])
        assertEquals(CommunityOpinion("Unsafe"), list.opinions["com.android.systemui"])
        // A plain list carries no opinion: importing it still picks its apps.
        assertEquals(emptyMap(), RemovalList.parse("mine", "com.a\ncom.b").opinions)
    }

    @Test
    fun aPresetMatchesAnAppByPackageAfterItsFolderMoved() {
        // Saved on one firmware as the package name; on the next the app lives in another folder.
        val moved = RemovalItem("product:app/GameSpace2", "GameSpace2", "product/app/GameSpace2", "cn.nubia.gamespace")
        val result = RemovalListMatcher.match(RemovalList("p", listOf("cn.nubia.gamespace")), listOf(moved))
        assertEquals(listOf("product:app/GameSpace2"), result.effectiveRemovals.map { it.id })
        assertTrue(result.unknown.isEmpty())
    }
}
