/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.common.di

import org.ide.lti.core.common.event.PanelEventBus
import org.ide.lti.core.common.notification.DelegatingSystemNotifier
import org.ide.lti.core.common.notification.SystemNotifier
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val CommonModule = module {
    singleOf(::PanelEventBus)
    singleOf(::DelegatingSystemNotifier) { bind<SystemNotifier>() }
}
