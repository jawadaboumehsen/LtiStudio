/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.workspace

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.ide.lti.core.model.target.TargetBinding

/**
 * Represents a recently opened project for quick access.
 */
@Serializable
data class RecentProject(
    val workspaceId: String,
    val name: String,
    val path: String,
    val lastOpened: Instant,
    val type: WorkspaceType = WorkspaceType.LOCAL,
    val targetBinding: TargetBinding? = null,
    val targetDisplayName: String? = null,
)
