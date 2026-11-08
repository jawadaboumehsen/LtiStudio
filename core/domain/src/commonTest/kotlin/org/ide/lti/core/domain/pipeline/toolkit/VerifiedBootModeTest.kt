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

class VerifiedBootModeTest {
    @Test
    fun theTwoVerificationBitsMapToAModeAndOtherFlagsAreKept() {
        assertEquals(VerifiedBootMode.VERIFY_ALL, VerifiedBootMode.of(0))
        assertEquals(VerifiedBootMode.NONE, VerifiedBootMode.of(3))
        // DO_NOT_USE_AB (4) is no part of the mode and survives a change of it.
        assertEquals(VerifiedBootMode.NO_VERITY, VerifiedBootMode.of(5))
        assertEquals(6, VerifiedBootMode.NO_VERIFICATION.applyTo(5))
        assertEquals(4, VerifiedBootMode.VERIFY_ALL.applyTo(7))
    }
}
