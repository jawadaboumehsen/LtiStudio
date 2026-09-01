/*
 * Copyright 2024 Mifos Initiative
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
import org.ide.lti.core.designsystem.generated.resources.ideBuildCompletionCloud
import org.ide.lti.core.designsystem.generated.resources.ideBuildCompletionLocalCache
import org.ide.lti.core.designsystem.generated.resources.ideBuildDependencyAnalyzer
import org.ide.lti.core.designsystem.generated.resources.ideBuildRebuild
import org.ide.lti.core.designsystem.generated.resources.ideBuildTaskGroup
import org.ide.lti.core.designsystem.generated.resources.ideBuildToggleOfflineMode
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "build" icon category.
 * Auto-generated from the bulk-imported expui/build/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideBuildBuild: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildBuild) }
val AppIcons.ideBuildCompletionCloud: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildCompletionCloud) }
val AppIcons.ideBuildCompletionLocalCache: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildCompletionLocalCache) }
val AppIcons.ideBuildDependencyAnalyzer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildDependencyAnalyzer) }
val AppIcons.ideBuildRebuild: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildRebuild) }
val AppIcons.ideBuildTaskGroup: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildTaskGroup) }
val AppIcons.ideBuildToggleOfflineMode: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideBuildToggleOfflineMode) }
