/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.utils

import kotlin.test.Test
import kotlin.test.assertEquals

class Sha256Test {

    @Test
    fun testEmptyStringDigest() {
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, sha256(""))
        assertEquals(expected, sha256(byteArrayOf()))
    }

    @Test
    fun testKnownStringDigest() {
        // SHA-256("hello world")
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9"
        assertEquals(expected, sha256("hello world"))
    }
}
