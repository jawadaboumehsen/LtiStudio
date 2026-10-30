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

import org.ide.lti.core.domain.pipeline.toolkit.LinkCheck.Ending
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkCheckTest {
    // Lines of LISTING_SCRIPT's output for PQ84P01's work tree (fw-baseline), plus a loop.
    private val listing = LinkCheck.parse(
        listOf(
            "P\tsystem", "P\tsystem_ext", "P\tvendor", "P\todm",
            "L\tsystem\tsystem/lib64/libc.so\t/apex/com.android.runtime/lib64/bionic/libc.so",
            "L\tsystem\tbin\t/system/bin",
            "L\tsystem\todm/app\t/vendor/odm/app",
            "L\tsystem_ext\tpriv-app/WfdService/lib/arm64/libwfdnative.so\t/system_ext/lib64/libwfdnative.so",
            "L\tsystem_ext\tbin/mkfs.exfat\tmount.exfat",
            "L\tvendor\tlib64/libEGL_adreno.so\tegl/libEGL_adreno.so",
            "L\tvendor\tx/a\tb",
            "L\tvendor\tx/b\ta",
            "E\tapex", "E\tdata", "E\todm",
        ).joinToString("\n"),
    )

    @Test
    fun linksEndWhereThePhoneWouldFindThem() {
        val endings = LinkCheck.resolve(listing)
        assertEquals(Ending.Runtime, endings["/system/lib64/libc.so"])
        assertEquals(Ending.InTree("system/system/bin"), endings["/bin"])
        assertEquals(
            Ending.InTree("system_ext/lib64/libwfdnative.so"),
            endings["/system_ext/priv-app/WfdService/lib/arm64/libwfdnative.so"],
        )
        assertEquals(Ending.InTree("system_ext/bin/mount.exfat"), endings["/system_ext/bin/mkfs.exfat"])
        assertEquals(Ending.InTree("vendor/lib64/egl/libEGL_adreno.so"), endings["/vendor/lib64/libEGL_adreno.so"])
        assertEquals(Ending.Loop, endings["/vendor/x/a"])
        // The odm partition is mounted over system's /odm folder, so system's links there do not exist.
        assertEquals(null, endings["/odm/app"])
    }

    @Test
    fun onlyTheLinksTheBuildBrokeAreReported() {
        val endings = LinkCheck.resolve(listing)
        val stock = LinkCheck.broken(listing, endings, missing = emptySet())
        // A script removed the library the WfdService app links to.
        val build = LinkCheck.broken(listing, endings, missing = setOf("system_ext/lib64/libwfdnative.so"))
        assertEquals(listOf("/vendor/x/a", "/vendor/x/b"), stock.map { it.path })
        assertEquals(
            listOf(
                DeviceLink(
                    "/system_ext/priv-app/WfdService/lib/arm64/libwfdnative.so",
                    "/system_ext/lib64/libwfdnative.so",
                ),
            ),
            LinkCheck.newlyBroken(stock, build),
        )
    }
}
