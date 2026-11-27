/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.designsystem.icon

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import org.ide.lti.core.designsystem.generated.resources.Res
import org.ide.lti.core.designsystem.generated.resources.ideBuildBuild
import org.ide.lti.core.designsystem.generated.resources.ideBuildTaskGroup
import org.ide.lti.core.designsystem.generated.resources.ideDelete
import org.ide.lti.core.designsystem.generated.resources.ideFileTypesManifest
import org.ide.lti.core.designsystem.generated.resources.ideGeneralDownload
import org.ide.lti.core.designsystem.generated.resources.ideGeneralUpload
import org.ide.lti.core.designsystem.generated.resources.ideNodesExtractedFolder
import org.ide.lti.core.designsystem.generated.resources.ideNodesPlugin
import org.jetbrains.compose.resources.painterResource

/**
 * Curated painter accessors for ROM setup pipeline stages, reusing existing JetBrains Expressive UI
 * drawable resources already imported across the design system.
 *
 * This object lives in `core:designsystem` and deliberately has no dependency on `core:model` or `StageId`.
 * The mapping between pipeline stages and these painter accessors is owned exclusively by the destination catalog.
 */
object AppIconsRomStages {
    val acquire: @Composable () -> Painter = { painterResource(Res.drawable.ideGeneralDownload) }
    val extract: @Composable () -> Painter = { painterResource(Res.drawable.ideNodesExtractedFolder) }
    val assemble: @Composable () -> Painter = { painterResource(Res.drawable.ideBuildTaskGroup) }
    val debloat: @Composable () -> Painter = { painterResource(Res.drawable.ideDelete) }
    val patches: @Composable () -> Painter = { painterResource(Res.drawable.ideNodesPlugin) }
    val build: @Composable () -> Painter = { painterResource(Res.drawable.ideBuildBuild) }
    val releaseMetadata: @Composable () -> Painter = { painterResource(Res.drawable.ideFileTypesManifest) }
    val publish: @Composable () -> Painter = { painterResource(Res.drawable.ideGeneralUpload) }
}
