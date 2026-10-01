/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.serialization.Serializable

/**
 * On-disk workspace security keys located under `keys/`.
 */
@Serializable
data class WorkspaceKeys(
    val avbPrivateKey: String = "keys/avb.pem",
    val avbPublicKey: String = "keys/avb.avbpubkey",
    val platformPk8: String = "keys/platform.pk8",
    val platformCert: String = "keys/platform.x509.pem",
    val avbPublicKeySha1: String? = null,
    val platformCertSha1: String? = null,
)
