/*
 * Copyright 2025 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.di

import org.ide.lti.core.data.remote.EnvironmentReadinessAdapter
import org.ide.lti.core.data.remote.RemoteWorkspaceProvisioner
import org.ide.lti.core.data.remote.ToolPublicationAdapter
import org.ide.lti.core.data.remote.WorkspaceKeyProvisioner
import org.ide.lti.core.data.repository.device.ConnectedDeviceRepositoryImpl
import org.ide.lti.core.data.repository.filesystem.DesktopFileSystemRepository
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.domain.repository.filesystem.FileSystemRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

import org.ide.lti.core.data.remote.ResourceVendoredFileAdapter
import org.ide.lti.core.data.remote.StepExecutionAdapter
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.VendoredFilePort

internal actual val platformRepositoryModule = module {
    singleOf(::DesktopFileSystemRepository) { bind<FileSystemRepository>() }
    singleOf(::ConnectedDeviceRepositoryImpl) { bind<ConnectedDeviceRepository>() }
    // Explicit lambdas: `singleOf` resolves every constructor parameter through Koin, including
    // the defaulted `Set<String>`/`Json` ones, which have no definition and crash at startup.
    single<ToolPublicationPort> { ToolPublicationAdapter(transport = get()) }
    single<EnvironmentReadinessPort> {
        EnvironmentReadinessAdapter(wslPresence = get(), supervisor = get(), transport = get())
    }
    single { WorkspaceKeyProvisioner(transport = get()) }
    single<WorkspaceProvisioningPort> {
        RemoteWorkspaceProvisioner(transport = get(), readinessPort = get(), keyProvisioner = get())
    }
    single<PipelineExecutionPort> {
        StepExecutionAdapter(transport = get())
    }
    single<VendoredFilePort> { ResourceVendoredFileAdapter() }
}

