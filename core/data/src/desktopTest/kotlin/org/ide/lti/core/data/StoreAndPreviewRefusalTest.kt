/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.data.patch.FileMutationAdapter
import org.ide.lti.core.data.toolkit.WorkspaceToolkitStores
import org.ide.lti.core.domain.pipeline.ToolResult
import org.ide.lti.core.domain.pipeline.toolkit.LockResult
import org.ide.lti.core.domain.plugin.ConcreteMutationPlan
import org.ide.lti.core.domain.plugin.ConcreteOperation
import org.ide.lti.core.domain.plugin.MutationContext
import org.ide.lti.core.domain.plugin.MutationOutcome
import org.ide.lti.core.domain.ports.FileStat
import org.ide.lti.core.domain.ports.PipelineExecutionPort
import org.ide.lti.core.domain.ports.RunHandle
import org.ide.lti.core.domain.ports.StepEvent
import org.ide.lti.core.domain.ports.StepStatus
import org.ide.lti.core.model.plugin.PartitionPath
import org.ide.lti.core.model.run.StepCommand
import org.ide.lti.core.model.workspace.Workspace
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertNull

/** A property the edit rules refuse is refused in the preview; a lock whose delete failed can be released again. */
class StoreAndPreviewRefusalTest {
    @Test fun previewRefusesInvalidPropertyValue() = runTest {
        val root = Files.createTempDirectory("preview-prop-")
        try {
            val part = Files.createDirectories(root.resolve("system"))
            Files.writeString(part.resolve("build.prop"), "x=1\n")
            val plan = ConcreteMutationPlan(
                "p",
                "id",
                "1",
                listOf(ConcreteOperation.PropertyPatch("op", PartitionPath("system", "build.prop"), "x", "2\nextra=3")),
                emptyList(),
                "digest",
            )
            assertIs<MutationOutcome.Refused>(
                FileMutationAdapter().preview(
                    plan,
                    MutationContext(root.toString(), mapOf("system" to "system")),
                    root.toString(),
                ),
            )
        } finally {
            Files.deleteIfExists(root.resolve("system/build.prop"))
            Files.deleteIfExists(root.resolve("system"))
            Files.deleteIfExists(root)
        }
    }

    @Test fun failedJobUnlockCanBeRetried() = runTest {
        var lockText: String? = null
        var deletes = 0
        val port = object : PipelineExecutionPort {
            override suspend fun execute(ws: Workspace, command: StepCommand): ToolResult {
                deletes++
                if (deletes == 1) return ToolResult(1, "", "read only")
                lockText = null
                return ToolResult(0, "", "")
            }
            override suspend fun readFile(ws: Workspace, relPath: String) = lockText
            override suspend fun uploadFile(ws: Workspace, relPath: String, bytes: ByteArray): Boolean {
                lockText = bytes.decodeToString()
                return true
            }
            override suspend fun startStep(ws: Workspace, step: StepCommand, idempotencyKey: String): RunHandle = error(
                "unused",
            )
            override fun observe(serverRunId: String, fromSeq: Long): Flow<StepEvent> = error("unused")
            override suspend fun status(serverRunId: String): StepStatus? = null
            override suspend fun cancel(serverRunId: String) = false
            override suspend fun stat(ws: Workspace, relPath: String) = FileStat(relPath, false)
            override suspend fun checkAvailableDiskSpace(ws: Workspace): Long? = null
        }
        val ws = Workspace("ws", "ws", "/tmp/ws", linuxPath = "/tmp/ws")
        val store = WorkspaceToolkitStores(port)
        val lease = assertIs<LockResult.Acquired>(store.acquire(ws, "editing")).handle
        runCatching { lease.release() }
        lease.release()
        assertNull(store.currentHolder(ws), "Retry must clear a lock whose first deletion failed")
    }
}
