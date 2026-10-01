/*
 * Copyright 2026 Mifos Initiative
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 *
 * See https://github.com/openMF/kmp-project-template/blob/main/LICENSE
 */
package org.ide.lti.core.domain.ports

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.ide.lti.core.domain.pipeline.SimulatedExecutionPort
import org.ide.lti.core.model.workspace.Workspace
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BoundedTransferPortTest {

    @Test
    fun testMaxTransferChunkBytesIsEightMiB() {
        assertEquals(8L * 1024 * 1024, MAX_TRANSFER_CHUNK_BYTES)
    }

    @Test
    fun testSimulatedExecutionPortThrowsUnsupportedTransportCapabilityExceptionByDefault() = runTest {
        val port = SimulatedExecutionPort("/tmp/ws")
        val ws = Workspace(
            id = "ws-1",
            name = "Test Workspace",
            path = "/tmp/ws",
        )

        val exception = assertFailsWith<UnsupportedTransportCapabilityException> {
            port.uploadBounded(
                ws = ws,
                relPath = "test/destination.bin",
                totalBytes = 1024L,
                chunks = emptyFlow(),
            )
        }

        assertEquals("bounded streaming upload is not supported by this transport", exception.message)
    }
}
