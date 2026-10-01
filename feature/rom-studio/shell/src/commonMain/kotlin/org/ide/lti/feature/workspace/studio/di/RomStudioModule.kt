/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio.di

import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.rom.studio.shell.registry.DefaultStageEditorRegistry
import org.ide.lti.feature.workspace.studio.StudioViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val romStudioShellModule: Module = module {
    single<StageEditorRegistry> {
        DefaultStageEditorRegistry(
            editors = getAll(),
            enforceFullPipeline = false,
        )
    }

    viewModel { params ->
        StudioViewModel(
            studioPresentationStore = get(),
            stageEditorRegistry = get(),
            initialWorkspaceId = params.getOrNull<String>() ?: "",
            workspaceManager = getOrNull(),
        )
    }
}

public val romStudioModule: Module get() = romStudioShellModule
