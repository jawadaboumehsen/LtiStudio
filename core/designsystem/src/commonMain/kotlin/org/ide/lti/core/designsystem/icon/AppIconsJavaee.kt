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
import org.ide.lti.core.designsystem.generated.resources.ideJavaeeHome
import org.ide.lti.core.designsystem.generated.resources.ideJavaeePersistenceEntity
import org.ide.lti.core.designsystem.generated.resources.ideJavaeeUpdateRunningApplication
import org.ide.lti.core.designsystem.generated.resources.ideJavaeeWebModuleGroup
import org.ide.lti.core.designsystem.generated.resources.ideJavaeeWebService
import org.ide.lti.core.designsystem.generated.resources.ideJavaeeWebServiceClient
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "javaee" icon category.
 * Auto-generated from the bulk-imported expui/javaee/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideJavaeeHome: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeeHome) }
val AppIcons.ideJavaeePersistenceEntity: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeePersistenceEntity) }
val AppIcons.ideJavaeeUpdateRunningApplication: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeeUpdateRunningApplication) }
val AppIcons.ideJavaeeWebModuleGroup: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeeWebModuleGroup) }
val AppIcons.ideJavaeeWebService: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeeWebService) }
val AppIcons.ideJavaeeWebServiceClient: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideJavaeeWebServiceClient) }
