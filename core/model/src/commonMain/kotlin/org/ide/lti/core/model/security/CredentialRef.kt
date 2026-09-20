/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.security

import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

/**
 * Symbolic reference to a secret stored securely in an external vault / keystore.
 * Raw secrets/passwords/tokens must never be placed in settings or recovery drafts.
 */
@Serializable
@JvmInline
public value class CredentialRef(public val key: String) {
    init {
        require(key.isNotBlank()) { "CredentialRef key must not be blank" }
        require(!key.contains('\n') && !key.contains('\u0000')) {
            "CredentialRef key must not contain newlines or null bytes"
        }
    }

    override fun toString(): String = "CredentialRef($key)"
}
