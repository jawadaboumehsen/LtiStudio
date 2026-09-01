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
import org.ide.lti.core.designsystem.generated.resources.ideWebReferencesMessageQueue
import org.ide.lti.core.designsystem.generated.resources.ideWebReferencesOpenApi
import org.ide.lti.core.designsystem.generated.resources.ideWebReferencesServer
import org.ide.lti.core.designsystem.generated.resources.ideWebReferencesWebSocket
import org.jetbrains.compose.resources.painterResource

/**
 * Extension accessors for the JetBrains IntelliJ Platform "webReferences" icon category.
 * Auto-generated from the bulk-imported expui/webReferences/ icon set - see ICON_MANIFEST.md.
 */
val AppIcons.ideWebReferencesMessageQueue: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWebReferencesMessageQueue) }
val AppIcons.ideWebReferencesOpenApi: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWebReferencesOpenApi) }
val AppIcons.ideWebReferencesServer: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWebReferencesServer) }
val AppIcons.ideWebReferencesWebSocket: @Composable () -> Painter
    get() = { painterResource(Res.drawable.ideWebReferencesWebSocket) }
