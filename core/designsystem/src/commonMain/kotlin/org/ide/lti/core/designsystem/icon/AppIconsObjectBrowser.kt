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
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserAbbreviatePackageNames
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserCompactEmptyPackages
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserFlattenModules
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserFlattenPackages
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserShowLibraryContents
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserShowMembers
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserSortAlphabetically
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserSortByType
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserSortByUsage
import org.ide.lti.core.designsystem.generated.resources.ideObjectBrowserSortByVisibility
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "objectBrowser" icon category.
 * Auto-generated from the bulk-imported expui/objectBrowser/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideObjectBrowserAbbreviatePackageNames: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserAbbreviatePackageNames) }
val AppIcons.ideObjectBrowserCompactEmptyPackages: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserCompactEmptyPackages) }
val AppIcons.ideObjectBrowserFlattenModules: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserFlattenModules) }
val AppIcons.ideObjectBrowserFlattenPackages: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserFlattenPackages) }
val AppIcons.ideObjectBrowserShowLibraryContents: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserShowLibraryContents) }
val AppIcons.ideObjectBrowserShowMembers: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserShowMembers) }
val AppIcons.ideObjectBrowserSortAlphabetically: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserSortAlphabetically) }
val AppIcons.ideObjectBrowserSortByType: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserSortByType) }
val AppIcons.ideObjectBrowserSortByUsage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserSortByUsage) }
val AppIcons.ideObjectBrowserSortByVisibility: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideObjectBrowserSortByVisibility) }
