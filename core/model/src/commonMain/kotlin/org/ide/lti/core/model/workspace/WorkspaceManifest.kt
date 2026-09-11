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
import org.ide.lti.core.model.target.TargetBinding

/**
 * On-disk workspace manifest (workspace.json).
 *
 * Written once at creation; used to re-discover workspaces and detect layout drift.
 */
@Serializable
data class WorkspaceManifest(
    val id: String,
    val layoutVersion: Int = 1,
    val targetBinding: TargetBinding,
    val createdAt: String,
    val appVersion: String,
)
