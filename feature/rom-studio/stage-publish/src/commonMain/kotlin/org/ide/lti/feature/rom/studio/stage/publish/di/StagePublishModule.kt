/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.publish.di

import org.ide.lti.feature.rom.studio.api.StageEditor
import org.ide.lti.feature.rom.studio.stage.publish.PublishStageEditor
import org.ide.lti.feature.rom.studio.stage.publish.PublishViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.core.qualifier.named
import org.koin.dsl.module

public val stagePublishModule: Module = module {
    single<StageEditor>(named("publish")) { PublishStageEditor() }

    viewModel { params ->
        PublishViewModel(
            workspaceId = params.getOrNull<String>() ?: "",
        )
    }
}
