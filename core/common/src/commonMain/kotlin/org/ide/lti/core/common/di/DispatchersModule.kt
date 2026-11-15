/*
 * Copyright 2024 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.di

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val DispatchersModule = module {
    includes(ioDispatcherModule)
    single<CoroutineDispatcher>(named(AppDispatchers.Default.name)) { Dispatchers.Default }
    single<CoroutineDispatcher>(named(AppDispatchers.Unconfined.name)) { Dispatchers.Unconfined }
}

expect val ioDispatcherModule: Module
