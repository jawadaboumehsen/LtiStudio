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

import org.ide.lti.core.common.di.CommonModule
import org.ide.lti.core.common.di.DispatchersModule
import org.ide.lti.core.data.di.DataModule
import org.ide.lti.core.domain.di.DomainModule
import org.ide.lti.feature.setup.SetupViewModel
import org.ide.lti.feature.workspace.di.workspaceModule
import org.koin.core.context.startKoin
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.KoinAppDeclaration
import org.koin.dsl.koinApplication
import org.koin.dsl.module

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
        // Empty for now - can include other non-standard core modules if needed
    }

    private val featureModules = module {
        includes(
            workspaceModule,
        )
        viewModelOf(::SetupViewModel)
    }

    val allModules = listOf(
        commonModule,
        dataModule,
        dispatcherModule,
        domainModule,
        coreModules,
        featureModules,
    )
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
