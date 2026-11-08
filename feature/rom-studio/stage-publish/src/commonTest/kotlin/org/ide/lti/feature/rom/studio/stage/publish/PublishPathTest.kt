/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish

import kotlin.test.Test
import kotlin.test.assertEquals

class PublishPathTest {
    @Test
    fun aLocalExportShowsTheWindowsFolderAndAnythingElseStaysAsItIs() {
        assertEquals("D:\\ROM\\releases", shownPath("/mnt/d/ROM/releases"))
        assertEquals("C:\\", shownPath("/mnt/c"))
        assertEquals("github.com/owner/repo", shownPath("github.com/owner/repo"))
        assertEquals("/home/alice/out", shownPath("/home/alice/out"))
    }

    @Test
    fun aMessageShowsItsLocalExportPathsAsWindowsFolders() {
        assertEquals("Exported to C:\\ROM\\out", withShownPaths("Exported to /mnt/c/ROM/out"))
        assertEquals(
            "sha256sum C:\\a.zip failed: C:\\a.zip: No such file",
            withShownPaths("sha256sum /mnt/c/a.zip failed: /mnt/c/a.zip: No such file"),
        )
    }
}
