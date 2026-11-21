/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.feature.workspace.studio

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.ide.lti.core.domain.pipeline.stages.PreflightReport
import org.ide.lti.core.domain.pipeline.stages.VintfAssessment
import org.ide.lti.core.domain.pipeline.stages.VintfEvidence
import org.ide.lti.core.domain.pipeline.stages.VintfEvidenceKind
import org.ide.lti.core.domain.pipeline.stages.VintfStatus
import org.ide.lti.core.domain.workspace.WorkspaceSettingsStore
import org.ide.lti.core.model.workspace.ConfigurationSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreflightViewModelTest {
    @Test
    fun aSettingsSaveClearsTheOldVintfVerdict() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = TestSettings()
            val vm = PreflightViewModel("one", report = { compatible() }, settings = settings)
            runCurrent()
            vm.check()
            runCurrent()
            assertEquals(VintfStatus.COMPATIBLE, vm.state.value.vintf?.status)
            settings.changes.emit("other")
            runCurrent()
            assertEquals(VintfStatus.COMPATIBLE, vm.state.value.vintf?.status)

            settings.changes.emit("one")
            runCurrent()
            assertNull(vm.state.value.vintf)
            assertTrue(vm.state.value.inputsChanged)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aSettingsSaveRestartsAnInFlightReport() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val settings = TestSettings()
            val releaseOldRead = CompletableDeferred<Unit>()
            var checks = 0
            val vm = PreflightViewModel("one", report = {
                if (++checks == 1) {
                    releaseOldRead.await()
                    compatible()
                } else {
                    PreflightReport(
                        emptyList(),
                        VintfAssessment(
                            VintfStatus.INCOMPLETE,
                            listOf(VintfEvidence("vendor manifest", VintfEvidenceKind.FIRMWARE_METADATA)),
                        ),
                    )
                }
            }, settings = settings)
            runCurrent()
            vm.check()
            runCurrent()
            settings.changes.emit("one")
            runCurrent()
            releaseOldRead.complete(Unit)
            runCurrent()

            assertEquals(2, checks)
            assertEquals(VintfStatus.INCOMPLETE, vm.state.value.vintf?.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun readinessChecksWaitForPendingSettingsToBeSaved() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val saved = CompletableDeferred<Unit>()
            val settings = TestSettings { 
                saved.await()
                Result.success(Unit)
             }
            var reads = 0
            val vm = PreflightViewModel("one", report = { 
                reads++
                compatible()
             }, settings = settings)
            vm.check()
            runCurrent()
            assertTrue(vm.state.value.checking)
            assertEquals(0, reads)

            saved.complete(Unit)
            runCurrent()
            assertEquals(1, reads)
            assertEquals(VintfStatus.COMPATIBLE, vm.state.value.vintf?.status)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun compatible() = PreflightReport(emptyList(), VintfAssessment(VintfStatus.COMPATIBLE))

    private class TestSettings(
        private val savePending: suspend () -> Result<Unit> = { Result.success(Unit) },
    ) : WorkspaceSettingsStore {
        override suspend fun flush(workspaceId: String): Result<Unit> = savePending()

        override val changes = MutableSharedFlow<String>(extraBufferCapacity = 1)
        override suspend fun current(workspaceId: String): ConfigurationSnapshot? = null
        override suspend fun device(workspaceId: String) = null
        override suspend fun update(
            workspaceId: String,
            change: (ConfigurationSnapshot) -> ConfigurationSnapshot,
        ): Result<ConfigurationSnapshot> = error("unused")
    }
}
