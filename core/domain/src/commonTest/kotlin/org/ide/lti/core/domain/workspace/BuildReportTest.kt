/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.workspace

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.PipelineFixture
import org.ide.lti.core.domain.pipeline.toolkit.AckStore
import org.ide.lti.core.domain.pipeline.toolkit.JobLock
import org.ide.lti.core.domain.pipeline.toolkit.RestorePointStore
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.repository.snapshot.InMemorySnapshotRepository
import org.ide.lti.core.domain.repository.workspace.WorkspaceRepository
import org.ide.lti.core.domain.workbench.WorkbenchRunner
import org.ide.lti.core.model.run.BuildRun
import org.ide.lti.core.model.run.StepCommand
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildReportTest {
    @Test
    fun theReportUsesTheRunsSnapshotAndConfiguredKeysWithoutReadingCurrentReceipts() = runTest {
        val fixture = PipelineFixture()
        val saved = fixture.snapshotGlobalOta.let {
            it.copy(
                build = it.build.copy(
                    signing = it.build.signing.copy(
                        platformKeyRef = "keys/custom platform",
                        avbKeyRef = "keys/custom avb.pem",
                    ),
                ),
            )
        }
        val snapshots = InMemorySnapshotRepository().also { it.saveSnapshot(saved) }
        val commands = mutableListOf<StepCommand>()
        val port = fake<PipelineExecutionPort> { name, args ->
            when (name) {
                "startStep" -> {
                    commands += args[1] as StepCommand
                    RunHandle("report", "RUNNING", 0)
                }

                "observe" -> flowOf(StepEvent.Output(1, "cert\tcustom-cert\navb\tcustom-avb"), StepEvent.Finished(0))

                else -> error("Unexpected port method $name: current workspace receipts must not be read")
            }
        }
        val workspaces = fake<WorkspaceRepository> { name, _ ->
            check(name == "getWorkspace")
            fixture.workspace
        }
        val settings = fake<WorkspaceSettingsStore> { name, _ ->
            check(name == "device") { "Historical report must not use settings.$name" }
            fixture.targetGlobal
        }
        val runner = WorkbenchRunner(
            workspaces,
            port,
            unused<JobLock>(),
            unused<RestorePointStore>(),
            unused<AckStore>(),
        )
        val reporter = BuildReport(runner, settings, snapshots)
        val run = BuildRun("run", saved.workspaceId, saved.id, startedAt = saved.createdAt)
        val report = reporter.of(run)
        assertTrue(checkNotNull(saved.acquisition.firmware).version in report.markdown)
        assertTrue("custom-cert" in report.markdown && "custom-avb" in report.markdown)
        assertTrue("results are unavailable" in report.markdown)
        val args = commands.flatMap { it.args }
        assertTrue(args.any { it.endsWith("/keys/custom platform.x509.pem") })
        assertTrue(args.any { it.endsWith("/keys/custom avb.pem") })
        assertFalse(args.any { it.endsWith("/keys/platform.x509.pem") })

        val missing = reporter.of(run.copy(snapshotId = "missing"))
        assertTrue(missing.problems.any { "snapshot is unavailable" in it })
        assertFalse("- Firmware:" in missing.markdown)
    }

    private inline fun <reified T> unused(): T = fake { name, _ -> error("Unexpected $name") }

    private inline fun <reified T> fake(crossinline call: (String, Array<out Any?>) -> Any?): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, args ->
            call(method.name, args.orEmpty())
        } as T
}
