/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.setup

import org.ide.lti.core.domain.setup.RecipeConfig
import org.ide.lti.core.domain.setup.ToolGroupCatalog

/**
 * A prebuilt tool installed from an upstream release archive, pinned by version and by the sha256 of the
 * extracted binary (the same digest `ToolMetadataRegistry` expects). A download that doesn't match is
 * never installed.
 */
internal data class PinnedRelease(
    val toolId: String,
    val version: String,
    val url: String,
    /** Path of the binary inside the archive. */
    val archiveMember: String,
    val sha256: String,
)

internal object PinnedReleases {
    val ALL: List<PinnedRelease>
        get() = ToolGroupCatalog.allGroups.mapNotNull { group ->
            val releaseRecipe = group.recipe as? RecipeConfig.Release ?: return@mapNotNull null
            val version = releaseRecipe.versions.firstOrNull() ?: return@mapNotNull null
            val output = group.outputs.firstOrNull() ?: return@mapNotNull null
            PinnedRelease(
                toolId = output.toolId,
                version = version.version,
                url = version.url,
                archiveMember = version.archiveMember,
                sha256 = version.sha256,
            )
        }
}
