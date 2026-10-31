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

class BootWorkbenchArgsTest {

    @Test
    fun anEmptyCommandLineStaysAnArgumentAndOnlyTheTrailingNewlineGoes() {
        // PQ84P01's init_boot, as unpack_bootimg --format mkbootimg -0 writes it.
        val initBoot = listOf("--header_version", "4", "--kernel", "k", "--ramdisk", "r", "--cmdline", "")
            .joinToString("\u0000", postfix = "\u0000\n")
        assertEquals(
            listOf("--header_version", "4", "--kernel", "k", "--ramdisk", "r", "--cmdline", ""),
            BootWorkbench.mkbootimgArgs(initBoot),
        )
        assertEquals(listOf("--cmdline", "x"), BootWorkbench.mkbootimgArgs("--cmdline\u0000x\u0000"))
    }
}
