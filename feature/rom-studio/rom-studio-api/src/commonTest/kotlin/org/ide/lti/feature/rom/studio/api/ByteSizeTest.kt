/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.api

import kotlin.test.Test
import kotlin.test.assertEquals

class ByteSizeTest {
    @Test
    fun smallSizesAreNotShownAsZero() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("840 KB", formatBytes(860_160))
        assertEquals("31.9 MB", formatBytes(33_488_896))
        assertEquals("3.9 GB", formatBytes(4_190_728_192))
        assertEquals("1.7 GB", formatBytes(1_840_340_992))
        assertEquals("118 MB", formatBytes(123_731_968))
    }
}
