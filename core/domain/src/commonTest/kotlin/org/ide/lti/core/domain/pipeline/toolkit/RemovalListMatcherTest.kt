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
}
