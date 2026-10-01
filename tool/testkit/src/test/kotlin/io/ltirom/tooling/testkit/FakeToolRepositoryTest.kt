package io.ltirom.tooling.testkit

import io.ltirom.tooling.core.CommandExecutionResult
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.RawToolCommand
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.core.remote.StreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeToolRepositoryTest {

    @Test
    fun `records invocations and executes default handler`() = runTest {
        val fake = FakeToolRepository()
        val cmd = RawToolCommand(ToolId.ADB, listOf("devices", "-l"))
        val ctx = ExecutionContext()

        val result = fake.execute(cmd, ctx)
        assertEquals(0, result.exitCode)
        assertEquals(1, fake.invocations.size)
        assertEquals(ToolId.ADB, fake.invocations[0].command.toolId)
        fake.assertInvoked(ToolId.ADB, times = 1)
        fake.assertArgumentPresent(ToolId.ADB, "devices")
    }

    @Test
    fun `supports custom handler per tool`() = runTest {
        val fake = FakeToolRepository()
        fake.onTool(ToolId.FASTBOOT) { _, _ ->
            CommandExecutionResult(0, "fastboot-custom", "", 5L)
        }

        val cmd = RawToolCommand(ToolId.FASTBOOT, listOf("devices"))
        val result = fake.execute(cmd, ExecutionContext())
        assertEquals("fastboot-custom", result.stdout)
    }

    @Test
    fun `supports streaming`() = runTest {
        val fake = FakeToolRepository()
        val cmd = RawToolCommand(ToolId.PAYLOAD_DUMPER_GO, listOf("payload.bin"))
        val events = fake.stream(cmd, ExecutionContext()).toList()

        assertTrue(events.isNotEmpty())
        assertTrue(events.last() is StreamEvent.ExecutionFinished)
    }
}
