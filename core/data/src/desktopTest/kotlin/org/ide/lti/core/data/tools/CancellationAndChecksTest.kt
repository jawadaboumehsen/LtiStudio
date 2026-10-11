/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.data.tools

import io.ltirom.tooling.core.RemoteCancellationUnconfirmedException
import io.ltirom.tooling.core.ports.RemoteTransportPort
import io.ltirom.tooling.core.remote.RunCancelResponse
import io.ltirom.tooling.core.remote.RunHandle
import io.ltirom.tooling.core.remote.RunStatus
import io.ltirom.tooling.core.remote.RunStatusValue
import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StartRunRequest
import io.ltirom.tooling.core.remote.StreamEvent
import io.ltirom.tooling.core.remote.ToolExecutionRequest
import io.ltirom.tooling.core.remote.ToolExecutionResponse
import io.ltirom.tooling.core.remote.ToolListResult
import io.ltirom.tooling.core.remote.WslServerInfo
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CancellationAndChecksTest {
    /** A service whose cancel takes a while to answer, as a real one under load does. */
    private class SlowCancelTransport(private val runFinishesAfterCancel: Boolean) : RemoteTransportPort {
        val attached = CompletableDeferred<Unit>()
        var cancelRequested = false
        var cancelAnswered = false
        private var stopped = false

        override suspend fun startRun(request: StartRunRequest) = RunHandle("run-1", RunStatusValue.RUNNING, 1L)

        override fun attachRun(runId: String, fromSeq: Long): Flow<SequencedStreamEvent> = flow {
            emit(SequencedStreamEvent(1L, StreamEvent.OutputChunk("compiling")))
            attached.complete(Unit)
            awaitCancellation()
        }

        override suspend fun cancelRun(runId: String, signal: String): RunCancelResponse {
            cancelRequested = true
            delay(300) // the request must suspend; a caller that is already cancelled would be stopped right here
            cancelAnswered = true
            stopped = runFinishesAfterCancel
            return RunCancelResponse(accepted = true, status = RunStatusValue.CANCELLING)
        }

        override suspend fun getRun(runId: String): RunStatus = RunStatus(
            runId,
            if (stopped) RunStatusValue.CANCELLED else RunStatusValue.RUNNING,
            startedAtEpochMs = 1L,
        )

        override suspend fun execute(request: ToolExecutionRequest) = ToolExecutionResponse(0, "", "", 1L)

        override fun stream(request: ToolExecutionRequest): Flow<StreamEvent> = error("durable runs only")

        override suspend fun checkHealth(): WslServerInfo? = null

        override suspend fun listTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun refreshTools(): ToolListResult = ToolListResult.Tools(emptyList())

        override suspend fun uploadFile(remotePath: String, content: ByteArray): Boolean = true

        override suspend fun downloadFile(remotePath: String): ByteArray? = null

        override suspend fun shutdown(): Boolean = true
    }

    @Test
    fun `cancelling the caller still delivers the stop request to the service and waits for it`() = runTest {
        val transport = SlowCancelTransport(runFinishesAfterCancel = true)
        val collecting = launch { RemoteCommandRunner(transport).stream("cmake --build out").toList() }
        transport.attached.await()

        collecting.cancelAndJoin()

        assertTrue(transport.cancelRequested, "the cancel request never left the client")
        assertTrue(transport.cancelAnswered, "the request was abandoned half way instead of completing")
        assertTrue(collecting.isCancelled)
    }

    @Test
    fun `a stop the service never confirms is reported instead of looking cancelled`() = runTest {
        val transport = SlowCancelTransport(runFinishesAfterCancel = false)
        var failure: Throwable? = null
        val collecting =
            launch {
                failure =
                    runCatching { RemoteCommandRunner(transport).stream("cmake --build out").toList() }
                        .exceptionOrNull()
            }
        transport.attached.await()

        collecting.cancelAndJoin()

        assertTrue(transport.cancelRequested)
        assertIs<RemoteCancellationUnconfirmedException>(failure, "the run may still be building remotely")
    }

    // ------------------------------------------------------------------ package check

    private fun shAvailable() = runCatching { ProcessBuilder("sh", "-c", "true").start().waitFor() == 0 }
        .getOrDefault(false)

    private fun runCheck(pkg: String, dpkgStatus: String): Int {
        // A stand-in dpkg-query first on the PATH (a function cannot have this name in POSIX sh).
        val bin = Files.createTempDirectory("fakedpkg").toFile().apply { deleteOnExit() }
        File(bin, "dpkg-query").writeText("#!/bin/sh\nprintf '%s' '$dpkgStatus'\n")
        val drive = Regex("^([A-Za-z]):")
        val dir = bin.absolutePath.replace('\\', '/').replace(drive) { "/" + it.groupValues[1].lowercase() }
        val script =
            File.createTempFile("pkgcheck", ".sh").apply {
                deleteOnExit()
                writeText("PATH='$dir':\"\$PATH\"\n" + KotlinStoreLayout.buildPackageCheck(pkg) + "\n")
            }
        val process = ProcessBuilder("sh", script.absolutePath).redirectErrorStream(true).start()
        process.inputStream.readBytes()
        return process.waitFor()
    }

    @Test
    fun `a package that dpkg merely knows about is not installed`() {
        org.junit.Assume.assumeTrue("needs sh", shAvailable())
        val missing = "definitely-not-a-command-xyz"

        assertEquals(0, runCheck(missing, "install ok installed"))
        assertEquals(0, runCheck(missing, "hold ok installed"), "a held package is installed")
        assertEquals(0, runCheck(missing, "deinstall ok installed"), "marked for removal, but still installed")
        assertEquals(1, runCheck(missing, "deinstall ok config-files"), "removed but configured")
        assertEquals(1, runCheck(missing, "install ok half-installed"), "an unfinished install")
        assertEquals(1, runCheck(missing, "install ok unpacked"))
        assertEquals(1, runCheck(missing, ""), "dpkg has no record")
        assertEquals(0, runCheck("sh", ""), "a command on the PATH still counts")
    }
}
