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

import kotlinx.coroutines.test.runTest
import org.ide.lti.core.model.security.CredentialRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecretStorePortTest {

    private class InMemorySecretStore : SecretStorePort {
        private val vault = mutableMapOf<CredentialRef, String>()

        override suspend fun getSecret(ref: CredentialRef): String? = vault[ref]

        override suspend fun storeSecret(ref: CredentialRef, secret: String) {
            vault[ref] = secret
        }

        override suspend fun deleteSecret(ref: CredentialRef) {
            vault.remove(ref)
        }

        override suspend fun hasSecret(ref: CredentialRef): Boolean = vault.containsKey(ref)
    }

    @Test
    fun credentialRefRejectsBlankOrInvalidKeys() {
        assertFailsWith<IllegalArgumentException> {
            CredentialRef("")
        }
        assertFailsWith<IllegalArgumentException> {
            CredentialRef("bad\nkey")
        }
    }

    @Test
    fun secretStoreRoundtrip() = runTest {
        val store = InMemorySecretStore()
        val ref = CredentialRef("github-token-v1")
        assertFalse(store.hasSecret(ref))
        store.storeSecret(ref, "ghp_superSecretToken123")
        assertTrue(store.hasSecret(ref))
        assertEquals("ghp_superSecretToken123", store.getSecret(ref))
        store.deleteSecret(ref)
        assertFalse(store.hasSecret(ref))
    }

    @Test
    fun secretRedactorReplacesSecrets() {
        val raw = "Publishing to repo with token ghp_superSecretToken123 on branch main"
        val redacted = SecretRedactor.redact(raw, listOf("ghp_superSecretToken123"))
        assertEquals("Publishing to repo with token ***REDACTED*** on branch main", redacted)
        assertFalse(redacted.contains("ghp_superSecretToken123"))
    }
}
