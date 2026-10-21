/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.rom.studio.stage.patch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.plugin.InstalledRecord
import org.ide.lti.core.domain.plugin.PackageIdentity
import org.ide.lti.core.domain.plugin.PluginCatalogPort
import org.ide.lti.core.domain.plugin.PluginInstallPort
import org.ide.lti.core.domain.plugin.TrustDecision
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.plugin.Compatibility
import org.ide.lti.core.model.plugin.DependencyRef
import org.ide.lti.core.model.plugin.PluginPlanTemplate
import org.ide.lti.core.model.run.StageId
import org.ide.lti.core.model.target.DefaultTargetCatalog
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import org.ide.lti.feature.rom.studio.api.CanonicalStageDescriptors
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PatchStageEditorTest {

    @Test
    fun descriptorMatchesCanonicalStage() {
        val editor = PatchStageEditor()
        assertEquals(StageId.MODULE_APPLICATION, editor.stageId)
        assertEquals(CanonicalStageDescriptors.PATCH, editor.descriptor)
        assertEquals(6, editor.descriptor.subobjects.size)
        assertNotNull(editor.descriptor.subobject(CanonicalStageDescriptors.PATCH.subobjects.first().id))
    }

    @Test
    fun viewModelInitializesWithDefaultsAndUpdates() {
        val vm = PatchViewModel(workspaceId = "test-ws")
        assertEquals("test-ws", vm.workspaceId)
        assertNotNull(vm.settings.value)
        // No modules are enabled until the user installs/enables one - a workspace's patch
        // settings must never default to fabricated lockfile entries (see e42c3e3c).
        assertTrue(vm.settings.value.enabledPackages.isEmpty())

        vm.updateSettings(vm.settings.value.copy(enabledPackages = listOf("org.example:mod")))
        assertEquals(1, vm.settings.value.enabledPackages.size)
    }
}

class PatchViewModelToggleTest {
    private fun record(id: String, needs: List<String> = emptyList()) = InstalledRecord(
        identity = PackageIdentity("io.ltirom", id, "1.0.0", "sha256:$id"),
        trust = TrustDecision.TRUSTED_UNVERIFIED,
        enabledInWorkspaces = emptyList(),
        revoked = false,
        archived = false,
        dependencies = needs.map { DependencyRef("io.ltirom", it, "1.0.0") },
        conflicts = emptyList(),
    )

    private val port = object : PluginInstallPort {
        override suspend fun quarantine(sourceLabel: String) = null
        override suspend fun inspect(quarantined: Path) = error("unused")
        override suspend fun promote(quarantined: Path, identity: PackageIdentity) = false
        override suspend fun installed() = listOf(record("selinux"), record("logpersist", needs = listOf("selinux")))
        override suspend fun remove(identity: PackageIdentity) = false
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun enablingAPluginBringsItsDependencyFirstAndTheDependencyCannotBeDisabledAlone() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val vm = PatchViewModel(workspaceId = "ws", plugins = port)
            vm.refresh()
            vm.toggle("io.ltirom:logpersist")
            assertEquals(listOf("io.ltirom:selinux", "io.ltirom:logpersist"), vm.settings.value.enabledPackages)
            assertEquals(2, vm.settings.value.lockfile.entries.size)

            vm.toggle("io.ltirom:selinux")
            assertEquals(2, vm.settings.value.enabledPackages.size)
            assertNotNull(vm.facts.value.lockError)

            vm.toggle("io.ltirom:logpersist")
            assertEquals(listOf("io.ltirom:selinux"), vm.settings.value.enabledPackages)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun aPluginMadeForAnotherDeviceSaysSoAndCannotBeEnabled() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            // selinux is made for another tablet; logpersist names no device, so it fits any.
            val catalog = object : PluginCatalogPort {
                override fun templateFor(publisher: String, id: String, version: String) = PluginPlanTemplate(
                    publisher = publisher,
                    id = id,
                    version = version,
                    settings = emptyList(),
                    operations = emptyList(),
                    compatibility = if (id == "selinux") listOf(Compatibility("NX769J")) else emptyList(),
                )
                override fun contentDigestFor(publisher: String, id: String, version: String) = null
            }
            val store = object : WorkspaceSettingsStore {
                override suspend fun current(workspaceId: String) = null
                override suspend fun update(
                    workspaceId: String,
                    change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
                ) = Result.failure<ConfigurationSnapshot>(IllegalStateException("read-only"))
                override suspend fun device(workspaceId: String) = DefaultTargetCatalog.PQ84P01_DEFAULT
            }
            val vm = PatchViewModel(workspaceId = "ws", store = store, plugins = port, catalog = catalog)
            vm.refresh()

            val why = vm.facts.value.incompatible["io.ltirom:selinux"]
            assertEquals("is made for NX769J, not PQ84P01 ()", why)
            assertEquals(setOf("io.ltirom:selinux"), vm.facts.value.incompatible.keys)

            vm.toggle("io.ltirom:selinux")
            assertTrue(vm.settings.value.enabledPackages.isEmpty())
            assertEquals("io.ltirom:selinux $why", vm.facts.value.lockError)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
