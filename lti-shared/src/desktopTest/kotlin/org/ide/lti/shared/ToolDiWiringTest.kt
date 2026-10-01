/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.shared

import io.ltirom.tooling.adapters.adb.DevicesUseCase
import io.ltirom.tooling.adapters.avbtool.ExtractPublicKeyUseCase
import io.ltirom.tooling.core.ToolMetadataRegistry
import org.ide.lti.feature.rom.studio.api.StageEditorRegistry
import org.ide.lti.feature.workspace.studio.StudioViewModel
import org.ide.lti.shared.di.koinConfiguration
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ToolDiWiringTest {

    @Test
    fun testToolAdapterFamiliesResolvableFromKoin() {
        val application = koinConfiguration()

        try {
            val koin = application.koin
            assertNotNull(koin.getOrNull<DevicesUseCase>(), "adb DevicesUseCase must be resolvable from Koin")
            assertNotNull(
                koin.getOrNull<ExtractPublicKeyUseCase>(),
                "avbtool ExtractPublicKeyUseCase must be resolvable from Koin",
            )
            assertNotNull(koin.getOrNull<ToolMetadataRegistry>(), "ToolMetadataRegistry must be resolvable from Koin")
        } finally {
            application.close()
        }
    }

    @Test
    fun testRomStudioStageEditorsAndViewModelResolvableFromKoin() {
        val application = koinConfiguration()

        try {
            val koin = application.koin
            val registry = koin.getOrNull<StageEditorRegistry>()
            assertNotNull(registry, "StageEditorRegistry must be resolvable from Koin")
            assertEquals(8, registry.allEditors().size, "All 8 stage editors must be registered in Koin")

            val viewModel = koin.getOrNull<StudioViewModel>()
            assertNotNull(viewModel, "StudioViewModel must be resolvable from Koin")
            assertEquals(8, viewModel.shellState.value.pipelineItems.size, "Shell state must have 8 pipeline items")
        } finally {
            application.close()
        }
    }
}
