package io.ltirom.tooling.client.transport

import io.ltirom.tooling.core.remote.SequencedStreamEvent
import io.ltirom.tooling.core.remote.StreamEvent
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/** The client reads exactly what the build service writes (lines copied from a real run journal). */
class StreamWireContractTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `decodes the service's stream events`() {
        val stderr = """{"seq":64,"event":{"type":"io.ltirom.server.model.StreamEvent.OutputChunk",""" +
            """"text":"tls_connection.cpp:334:46: error: use of undeclared identifier 'strerror'","isError":true}}"""
        val finished = """{"seq":98,"event":{"type":"io.ltirom.server.model.StreamEvent.ExecutionFinished",""" +
            """"exitCode":2,"durationMs":4832,"summary":""}}"""
        val heartbeat = """{"seq":0,"event":{"type":"io.ltirom.server.model.StreamEvent.Heartbeat",""" +
            """"timestampEpochMs":1}}"""

        assertEquals(
            StreamEvent.OutputChunk("tls_connection.cpp:334:46: error: use of undeclared identifier 'strerror'", true),
            json.decodeFromString<SequencedStreamEvent>(stderr).event,
        )
        assertEquals(StreamEvent.ExecutionFinished(2, 4832, ""), json.decodeFromString<SequencedStreamEvent>(finished).event)
        assertEquals(StreamEvent.Heartbeat(1), json.decodeFromString<SequencedStreamEvent>(heartbeat).event)
    }
}
