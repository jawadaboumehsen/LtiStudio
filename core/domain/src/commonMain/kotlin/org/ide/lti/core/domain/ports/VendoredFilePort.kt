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
 * Resolves product-vendored prebuilt files (currently only the recovery `update-binary`) by id.
 * Implementations must verify the file against its pinned sha256 and return null when the file is
 * absent or the digest does not match; the pipeline then fails with a remediation instead of
 * packaging a substitute.
 */
public interface VendoredFilePort {
    public fun load(resourceId: String): VendoredFile?
}

public data class VendoredFile(val bytes: ByteArray, val sha256: String)
