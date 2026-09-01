/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ide.lti.core.domain.ai.AIService
import org.ide.lti.core.domain.ai.AIServiceImpl
import org.ide.lti.core.domain.connection.ConnectionManager
import org.ide.lti.core.domain.panels.PanelManager
import org.ide.lti.core.domain.terminal.TerminalManager
import org.ide.lti.core.domain.workspace.WorkspaceManager
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val DomainModule = module {
    // Coroutine scope for managers
    single { CoroutineScope(SupervisorJob() + Dispatchers.Default) }

    // Managers and Services
    singleOf(::WorkspaceManager)
    singleOf(::PanelManager)
    singleOf(::TerminalManager)
    singleOf(::ConnectionManager)
    singleOf(::AIServiceImpl) { bind<AIService>() }
}
