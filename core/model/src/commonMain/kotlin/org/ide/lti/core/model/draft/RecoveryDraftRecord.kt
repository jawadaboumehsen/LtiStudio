/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.model.draft

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.ide.lti.core.model.workspace.AcquisitionSettings
import org.ide.lti.core.model.workspace.AssemblySettings
import org.ide.lti.core.model.workspace.BuildSettings
import org.ide.lti.core.model.workspace.CustomizationSettings
import org.ide.lti.core.model.workspace.DebloatSettings
import org.ide.lti.core.model.workspace.ExtractionSettings
import org.ide.lti.core.model.workspace.PublishSettings
import org.ide.lti.core.model.workspace.ReleaseSettings

@Serializable
public data class RecoveryDraftRecord(
    val schema: Int = 1,
    val workspaceId: String,
    val baseSnapshotId: String?,
    val baseSnapshotDigest: String?,
    val draftRevision: Long,
    val discardGeneration: Long = 0L,
    val acquisition: AcquisitionSettings,
    val extraction: ExtractionSettings,
    val assembly: AssemblySettings,
    val debloat: DebloatSettings,
    val customization: CustomizationSettings,
    val build: BuildSettings,
    val release: ReleaseSettings,
    val publish: PublishSettings,
    val dirtyPaths: Set<String> = emptySet(),
    val lastPersistedAt: Instant,
    val checksum: String = "",
    val tombstoned: Boolean = false,
)

public data class RecoveryDraftReadResult(
    val record: RecoveryDraftRecord?,
    val baseDiverged: Boolean = false,
    val wasCorrupt: Boolean = false,
)
