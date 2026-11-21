/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.panels.target

import org.ide.lti.core.domain.workbench.PhoneFacts
import org.ide.lti.core.domain.workbench.PhoneMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhoneUiStateTest {
    @Test
    fun aDisconnectedSelectionDoesNotFallBackToAnotherPhone() {
        val replacement = PhoneFacts("second", PhoneMode.ANDROID)
        val state = PhoneUiState(phones = listOf(replacement), serial = "first")
        assertNull(state.phone)
        assertEquals(replacement, state.copy(serial = replacement.serial).phone)
    }
}
