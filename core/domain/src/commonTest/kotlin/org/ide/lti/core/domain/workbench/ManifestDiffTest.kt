/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workbench

import kotlin.test.Test
import kotlin.test.assertEquals

/** Lines as `BootWorkbench.MANIFEST_SCRIPT` prints them for PQ84P01's unpacked init_boot. */
class ManifestDiffTest {
    private val before = listOf(
        "ramdisk/debug_ramdisk\t755\t\t-",
        "ramdisk/dev\t755\t\t-",
        "ramdisk/dev/console\t755\t\te3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
        "unpacked/kernel\t\t\te3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    ).joinToString("\n")

    @Test
    fun aModeChangeAnAddedFileAndARemovedOneAreCounted() {
        val after = before
            .replace("ramdisk/dev\t755", "ramdisk/dev\t700")
            .replace("ramdisk/debug_ramdisk\t755\t\t-\n", "") + "\nramdisk/overlay.d\t750\t\t-"
        assertEquals(ManifestDiff(changed = 1, added = 1, removed = 1), ManifestDiff.of(before, after))
        assertEquals(0, ManifestDiff.of(before, before).total)
    }
}
