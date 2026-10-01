/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

/**
 * Domain port for uploading files to a remote environment (e.g. WSL or remote daemon).
 * Decouples presentation and domain layers from specific tooling transport implementations.
 */
fun interface RemoteFileUploaderPort {
    suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean
}
