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

import org.ide.lti.core.data.admission.WorkspaceAdmissionRepository
import org.ide.lti.core.data.common.resolveAppDataDir
import org.ide.lti.core.data.configuration.SnapshotMaterializer
import org.ide.lti.core.data.plugin.InstalledPluginRepository
import org.ide.lti.core.data.plugin.PluginIndexAdapter
import org.ide.lti.core.data.plugin.author.AuthorTaskProcessAdapter
import org.ide.lti.core.data.remote.EnvironmentReadinessAdapter
import org.ide.lti.core.data.remote.RemoteWorkspaceProvisioner
import org.ide.lti.core.data.remote.ResourceVendoredFileAdapter
import org.ide.lti.core.data.remote.StepExecutionAdapter
import org.ide.lti.core.data.remote.ToolPublicationAdapter
import org.ide.lti.core.data.remote.WorkspaceKeyProvisioner
import org.ide.lti.core.data.repository.device.ConnectedDeviceRepositoryImpl
import org.ide.lti.core.data.repository.filesystem.DesktopFileSystemRepository
import org.ide.lti.core.data.repository.run.RunRepositoryImpl
import org.ide.lti.core.domain.plugin.AuthorProjectTrustPort
import org.ide.lti.core.domain.plugin.EmptyPluginCatalog
import org.ide.lti.core.domain.plugin.MarketplaceIndexPort
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.ports.EnvironmentReadinessPort
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.SnapshotMaterializerPort
import org.ide.lti.core.domain.ports.ToolPublicationPort
import org.ide.lti.core.domain.ports.VendoredFilePort
import org.ide.lti.core.domain.ports.WorkspaceAdmissionPort
import org.ide.lti.core.domain.ports.WorkspaceProvisioningPort
import org.ide.lti.core.domain.repository.device.ConnectedDeviceRepository
import org.ide.lti.core.domain.repository.filesystem.FileSystemRepository
import org.ide.lti.core.domain.repository.run.RunRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

internal actual val platformRepositoryModule = module {
    singleOf(::DesktopFileSystemRepository) { bind<FileSystemRepository>() }
    singleOf(::ConnectedDeviceRepositoryImpl) { bind<ConnectedDeviceRepository>() }
    // Explicit lambdas: `singleOf` resolves every constructor parameter through Koin, including
    // the defaulted `Set<String>`/`Json` ones, which have no definition and crash at startup.
    single<ToolPublicationPort> { ToolPublicationAdapter(transport = get()) }
    single<EnvironmentReadinessPort> {
        EnvironmentReadinessAdapter(
            toolchainService = get(),
            transport = get(),
        )
    }
    single { WorkspaceKeyProvisioner(transport = get()) }
    single<WorkspaceProvisioningPort> {
        RemoteWorkspaceProvisioner(transport = get(), readinessPort = get(), keyProvisioner = get())
    }
    single<PipelineExecutionPort> {
        StepExecutionAdapter(transport = get())
    }
    single<VendoredFilePort> { ResourceVendoredFileAdapter() }
    single<RunRepository> {
        RunRepositoryImpl(
            runRecordDataSource = get(),
            workspaceRepository = getOrNull(),
            executionPort = getOrNull(),
        )
    }
    single<PluginInstallPort> {
        InstalledPluginRepository(storeDir = resolveAppDataDir("plugins/installed"))
    }
    single<MarketplaceIndexPort> {
        PluginIndexAdapter(cacheDir = resolveAppDataDir("plugins/index-cache"))
    }
    single<AuthorProjectTrustPort> {
        AuthorTaskProcessAdapter(trustStoreDir = resolveAppDataDir("plugins/author-trust"))
    }
    // Bind PluginCatalogPort to EmptyPluginCatalog explicitly; a real catalog-backed implementation is a later task.
    single<PluginCatalogPort> { EmptyPluginCatalog }

    single<WorkspaceAdmissionPort> {
        WorkspaceAdmissionRepository(storageDir = resolveAppDataDir("workspace-admission"))
    }
    single<SnapshotMaterializerPort> {
        SnapshotMaterializer(storageDir = resolveAppDataDir("snapshot-intents"))
    }
}
