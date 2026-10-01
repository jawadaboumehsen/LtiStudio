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

    @Test
    fun `decodes start run request with run purpose default and explicit`() {
        val withoutPurpose = """{"request":{"toolId":"ls","arguments":[]},"idempotencyKey":"k1"}"""
        val decodedDefault = json.decodeFromString<io.ltirom.tooling.core.remote.StartRunRequest>(withoutPurpose)
        assertEquals(io.ltirom.tooling.core.remote.RunPurpose.WORKSPACE, decodedDefault.purpose)
        assertEquals(io.ltirom.tooling.core.remote.RunPurpose.WORKSPACE, decodedDefault.request.purpose)

        val withPurpose = """{"request":{"toolId":"ls","arguments":[],"purpose":"SETUP"},"idempotencyKey":"k2","purpose":"SETUP"}"""
        val decodedSetup = json.decodeFromString<io.ltirom.tooling.core.remote.StartRunRequest>(withPurpose)
        assertEquals(io.ltirom.tooling.core.remote.RunPurpose.SETUP, decodedSetup.purpose)
        assertEquals(io.ltirom.tooling.core.remote.RunPurpose.SETUP, decodedSetup.request.purpose)
    }
}
