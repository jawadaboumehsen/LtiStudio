package io.ltirom.tooling.core.events

import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ToolEventBusTest {

    @Test
    fun `publishes and receives lifecycle events`() = runTest {
        val bus = DefaultToolEventBus()
        val context = ExecutionContext()
        val events = mutableListOf<ToolDomainEvent>()

        val job = launch {
            bus.events.take(2).toList(events)
        }

        bus.publish(
            ToolDomainEvent.ExecutionStarted(
                toolId = ToolId.ADB,
                context = context,
                commandName = "adb",
                arguments = listOf("devices")
            )
        )
        bus.publish(
            ToolDomainEvent.ExecutionCompleted(
                toolId = ToolId.ADB,
                context = context,
                durationMs = 150L,
                isSuccess = true
            )
        )

        job.join()

        assertEquals(2, events.size)
        assertTrue(events[0] is ToolDomainEvent.ExecutionStarted)
        assertEquals(ToolId.ADB, events[0].toolId)
        assertTrue(events[1] is ToolDomainEvent.ExecutionCompleted)
        assertEquals(150L, (events[1] as ToolDomainEvent.ExecutionCompleted).durationMs)
    }

    @Test
    fun `tryPublish succeeds on non-full buffer`() = runTest {
        val bus = DefaultToolEventBus(replay = 5, extraBufferCapacity = 5)
        val context = ExecutionContext()

        val published = bus.tryPublish(
            ToolDomainEvent.ExecutionStarted(
                toolId = ToolId.FASTBOOT,
                context = context,
                commandName = "fastboot",
                arguments = listOf("devices")
            )
        )
        assertTrue(published)
    }
}
