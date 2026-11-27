/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.security

import org.ide.lti.core.model.security.CredentialRef

/**
 * Domain port for secure credential storage.
 * Infrastructure implementations (OS keychain, secure storage, in-memory test vault)
 * implement this interface.
 */
interface SecretStorePort {
    suspend fun getSecret(ref: CredentialRef): String?
    suspend fun storeSecret(ref: CredentialRef, secret: String)
    suspend fun deleteSecret(ref: CredentialRef)
    suspend fun hasSecret(ref: CredentialRef): Boolean
}

/**
 * Utility for redacting sensitive values from strings before logging or UI export.
 */
object SecretRedactor {
    const val REDACTED_PLACEHOLDER: String = "***REDACTED***"

    fun redact(raw: String, secrets: Collection<String>): String {
        var result = raw
        for (secret in secrets) {
            if (secret.isNotBlank()) {
                result = result.replace(secret, REDACTED_PLACEHOLDER)
            }
        }
        return result
    }

    fun redactSecretValue(secret: String?): String {
        if (secret.isNullOrEmpty()) return ""
        return REDACTED_PLACEHOLDER
    }
}
