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

class TransferLineTest {
    @Test
    fun showsSizeShareSpeedAndTimeLeft() {
        assertEquals("305.0 MB of 7.16 GB (4%)", transferLine(305_000_000, 7_158_897_436, null))
        assertEquals(
            "3.21 of 7.16 GB (44%) · 24.3 MB/s · about 3 min left".replace("3.21 of", "3.21 GB of"),
            transferLine(3_210_000_000, 7_158_897_436, 24_300_000.0),
        )
        assertEquals(
            "1.00 GB of 7.16 GB (13%) · 0.3 MB/s · about 5 h 42 min left",
            transferLine(1_000_000_000, 7_158_897_436, 300_000.0),
        )
    }
}
