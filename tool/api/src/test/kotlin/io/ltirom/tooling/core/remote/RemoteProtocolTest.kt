package io.ltirom.tooling.core.remote

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.test.*

class RemoteProtocolTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    @Test
    fun testSequencedStreamEventWireFormat() {
        // Daemon format: {"seq": <long>, "event": {...}}
        val payload = """
            {
                "seq": 42,
                "event": {
                    "type": "io.ltirom.server.model.StreamEvent.OutputChunk",
                    "text": "build succeeded\n",
                    "isError": false
                }
            }
        """.trimIndent()

        val event = json.decodeFromString<SequencedStreamEvent>(payload)
        assertEquals(42L, event.seq)
        val streamEvent = event.event
        assertIs<StreamEvent.OutputChunk>(streamEvent)
        assertEquals("build succeeded\n", streamEvent.text)
        assertFalse(streamEvent.isError)

        // Roundtrip serialization check
        val serialized = json.encodeToString(event)
        assertTrue(serialized.contains("\"seq\":42"))
        val roundtrip = json.decodeFromString<SequencedStreamEvent>(serialized)
        assertEquals(42L, roundtrip.seq)
    }

    @Test
    fun testSessionSummaryWireFormat() {
        // Daemon format: sessionId, toolId, arguments, startTimeEpochMs, status, exitCode (nullable), durationMs (nullable)
        val payload = """
            {
                "sessionId": "session-123",
                "toolId": "fastboot",
                "arguments": ["getvar", "all"],
                "startTimeEpochMs": 1700000000000,
                "status": "COMPLETED",
                "exitCode": 0,
                "durationMs": 520
            }
        """.trimIndent()

        val summary = json.decodeFromString<SessionSummary>(payload)
        assertEquals("session-123", summary.sessionId)
        assertEquals("fastboot", summary.toolId)
        assertEquals(listOf("getvar", "all"), summary.arguments)
        assertEquals(1700000000000L, summary.startTimeEpochMs)
        assertEquals(SessionStatus.COMPLETED, summary.status)
        assertEquals(0, summary.exitCode)
        assertEquals(520L, summary.durationMs)

        // Nullable fields default to null when absent
        val minimalPayload = """
            {
                "sessionId": "session-456",
                "toolId": "adb",
                "arguments": ["devices"],
                "startTimeEpochMs": 1700000005000,
                "status": "RUNNING"
            }
        """.trimIndent()

        val runningSummary = json.decodeFromString<SessionSummary>(minimalPayload)
        assertEquals("session-456", runningSummary.sessionId)
        assertEquals("adb", runningSummary.toolId)
        assertEquals(listOf("devices"), runningSummary.arguments)
        assertEquals(1700000005000L, runningSummary.startTimeEpochMs)
        assertEquals(SessionStatus.RUNNING, runningSummary.status)
        assertNull(runningSummary.exitCode)
        assertNull(runningSummary.durationMs)

        // Roundtrip serialization
        val serialized = json.encodeToString(summary)
        val roundtrip = json.decodeFromString<SessionSummary>(serialized)
        assertEquals(summary, roundtrip)
    }
}
