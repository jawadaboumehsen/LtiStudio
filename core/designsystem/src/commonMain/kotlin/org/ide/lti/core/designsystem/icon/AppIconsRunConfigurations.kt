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
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsApplication
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsApplicationRemote
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsCompound
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsCoverage
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsIgnoredTest
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsInvalidConfigurationLayer
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsJunit
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsJunitTestMark
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsMultiLaunch
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsRemoteDebug
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsSortByDuration
import org.ide.lti.core.designsystem.generated.resources.ideRunConfigurationsWebApp
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "runConfigurations" icon category.
 * Auto-generated from the bulk-imported expui/runConfigurations/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideRunConfigurationsApplication: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsApplication) }
val AppIcons.ideRunConfigurationsApplicationRemote: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsApplicationRemote) }
val AppIcons.ideRunConfigurationsCompound: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsCompound) }
val AppIcons.ideRunConfigurationsCoverage: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsCoverage) }
val AppIcons.ideRunConfigurationsIgnoredTest: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsIgnoredTest) }
val AppIcons.ideRunConfigurationsInvalidConfigurationLayer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsInvalidConfigurationLayer) }
val AppIcons.ideRunConfigurationsJunit: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsJunit) }
val AppIcons.ideRunConfigurationsJunitTestMark: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsJunitTestMark) }
val AppIcons.ideRunConfigurationsMultiLaunch: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsMultiLaunch) }
val AppIcons.ideRunConfigurationsRemoteDebug: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsRemoteDebug) }
val AppIcons.ideRunConfigurationsSortByDuration: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsSortByDuration) }
val AppIcons.ideRunConfigurationsWebApp: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideRunConfigurationsWebApp) }
