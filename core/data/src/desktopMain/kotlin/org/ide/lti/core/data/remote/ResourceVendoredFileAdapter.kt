/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.remote

import org.ide.lti.core.domain.ports.VendoredFile
import org.ide.lti.core.domain.ports.VendoredFilePort
import java.security.MessageDigest

/**
 * Loads vendored prebuilts from the desktop classpath (`core/data/src/desktopMain/resources/`).
 * A file is only returned when `<id>.sha256` exists next to it and its first token matches the
 * file's actual digest; otherwise null, so the pipeline refuses to package a substitute.
 */
public class ResourceVendoredFileAdapter(
    private val loader: (String) -> ByteArray? = { name ->
        ResourceVendoredFileAdapter::class.java.classLoader?.getResourceAsStream(name)?.use { it.readBytes() }
    },
) : VendoredFilePort {

    override fun load(resourceId: String): VendoredFile? {
        val bytes = loader(resourceId)
        val pinned = loader("$resourceId.sha256")?.decodeToString()?.trim()?.split(Regex("\\s+"))?.firstOrNull()
        val actual = bytes?.let { MessageDigest.getInstance("SHA-256").digest(it) }
            ?.joinToString("") { b -> "%02x".format(b) }
        return if (bytes != null && actual != null && actual.equals(pinned, ignoreCase = true)) {
            VendoredFile(bytes, actual)
        } else {
            null
        }
    }
}
