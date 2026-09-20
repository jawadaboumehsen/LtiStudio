/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.ide.lti.core.cli.di.cliModule
import org.ide.lti.core.common.di.CommonModule
import org.ide.lti.core.common.di.DispatchersModule
import org.ide.lti.core.data.di.DataModule
import org.ide.lti.core.domain.di.DomainModule
import org.ide.lti.core.domain.repository.user.UserDataRepository
import org.ide.lti.core.ui.settings.AppSettingsState
import org.ide.lti.feature.configuration.di.configurationModule
import org.ide.lti.feature.plugins.di.pluginsModule
import org.ide.lti.feature.rom.studio.stage.acquire.di.stageAcquireModule
import org.ide.lti.feature.rom.studio.stage.assemble.di.stageAssembleModule
import org.ide.lti.feature.rom.studio.stage.build.di.stageBuildModule
import org.ide.lti.feature.rom.studio.stage.debloat.di.stageDebloatModule
import org.ide.lti.feature.rom.studio.stage.extract.di.stageExtractModule
import org.ide.lti.feature.rom.studio.stage.metadata.di.stageMetadataModule
import org.ide.lti.feature.rom.studio.stage.patch.di.stagePatchModule
import org.ide.lti.feature.rom.studio.stage.publish.di.stagePublishModule
import org.ide.lti.feature.settings.di.settingsModule
import org.ide.lti.feature.setup.di.setupModule
import org.ide.lti.feature.workspace.creation.di.workspaceCreateModule
import org.ide.lti.feature.workspace.panels.run.di.workspaceRunModule
import org.ide.lti.feature.workspace.panels.target.di.workspaceTargetModule
import org.ide.lti.feature.workspace.studio.di.romStudioShellModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication
import org.koin.dsl.module

expect val platformModules: List<Module>

object KoinModules {
    private val commonModule = module {
        includes(CommonModule)
    }

    private val dataModule = module {
        includes(DataModule)
    }

    private val dispatcherModule = module {
        includes(DispatchersModule)
    }

    private val domainModule = module {
        includes(DomainModule)
    }

    private val coreModules = module {
        includes(cliModule)
        single {
            val repo = get<UserDataRepository>()
            val initialData = repo.currentUserData
            AppSettingsState(
                initialTheme = initialData.theme,
                initialEffectsEnabled = initialData.effectsEnabled,
                initialReducedMotion = initialData.reducedMotion,
                initialEditorSettings = initialData.editorSettings,
                initialNotificationSettings = initialData.notificationSettings,
                userDataRepository = repo,
                coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
            )
        }
    }

    private val featureModules = module {
        includes(
            setupModule,
            configurationModule,
            settingsModule,
            pluginsModule,
            romStudioShellModule,
            workspaceCreateModule,
            workspaceTargetModule,
            workspaceRunModule,
            stageAcquireModule,
            stageExtractModule,
            stageAssembleModule,
            stageDebloatModule,
            stagePatchModule,
            stageBuildModule,
            stageMetadataModule,
            stagePublishModule,
        )
    }

    val allModules = listOf(
        commonModule,
        dataModule,
        dispatcherModule,
        domainModule,
        coreModules,
        featureModules,
    ) + platformModules
}

fun koinConfiguration() = koinApplication {
    modules(KoinModules.allModules)
}

fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(KoinModules.allModules)
    }
}
