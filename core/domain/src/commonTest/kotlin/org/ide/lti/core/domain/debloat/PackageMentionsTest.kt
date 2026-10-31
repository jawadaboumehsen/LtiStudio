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

class PackageMentionsTest {
    // PackageMentions.SCRIPT's lines for PQ84P01 (fw-baseline), trimmed.
    private val output = """
        system/system/etc/permissions/privapp_whitelist_com.zte.zgesture.xml	com.zte.zgesture
        product/etc/sysconfig/google.xml	com.google.android.apps.maps
        product/etc/sysconfig/google.xml	com.google.android.gms
        product/etc/sysconfig/google-initial-package-stopped-states.xml	com.google.android.apps.maps
        system/system/etc/permissions/platform.xml	com.android.shell
        system/system/etc/permissions/platform.xml	!
    """.trimIndent()

    @Test
    fun aFileNamingOnlyRemovedAppsGoesWithThemAndOneThatDeclaresThingsNever() {
        val mentions = PackageMentions.parse(output)
        assertEquals(
            listOf(
                "product/etc/sysconfig/google-initial-package-stopped-states.xml",
                "product/etc/sysconfig/google.xml",
            ),
            PackageMentions.namedIn(mentions, "com.google.android.apps.maps"),
        )
        // google.xml also names GMS, which stays: kept.
        assertEquals(
            listOf("product/etc/sysconfig/google-initial-package-stopped-states.xml"),
            PackageMentions.orphaned(mentions, setOf("com.google.android.apps.maps")),
        )
        assertEquals(
            listOf("system/system/etc/permissions/privapp_whitelist_com.zte.zgesture.xml"),
            PackageMentions.orphaned(mentions, setOf("com.zte.zgesture", "com.android.shell")),
            "platform.xml declares permissions: never removed",
        )
    }
}
