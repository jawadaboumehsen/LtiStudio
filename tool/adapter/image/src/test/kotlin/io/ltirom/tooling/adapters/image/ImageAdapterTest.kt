package io.ltirom.tooling.adapters.image

import io.ltirom.tooling.adapters.lpmake.ExecuteCommand as LpmakeExecuteCommand
import io.ltirom.tooling.core.ExecutionContext
import io.ltirom.tooling.core.ToolId
import io.ltirom.tooling.testkit.FakeToolRepository
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test

class ImageAdapterTest {

    @Test
    fun `lpmake execute command executes via fake repository`() = runTest {
        val fakeRepo = FakeToolRepository()
        val cmd = LpmakeExecuteCommand(
            output = File("out.img"),
            sparse = true
        )
        val ctx = ExecutionContext()

        fakeRepo.execute(cmd, ctx)
        fakeRepo.assertInvoked(ToolId.LPMAKE, times = 1)
        fakeRepo.assertArgumentPresent(ToolId.LPMAKE, "--sparse")
    }
}
