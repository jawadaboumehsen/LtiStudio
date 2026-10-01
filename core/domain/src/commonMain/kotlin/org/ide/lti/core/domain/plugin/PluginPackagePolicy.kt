/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.plugin

import org.ide.lti.core.domain.ports.MAX_TRANSFER_CHUNK_BYTES

/**
 * Versioned package policy limits.
 *
 * These limits do not constrain ROM archive size and may only change through a reviewed schema/policy update.
 */
object PluginPackagePolicy {
    const val MANIFEST_MAX_BYTES: Long = 1024L * 1024L
    const val PLAN_MAX_BYTES: Long = 1024L * 1024L
    const val SETTINGS_MAX_BYTES: Long = 1024L * 1024L
    const val README_MAX_BYTES: Long = 256L * 1024L
    const val MAX_OPERATIONS: Int = 10_000
    const val MAX_PACKAGE_MEMBERS: Int = 10_000
    const val MAX_CONDITION_DEPTH: Int = 16
    const val PACKAGE_MAX_COMPRESSED_BYTES: Long = 4L * 1024L * 1024L * 1024L
    const val PACKAGE_MAX_EXPANDED_BYTES: Long = 8L * 1024L * 1024L * 1024L
    const val POLICY_VERSION: Int = 1

    val TRANSFER_CHUNK_BYTES: Long
        get() = MAX_TRANSFER_CHUNK_BYTES
}
