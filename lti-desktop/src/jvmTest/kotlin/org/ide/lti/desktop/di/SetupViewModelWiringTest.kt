/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.desktop.di

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.ide.lti.core.domain.tools.ToolManager
import org.ide.lti.feature.setup.SetupViewModel
import org.ide.lti.shared.di.KoinModules
import org.ide.lti.shared.di.toolingModule
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SetupViewModelWiringTest {
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        stopKoin()
        koin =
            startKoin {
                modules(toolingModule)
                modules(KoinModules.allModules)
            }.koin
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun setupViewModelExposesToolsPageStateBuiltOnKoinToolManager() {
        val toolManager = koin.get<ToolManager>()
        val vm = koin.get<SetupViewModel>()

        val pageState = vm.toolsPageState
        assertNotNull(pageState, "SetupViewModel must expose toolsPageState")
        assertEquals(toolManager, pageState.manager, "toolsPageState must be built on the Koin ToolManager")
    }

    @Test
    fun toolsTabRowsComeFromToolsPageStateRows() = runBlocking {
        val vm = koin.get<SetupViewModel>()
        val pageRows = vm.toolsPageState.rows.first()
        val exposedRows = vm.toolsRows.first()

        assertTrue(pageRows.isNotEmpty(), "Tools tab rows must be populated from ToolsPageState")
        assertEquals(pageRows, exposedRows, "Tools tab rows must match ToolsPageState.rows")
    }

    @Test
    fun toolVersionsStateAndToolBrowserStateAreNotResolvableOrExposed() {
        val versionsStateInKoin =
            runCatching {
                val clazz = Class.forName("org.ide.lti.feature.setup.versions.ToolVersionsState").kotlin
                koin.getOrNull<Any>(clazz, qualifier = null, parameters = null)
            }.getOrNull()
        assertNull(versionsStateInKoin, "ToolVersionsState must not be resolvable in Koin")

        val browserStateInKoin =
            runCatching {
                val clazz = Class.forName("org.ide.lti.feature.setup.ToolBrowserState").kotlin
                koin.getOrNull<Any>(clazz, qualifier = null, parameters = null)
            }.getOrNull()
        assertNull(browserStateInKoin, "ToolBrowserState must not be resolvable in Koin")

        val vmClass = SetupViewModel::class.java
        val hasVersionsState = vmClass.methods.any { it.name == "getToolVersionsState" }
        val hasBrowserState = vmClass.methods.any { it.name == "getToolBrowserState" }
        assertFalse(hasVersionsState, "SetupViewModel must not expose toolVersionsState")
        assertFalse(hasBrowserState, "SetupViewModel must not expose toolBrowserState")
    }

    @Test
    fun viewModelNeverStartsAutomaticToolsRunItself() {
        val vm = koin.get<SetupViewModel>()
        val isRunning = vm.toolchainSetupState.value.isRunning
        assertFalse(isRunning, "SetupViewModel must not start automatic tools run itself")
    }
}
