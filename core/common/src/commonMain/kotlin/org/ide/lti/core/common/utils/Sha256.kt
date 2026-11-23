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

import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

/**
 * Shared multiplatform SHA-256 digest computation utility.
 */
public object Sha256 {
    /**
     * Computes the SHA-256 hexadecimal hash string (lowercase) of the given UTF-8 [input].
     */
    public fun digest(input: String): String = input.encodeUtf8().sha256().hex()

    /**
     * Computes the SHA-256 hexadecimal hash string (lowercase) of the given [bytes].
     */
    public fun digest(bytes: ByteArray): String = bytes.toByteString().sha256().hex()
}

/**
 * Returns the SHA-256 hex string for this string in UTF-8.
 */
public fun sha256(input: String): String = Sha256.digest(input)

/**
 * Returns the SHA-256 hex string for these bytes.
 */
public fun sha256(bytes: ByteArray): String = Sha256.digest(bytes)
